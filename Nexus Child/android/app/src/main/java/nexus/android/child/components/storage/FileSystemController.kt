package nexus.android.child.components.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import nexus.android.child.commands.FileListRequest
import nexus.android.child.commands.FileStreamRequest
import nexus.android.child.commands.StorageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.ClosedByInterruptException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import androidx.core.graphics.scale
import kotlinx.coroutines.delay

/**
 * Coordinates file metadata enumeration and chunked file streaming over the
 * existing WebRTC DataChannel. Heavy work is dispatched off the main thread.
 */
@Suppress("DEPRECATION")
class FileSystemController(
    private val context: Context,
    private val dataChannel: DataChannel,
    private val scope: CoroutineScope,
) : StorageHandler {

    companion object {
        private const val TAG = "FileSystemController"
        private const val DEFAULT_BATCH_SIZE = FileListRequest.DEFAULT_BATCH_SIZE
        private const val MAX_BATCH_SIZE = 250
        private const val MIN_BATCH_SIZE = 20
        private const val DEFAULT_CHUNK_BYTES = FileStreamRequest.DEFAULT_CHUNK_BYTES
        private const val MAX_CHUNK_BYTES = 512 * 1024
        private const val MIN_CHUNK_BYTES = 16 * 1024
        private const val CHUNK_MAGIC = 0x4643484B // "FCHK"
        private const val CHUNK_HEADER_VERSION = 1
    }

    private data class StorageDescriptor(
        val id: String,
        val label: String,
        val root: File,
    )

    private data class FileMetadataPayload(
        val filename: String,
        val absolutePath: String,
        val fileSize: Long,
        val createdTimestamp: Long,
        val modifiedTimestamp: Long,
        val mimeType: String,
        val thumbnailBase64: String?,
        val fileTypeIcon: String,
        val isDirectory: Boolean,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("filename", filename)
            put("absolute_path", absolutePath)
            put("file_size", fileSize)
            put("created_timestamp", createdTimestamp)
            put("modified_timestamp", modifiedTimestamp)
            put("mime_type", mimeType)
            put("thumbnail_base64", thumbnailBase64)
            put("file_type_icon", fileTypeIcon)
            put("is_directory", isDirectory)
        }
    }

    private data class FileTransferSession(
        val transferId: String,
        val file: File,
        val chunkSize: Int,
        var offset: Long,
        val requestId: String?,
    ) {
        var job: Job? = null
    }

    private var listingJob: Job? = null
    private val activeTransfers = ConcurrentHashMap<String, FileTransferSession>()

    override fun listFiles(request: FileListRequest) {
        listingJob?.cancel()
        listingJob = scope.launch(Dispatchers.IO) {
            try {
                if (!hasReadAccess()) {
                    sendListError(
                        reason = "PERMISSION_DENIED",
                        requestId = request.requestId,
                        path = request.directoryPath
                    )
                    return@launch
                }

                val targetBatchSize = request.batchSize
                    .coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)

                val tokens = request.pageToken?.takeIf { it.isNotBlank() }

                val targets = resolveTargets(request.directoryPath)
                if (targets.isEmpty()) {
                    sendListError(
                        reason = "INVALID_PATH",
                        requestId = request.requestId,
                        path = request.directoryPath
                    )
                    return@launch
                }

                for (descriptor in targets) {
                    if (!isActive) break
                    streamDescriptor(
                        descriptor = descriptor,
                        includeHidden = request.includeHidden,
                        batchSize = targetBatchSize,
                        pageToken = tokens,
                        requestId = request.requestId
                    )
                }

                sendListComplete(request.requestId, request.directoryPath)
            } catch (ex: Exception) {
                Log.e(TAG, "LIST_FILES failed", ex)
                sendListError(
                    reason = ex.message ?: "UNKNOWN",
                    requestId = request.requestId,
                    path = request.directoryPath
                )
            }
        }
    }

    override fun streamFile(request: FileStreamRequest) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasReadAccess()) {
                    sendFileError(
                        absolutePath = request.absolutePath,
                        requestId = request.requestId,
                        transferId = request.transferId,
                        reason = "PERMISSION_DENIED"
                    )
                    return@launch
                }

                val file = File(request.absolutePath)
                if (!file.exists() || !file.canRead()) {
                    sendFileError(
                        absolutePath = request.absolutePath,
                        requestId = request.requestId,
                        transferId = request.transferId,
                        reason = "FILE_NOT_FOUND"
                    )
                    return@launch
                }

                val chunkSize = request.chunkSizeBytes
                    .coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES)
                val sanitizedOffset = request.resumeOffset.coerceIn(0, file.length())
                val transferId = request.transferId ?: UUID.randomUUID().toString()

                // Cancel duplicate transfers for the same transferId
                activeTransfers.remove(transferId)?.let { existing ->
                    existing.job?.cancel()
                }

                val session = FileTransferSession(
                    transferId = transferId,
                    file = file,
                    chunkSize = chunkSize,
                    offset = sanitizedOffset,
                    requestId = request.requestId
                )
                activeTransfers[transferId] = session
                session.job = launchTransfer(session)
            } catch (ex: Exception) {
                Log.e(TAG, "GET_FILE failed", ex)
                sendFileError(
                    absolutePath = request.absolutePath,
                    requestId = request.requestId,
                    transferId = request.transferId,
                    reason = ex.message ?: "UNKNOWN"
                )
            }
        }
    }

    /**
     * Handle progressive file streaming request
     * This method starts streaming immediately without waiting for the entire file
     */
    fun streamFileProgressive(request: FileStreamRequest) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasReadAccess()) {
                    sendFileError(
                        absolutePath = request.absolutePath,
                        requestId = request.requestId,
                        transferId = request.transferId,
                        reason = "PERMISSION_DENIED"
                    )
                    return@launch
                }

                val file = File(request.absolutePath)
                if (!file.exists() || !file.canRead()) {
                    sendFileError(
                        absolutePath = request.absolutePath,
                        requestId = request.requestId,
                        transferId = request.transferId,
                        reason = "FILE_NOT_FOUND"
                    )
                    return@launch
                }

                // Use smaller chunks for progressive streaming to enable faster start
                val progressiveChunkSize = (request.chunkSizeBytes / 2)
                    .coerceIn(MIN_CHUNK_BYTES, 64 * 1024) // Max 64KB for progressive
                val sanitizedOffset = request.resumeOffset.coerceIn(0, file.length())
                val transferId = request.transferId ?: UUID.randomUUID().toString()

                Log.d(TAG, "Starting progressive stream: ${file.name}, chunkSize: $progressiveChunkSize")

                // Cancel duplicate transfers for the same transferId
                activeTransfers.remove(transferId)?.let { existing ->
                    existing.job?.cancel()
                }

                val session = FileTransferSession(
                    transferId = transferId,
                    file = file,
                    chunkSize = progressiveChunkSize,
                    offset = sanitizedOffset,
                    requestId = request.requestId
                )
                activeTransfers[transferId] = session
                session.job = launchProgressiveTransfer(session)
            } catch (ex: Exception) {
                Log.e(TAG, "GET_FILE_PROGRESSIVE failed", ex)
                sendFileError(
                    absolutePath = request.absolutePath,
                    requestId = request.requestId,
                    transferId = request.transferId,
                    reason = ex.message ?: "UNKNOWN"
                )
            }
        }
    }

    fun shutdown() {
        listingJob?.cancel()
        listingJob = null
        activeTransfers.forEach { (_, session) ->
            session.job?.cancel()
        }
        activeTransfers.clear()
    }

    private fun launchTransfer(session: FileTransferSession): Job {
        sendFileInit(session)
        return scope.launch(Dispatchers.IO) {
            val file = session.file
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(file, "r").apply {
                    seek(session.offset)
                }
                val buffer = ByteArray(session.chunkSize)
                while (isActive && session.offset < file.length()) {
                    val remaining = (file.length() - session.offset).coerceAtLeast(0L)
                    val toRead = min(remaining, session.chunkSize.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    val chunkData = buffer.copyOf(read)
                    val isLast = (session.offset + read) >= file.length()
                    val sent = sendChunk(session.transferId, session.offset, chunkData, isLast)
                    if (!sent) {
                        sendFilePaused(session, "CHANNEL_NOT_OPEN")
                        break
                    }
                    session.offset += read
                    sendFileProgress(session)
                }

                if (session.offset >= file.length()) {
                    sendFileComplete(session)
                    activeTransfers.remove(session.transferId)
                }
            } catch (ex: Exception) {
                if (ex is ClosedByInterruptException) return@launch
                Log.e(TAG, "Streaming failed", ex)
                sendFileError(
                    absolutePath = file.absolutePath,
                    requestId = session.requestId,
                    transferId = session.transferId,
                    reason = ex.message ?: "STREAM_FAILED"
                )
                activeTransfers.remove(session.transferId)
            } finally {
                raf?.close()
            }
        }
    }

    private fun launchProgressiveTransfer(session: FileTransferSession): Job {
        sendFileInit(session)
        return scope.launch(Dispatchers.IO) {
            val file = session.file
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(file, "r").apply {
                    seek(session.offset)
                }
                val buffer = ByteArray(session.chunkSize)
                var chunkCount = 0
                
                Log.d(TAG, "Starting progressive transfer: ${file.name}, size: ${file.length()}")
                
                while (isActive && session.offset < file.length()) {
                    val remaining = (file.length() - session.offset).coerceAtLeast(0L)
                    val toRead = min(remaining, session.chunkSize.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    
                    val chunkData = buffer.copyOf(read)
                    val isLast = (session.offset + read) >= file.length()
                    
                    val sent = sendChunk(session.transferId, session.offset, chunkData, isLast)
                    if (!sent) {
                        Log.w(TAG, "Progressive transfer paused: channel not open")
                        sendFilePaused(session, "CHANNEL_NOT_OPEN")
                        break
                    }
                    
                    session.offset += read
                    chunkCount++
                    
                    // Send progress updates more frequently for progressive streaming
                    if (chunkCount % 5 == 0 || isLast) {
                        sendFileProgress(session)
                    }
                    
                    // Add small delay for progressive streaming to prevent overwhelming the channel
                    // but only after the first few chunks to allow quick startup
                    if (chunkCount > 10) {
                        delay(10) // 10ms delay between chunks after initial burst
                    }
                    
                    Log.v(TAG, "Progressive chunk sent: $chunkCount, offset: ${session.offset}, isLast: $isLast")
                }

                if (session.offset >= file.length()) {
                    Log.d(TAG, "Progressive transfer complete: ${file.name}, chunks: $chunkCount")
                    sendFileComplete(session)
                    activeTransfers.remove(session.transferId)
                }
            } catch (ex: Exception) {
                if (ex is ClosedByInterruptException) return@launch
                Log.e(TAG, "Progressive streaming failed", ex)
                sendFileError(
                    absolutePath = file.absolutePath,
                    requestId = session.requestId,
                    transferId = session.transferId,
                    reason = ex.message ?: "PROGRESSIVE_STREAM_FAILED"
                )
                activeTransfers.remove(session.transferId)
            } finally {
                raf?.close()
            }
        }
    }

    private fun streamDescriptor(
        descriptor: StorageDescriptor,
        includeHidden: Boolean,
        batchSize: Int,
        pageToken: String?,
        requestId: String?,
    ) {
        val root = descriptor.root
        if (root.isFile) {
            buildMetadata(root, includeHidden)?.let { payload ->
                sendBatch(
                    descriptor = descriptor,
                    items = listOf(payload),
                    requestId = requestId,
                    parentPath = root.parent,
                    nextToken = null
                )
            }
            return
        }

        val children = root.listFiles()?.sortedBy { it.name.lowercase(Locale.US) } ?: emptyList()
        if (children.isEmpty()) {
            sendBatch(
                descriptor = descriptor,
                items = emptyList(),
                requestId = requestId,
                parentPath = root.absolutePath,
                nextToken = null
            )
            return
        }

        val batch = mutableListOf<FileMetadataPayload>()
        val scopedToken = pageToken?.takeIf { token ->
            token.startsWith(root.absolutePath)
        }
        var startGate = scopedToken != null
        for (child in children) {
            if (!includeHidden && child.isHidden) continue
            if (startGate) {
                if (child.absolutePath == scopedToken) {
                    startGate = false
                }
                continue
            }

            buildMetadata(child, includeHidden)?.let { payload ->
                batch += payload
                if (batch.size >= batchSize) {
                    val nextToken = payload.absolutePath
                    sendBatch(
                        descriptor = descriptor,
                        items = batch.toList(),
                        requestId = requestId,
                        parentPath = root.absolutePath,
                        nextToken = nextToken
                    )
                    batch.clear()
                }
            }
        }

        if (batch.isNotEmpty()) {
            val nextToken = batch.last().absolutePath
            sendBatch(
                descriptor = descriptor,
                items = batch.toList(),
                requestId = requestId,
                parentPath = root.absolutePath,
                nextToken = nextToken
            )
        }
    }

    private fun buildMetadata(file: File, includeHidden: Boolean): FileMetadataPayload? {
        if (!file.exists()) return null
        if (!includeHidden && file.isHidden) return null
        val mimeType = deduceMimeType(file)
        val thumb = if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            createThumbnailBase64(file, mimeType)
        } else null

        return FileMetadataPayload(
            filename = file.name.ifBlank { file.absolutePath },
            absolutePath = file.absolutePath,
            fileSize = if (file.isDirectory) 0L else file.length(),
            createdTimestamp = createdTimestamp(file),
            modifiedTimestamp = file.lastModified(),
            mimeType = mimeType,
            thumbnailBase64 = thumb,
            fileTypeIcon = resolveIcon(mimeType, file),
            isDirectory = file.isDirectory
        )
    }

    private fun sendBatch(
        descriptor: StorageDescriptor,
        items: List<FileMetadataPayload>,
        requestId: String?,
        parentPath: String?,
        nextToken: String?,
    ) {
        val json = JSONObject().apply {
            put("type", "LIST_FILES_BATCH")
            put("storage_id", descriptor.id)
            put("storage_label", descriptor.label)
            put("parent_path", parentPath)
            requestId?.let { put("request_id", it) }
            nextToken?.let { put("next_page_token", it) }
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            put("items", array)
        }
        sendJson(json)
    }

    private fun sendListComplete(requestId: String?, path: String?) {
        val json = JSONObject().apply {
            put("type", "LIST_FILES_COMPLETE")
            requestId?.let { put("request_id", it) }
            path?.let { put("path", it) }
        }
        sendJson(json)
    }

    private fun sendListError(reason: String, requestId: String?, path: String?) {
        val json = JSONObject().apply {
            put("type", "LIST_FILES_ERROR")
            put("reason", reason)
            requestId?.let { put("request_id", it) }
            path?.let { put("path", it) }
        }
        sendJson(json)
    }

    private fun sendFileInit(session: FileTransferSession) {
        val json = JSONObject().apply {
            put("type", "FILE_TRANSFER_INIT")
            put("transfer_id", session.transferId)
            put("absolute_path", session.file.absolutePath)
            put("file_size", session.file.length())
            put("chunk_size", session.chunkSize)
            put("mime_type", deduceMimeType(session.file))
            put("resume_offset", session.offset)
            session.requestId?.let { put("request_id", it) }
        }
        sendJson(json)
    }

    private fun sendFileProgress(session: FileTransferSession) {
        val json = JSONObject().apply {
            put("type", "FILE_TRANSFER_PROGRESS")
            put("transfer_id", session.transferId)
            put("bytes_sent", session.offset)
            put("file_size", session.file.length())
            session.requestId?.let { put("request_id", it) }
        }
        sendJson(json)
    }

    private fun sendFileComplete(session: FileTransferSession) {
        val json = JSONObject().apply {
            put("type", "FILE_TRANSFER_COMPLETE")
            put("transfer_id", session.transferId)
            put("absolute_path", session.file.absolutePath)
            put("file_size", session.file.length())
            session.requestId?.let { put("request_id", it) }
        }
        sendJson(json)
    }

    private fun sendFilePaused(session: FileTransferSession, reason: String) {
        val json = JSONObject().apply {
            put("type", "FILE_TRANSFER_PAUSED")
            put("transfer_id", session.transferId)
            put("absolute_path", session.file.absolutePath)
            put("bytes_sent", session.offset)
            put("reason", reason)
            session.requestId?.let { put("request_id", it) }
        }
        sendJson(json)
    }

    private fun sendFileError(
        absolutePath: String,
        requestId: String?,
        transferId: String?,
        reason: String,
    ) {
        val json = JSONObject().apply {
            put("type", "FILE_TRANSFER_ERROR")
            put("absolute_path", absolutePath)
            put("reason", reason)
            requestId?.let { put("request_id", it) }
            transferId?.let { put("transfer_id", it) }
        }
        sendJson(json)
    }

    private fun sendJson(payload: JSONObject) {
        if (dataChannel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "DataChannel not open; dropping ${payload.optString("type")}")
            return
        }
        try {
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.wrap(bytes)
            dataChannel.send(DataChannel.Buffer(buffer, false))
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to send payload ${payload.optString("type")}", ex)
        }
    }

    private fun sendChunk(
        transferId: String,
        offset: Long,
        chunk: ByteArray,
        isLast: Boolean,
    ): Boolean {
        if (dataChannel.state() != DataChannel.State.OPEN) {
            return false
        }
        val transferBytes = transferId.toByteArray(Charsets.UTF_8)
        val headerLength = 4 + 2 + 2 + 8 + 4 + 4 + 2 + transferBytes.size
        val buffer = ByteBuffer.allocate(headerLength + chunk.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(CHUNK_MAGIC)
        buffer.putShort(CHUNK_HEADER_VERSION.toShort())
        buffer.putShort(headerLength.toShort())
        buffer.putLong(offset)
        buffer.putInt(chunk.size)
        buffer.putInt(if (isLast) 1 else 0)
        buffer.putShort(transferBytes.size.toShort())
        buffer.put(transferBytes)
        buffer.put(chunk)
        buffer.flip()
        return try {
            dataChannel.send(DataChannel.Buffer(buffer, true))
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to send chunk", ex)
            false
        }
    }

    private fun createdTimestamp(file: File): Long {
        return try {
            val attrs: BasicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attrs.creationTime().toMillis()
        } catch (_: Exception) {
            file.lastModified()
        }
    }

    private fun deduceMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase(Locale.US)
        val mime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else null
        return mime ?: "application/octet-stream"
    }

    private fun resolveIcon(mimeType: String, file: File): String {
        if (file.isDirectory) return "folder"
        return when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("zip") || mimeType.contains("rar") -> "archive"
            mimeType.contains("msword") || mimeType.contains("officedocument") -> "doc"
            mimeType.contains("text") -> "text"
            else -> "file"
        }
    }

    private fun createThumbnailBase64(file: File, mimeType: String): String? {
        return try {
            val bitmap = if (mimeType.startsWith("image/")) {
                decodeImageThumbnail(file)
            } else {
                decodeVideoThumbnail(file)
            } ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            bitmap.recycle()
            base64
        } catch (ex: Exception) {
            Log.w(TAG, "Thumbnail failed for ${file.absolutePath}", ex)
            null
        }
    }

    private fun decodeImageThumbnail(file: File): android.graphics.Bitmap? {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        val maxSize = 256
        var sampleSize = 1
        while ((options.outHeight / sampleSize) > maxSize || (options.outWidth / sampleSize) > maxSize) {
            sampleSize *= 2
        }
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        return bitmap?.let {
            val scaled = it.scale(maxSize, maxSize)
            it.recycle()
            scaled
        }
    }

    private fun decodeVideoThumbnail(file: File): android.graphics.Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(256, 256), null)
            } else {
                ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Video thumbnail failed for ${file.absolutePath}", ex)
            null
        }
    }

    private fun resolveTargets(path: String?): List<StorageDescriptor> {
        if (!path.isNullOrBlank()) {
            val file = File(path)
            return if (file.exists()) {
                listOf(
                    StorageDescriptor(
                        id = "direct:${file.absolutePath.hashCode()}",
                        label = file.name.ifBlank { file.absolutePath },
                        root = file
                    )
                )
            } else emptyList()
        }

        val descriptors = mutableListOf<StorageDescriptor>()
        context.filesDir?.let {
            descriptors += StorageDescriptor(
                id = "internal",
                label = "Internal storage",
                root = it
            )
        }
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let {
            descriptors += StorageDescriptor(
                id = "primary",
                label = "Shared storage",
                root = it
            )
        }
        context.getExternalFilesDirs(null)
            ?.filterNotNull()
            ?.filter { dir -> dir != context.filesDir }
            ?.forEachIndexed { index, dir ->
                descriptors += StorageDescriptor(
                    id = "secondary_$index",
                    label = "External ${index + 1}",
                    root = dir
                )
            }
        return descriptors
    }

    /**
     * Stop an active stream by transfer ID
     * @param transferId The transfer ID to stop
     * @return true if stream was found and stopped, false otherwise
     */
    fun stopStream(transferId: String): Boolean {
        val session = activeTransfers.remove(transferId)
        return if (session != null) {
            Log.d(TAG, "Stopping stream: $transferId for file: ${session.file.name}")
            
            // Cancel the job
            session.job?.cancel()
            
            // Send stream stopped notification
            val json = JSONObject().apply {
                put("type", "FILE_TRANSFER_STOPPED")
                put("transfer_id", transferId)
                put("absolute_path", session.file.absolutePath)
                put("bytes_sent", session.offset)
                session.requestId?.let { put("request_id", it) }
            }
            sendJson(json)
            
            Log.d(TAG, "Successfully stopped stream: $transferId")
            true
        } else {
            Log.w(TAG, "Stream not found for transfer_id: $transferId")
            false
        }
    }

    private fun hasReadAccess(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Environment.isExternalStorageManager() ||
                        hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                        hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                        hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ||
                        hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            else -> hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        val status = ContextCompat.checkSelfPermission(context, permission)
        return status == PackageManager.PERMISSION_GRANTED
    }
}

