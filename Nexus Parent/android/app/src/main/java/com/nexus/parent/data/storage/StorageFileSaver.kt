package com.nexus.parent.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * StorageFileSaver.kt - Constant-memory disk chunk assembler, preview cache, and Scoped Storage saver.
 *
 * Hey friend! Child streams files as 64KB Base64 chunks (`STORAGE_FILE_CHUNK`). Instead of stacking
 * hundreds of chunks in heap memory (which could OOM on large videos), we write each sequential
 * chunk directly to a `.nexuspart` staging file on `Dispatchers.IO`. Completed downloads are then
 * committed atomically to `Downloads/NexusParent/` via `MediaStore.Downloads` or copied to the
 * parent's chosen SAF `ACTION_CREATE_DOCUMENT` Uri.
 */
class StorageFileSaver(
    private val context: Context?,
    private val cacheRootDir: File = context?.cacheDir
        ?: File(System.getProperty("java.io.tmpdir"), "nexus_parent_storage_cache"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val base64Decoder: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) }
) {

    inner class StagedTransferWriter(
        val transferId: String,
        val path: String,
        val fileName: String,
        val expectedSize: Long,
        val totalChunks: Int,
        val mimeType: String,
        val purpose: String,
        val destinationUri: Uri? = null
    ) {
        private val stagingFile = File(
            transfersDir(),
            "${sanitizeFileName(transferId)}.nexuspart"
        )
        private val stream = BufferedOutputStream(FileOutputStream(stagingFile, false))
        private val pendingChunks = HashMap<Int, ByteArray>()
        private var nextChunkToWrite = 0

        var receivedChunkCount: Int = 0
            private set
        var receivedBytes: Long = 0L
            private set

        @Synchronized
        fun appendChunk(chunkIndex: Int, dataBase64: String) {
            if (chunkIndex < nextChunkToWrite || pendingChunks.containsKey(chunkIndex)) {
                return
            }
            val bytes = if (dataBase64.isEmpty()) ByteArray(0) else base64Decoder(dataBase64)
            receivedChunkCount += 1
            receivedBytes += bytes.size.toLong()
            pendingChunks[chunkIndex] = bytes

            while (pendingChunks.containsKey(nextChunkToWrite)) {
                val slice = pendingChunks.remove(nextChunkToWrite) ?: break
                if (slice.isNotEmpty()) {
                    stream.write(slice)
                }
                nextChunkToWrite++
            }
            stream.flush()
        }

        @Synchronized
        fun finish(): File {
            // Drain any remaining buffered indices cleanly if totalChunks matched with non-zero gap
            val remainingKeys = pendingChunks.keys.sorted()
            for (idx in remainingKeys) {
                val slice = pendingChunks.remove(idx) ?: continue
                if (slice.isNotEmpty()) {
                    stream.write(slice)
                }
            }
            stream.flush()
            stream.close()
            return stagingFile
        }

        @Synchronized
        fun abort() {
            pendingChunks.clear()
            runCatching { stream.close() }
            stagingFile.delete()
        }
    }

    private fun transfersDir(): File = File(cacheRootDir, "storage_transfers").apply { mkdirs() }
    private fun previewsDir(): File = File(cacheRootDir, "storage_previews").apply { mkdirs() }

    fun openWriter(
        transferId: String,
        path: String,
        fileName: String,
        expectedSize: Long,
        totalChunks: Int,
        mimeType: String,
        purpose: String,
        destinationUri: Uri?
    ): StagedTransferWriter {
        return StagedTransferWriter(
            transferId = transferId,
            path = path,
            fileName = fileName,
            expectedSize = expectedSize,
            totalChunks = totalChunks,
            mimeType = mimeType,
            purpose = purpose,
            destinationUri = destinationUri
        )
    }

    suspend fun cachePreviewFile(stagedFile: File, remotePath: String, fileName: String): File =
        withContext(ioDispatcher) {
            val hash = sha256Prefix(remotePath)
            val destFile = File(previewsDir(), "${hash}_${sanitizeFileName(fileName)}")
            if (destFile.exists()) destFile.delete()
            if (!stagedFile.renameTo(destFile)) {
                FileInputStream(stagedFile).use { input ->
                    FileOutputStream(destFile, false).use { output ->
                        input.copyTo(output)
                    }
                }
                stagedFile.delete()
            }
            destFile
        }

    suspend fun saveStagedFileToDownloads(
        stagedFile: File,
        fileName: String,
        mimeType: String,
        deleteSource: Boolean
    ): String? = withContext(ioDispatcher) {
        val appCtx = context?.applicationContext ?: return@withContext null
        val uniqueName = resolveUniqueDownloadName(appCtx, sanitizeFileName(fileName))
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/NexusParent"
        val resolver = appCtx.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null

        val copied = runCatching {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(stagedFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } != null
        }.getOrDefault(false)

        if (!copied) {
            resolver.delete(itemUri, null, null)
            return@withContext null
        }

        val publishValues = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(itemUri, publishValues, null, null)

        if (deleteSource) {
            stagedFile.delete()
        }
        "$relativeDir/$uniqueName"
    }

    suspend fun copyFileToUri(
        sourceFile: File,
        destinationUri: Uri,
        deleteSource: Boolean
    ): Boolean = withContext(ioDispatcher) {
        val appCtx = context?.applicationContext ?: return@withContext false
        val ok = runCatching {
            appCtx.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } != null
        }.getOrDefault(false)

        if (deleteSource) {
            sourceFile.delete()
        }
        ok
    }

    fun clearAllCachedFiles() {
        runCatching { transfersDir().deleteRecursively() }
        runCatching { previewsDir().deleteRecursively() }
    }

    private fun resolveUniqueDownloadName(context: Context, desiredName: String): String {
        val resolver = context.contentResolver
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""

        val existingNames = HashSet<String>()
        runCatching {
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/NexusParent%")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val colIdx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                if (colIdx >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(colIdx)?.let { existingNames.add(it) }
                    }
                }
            }
        }

        if (!existingNames.contains(desiredName)) return desiredName
        var counter = 1
        while (counter < 1000) {
            val candidate = "$base ($counter)$ext"
            if (!existingNames.contains(candidate)) return candidate
            counter++
        }
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "nexus_file" }
    }

    private fun sha256Prefix(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
