package com.nexus.parent.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
        askBeforeDownload: Boolean
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

        val saved = writeToMediaStoreDownloads(appCtx, stagedFile, fileName, mimeType)
        stagedFile.delete()
        if (saved) {
            ClipExportOutcome.SavedToDownloads(
                fileName = fileName,
                relativePath = "${Environment.DIRECTORY_DOWNLOADS}/NexusParent/$fileName"
            )
        } else {
            ClipExportOutcome.Failed("Failed writing clip to Downloads/NexusParent.")
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

    private fun writeToMediaStoreDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): Boolean {
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/NexusParent"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
