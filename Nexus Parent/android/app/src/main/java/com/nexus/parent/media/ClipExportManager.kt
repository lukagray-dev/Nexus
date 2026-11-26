package com.nexus.parent.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.webrtc.VideoFeedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

sealed class ClipExportOutcome {
    data class SavedToDownloads(val fileName: String, val relativePath: String) : ClipExportOutcome()
    data class NeedsUserDestination(val fileName: String, val mimeType: String, val stagedFile: File) : ClipExportOutcome()
    data class Failed(val reason: String) : ClipExportOutcome()
}

/**
 * ClipExportManager.kt - Losslessly remuxes retained rolling DVR segments into a single
 * playable `.mp4` or `.webm` clip respecting Desktop `downloadsDirectory` & `askBeforeDownload`.
 */
object ClipExportManager {

    suspend fun exportClip(
        context: Context,
        feedType: VideoFeedType,
        segmentFiles: List<File>,
        container: ClipFormat,
        askBeforeDownload: Boolean,
        customTreeUri: String? = null,
        directoryLabel: String = "Downloads/NexusParent"
    ): ClipExportOutcome = withContext(Dispatchers.IO) {
        if (segmentFiles.isEmpty()) {
            return@withContext ClipExportOutcome.Failed("No recorded DVR segments ready yet. Wait a few seconds and retry.")
        }

        val appCtx = context.applicationContext
        val exportDir = File(appCtx.cacheDir, "export").apply { mkdirs() }
        val fileName = DvrBufferPolicy.buildClipFileName(feedType, container)
        val mimeType = if (container == ClipFormat.WEBM) "video/webm" else "video/mp4"
        val stagedFile = File(exportDir, fileName)

        val mergedOk = mergeSegmentsLossless(segmentFiles, stagedFile, container)
        if (!mergedOk || !stagedFile.exists() || stagedFile.length() == 0L) {
            stagedFile.delete()
            return@withContext ClipExportOutcome.Failed("Unable to assemble DVR clip file.")
        }

        if (askBeforeDownload) {
            return@withContext ClipExportOutcome.NeedsUserDestination(
                fileName = fileName,
                mimeType = mimeType,
                stagedFile = stagedFile
            )
        }

        if (!customTreeUri.isNullOrBlank()) {
            val savedTreeFile = writeToCustomTreeUri(appCtx, stagedFile, customTreeUri, fileName, mimeType)
            if (savedTreeFile != null) {
                stagedFile.delete()
                return@withContext ClipExportOutcome.SavedToDownloads(
                    fileName = savedTreeFile,
                    relativePath = "$directoryLabel/$savedTreeFile"
                )
            }
        }

        val savedMediaName = writeToMediaStoreDownloads(appCtx, stagedFile, fileName, mimeType)
        stagedFile.delete()
        if (savedMediaName != null) {
            ClipExportOutcome.SavedToDownloads(
                fileName = savedMediaName,
                relativePath = "${Environment.DIRECTORY_DOWNLOADS}/NexusParent/$savedMediaName"
            )
        } else {
            ClipExportOutcome.Failed("Failed writing clip to $directoryLabel.")
        }
    }

    suspend fun copyStagedFileToUri(context: Context, stagedFile: File, destinationUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    FileInputStream(stagedFile).use { inStream ->
                        inStream.copyTo(outStream) > 0L
                    }
                } ?: false
            }.getOrDefault(false).also {
                stagedFile.delete()
            }
        }

    private fun mergeSegmentsLossless(
        segments: List<File>,
        outputFile: File,
        container: ClipFormat
    ): Boolean {
        val outputFormat = if (container == ClipFormat.WEBM) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        } else {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }

        var muxer: MediaMuxer? = null
        return runCatching {
            muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
            var outTrackIndex = -1
            var muxerStarted = false
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var globalPtsOffsetUs = 0L

            for (segment in segments) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.absolutePath)
                    val trackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
                        extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                    } ?: continue

                    extractor.selectTrack(trackIdx)
                    if (!muxerStarted) {
                        outTrackIndex = muxer!!.addTrack(extractor.getTrackFormat(trackIdx))
                        muxer!!.start()
                        muxerStarted = true
                    }

                    var lastSegmentPtsUs = 0L
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val sampleTime = extractor.sampleTime.coerceAtLeast(0L)
                        lastSegmentPtsUs = maxOf(lastSegmentPtsUs, sampleTime)
                        val sampleFlags = extractor.sampleFlags
                        var codecFlags = 0
                        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        }
                        bufferInfo.set(
                            0,
                            sampleSize,
                            globalPtsOffsetUs + sampleTime,
                            codecFlags
                        )
                        buffer.position(0)
                        buffer.limit(sampleSize)
                        muxer!!.writeSampleData(outTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    globalPtsOffsetUs += lastSegmentPtsUs + 33_333L
                } finally {
                    extractor.release()
                }
            }

            muxerStarted
        }.getOrDefault(false).also {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun writeToCustomTreeUri(
        context: Context,
        sourceFile: File,
        treeUriString: String,
        desiredName: String,
        mimeType: String
    ): String? {
        return runCatching {
            val resolver = context.contentResolver
            val treeUri = treeUriString.toUri()
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val docUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, desiredName)
                ?: return null
            val ok = resolver.openOutputStream(docUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } != null
            if (!ok) {
                runCatching { DocumentsContract.deleteDocument(resolver, docUri) }
                return null
            }
            desiredName
        }.getOrNull()
    }

    private fun writeToMediaStoreDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): String? {
        val resolver = context.contentResolver
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/NexusParent"
        val uniqueName = resolveUniqueDownloadName(context, fileName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uniqueName
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun resolveUniqueDownloadName(context: Context, desiredName: String): String {
        val existingNames = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("${Environment.DIRECTORY_DOWNLOADS}/NexusParent%"),
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                if (idx >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(idx)?.let { existingNames.add(it) }
                    }
                }
            }
        }
        if (!existingNames.contains(desiredName)) return desiredName
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
        var counter = 1
        while (counter < 1000) {
            val candidate = "$base ($counter)$ext"
            if (!existingNames.contains(candidate)) return candidate
            counter++
        }
        return "${base}_${System.currentTimeMillis()}$ext"
    }
}
