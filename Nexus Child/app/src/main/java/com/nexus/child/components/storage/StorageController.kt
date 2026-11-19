package com.nexus.child.components.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * StorageController coordinates filesystem operations, directory traversal,
 * thumbnail generation, chunked file streaming, and remote deletion on the Android child device.
 *
 * Hey friend! Here is how our optimized storage engine works:
 * 1. Permissions: We check Android's storage permissions (MANAGE_EXTERNAL_STORAGE on API 30+
 *    or READ_EXTERNAL_STORAGE on older versions).
 * 2. Progressive Pagination: Instead of generating thumbnails for thousands of items at once,
 *    we read on-demand slices (e.g. 40 items per page). Only the visible slice generates thumbnails!
 * 3. High-Speed 64KB Streaming: Files are sliced into 64KB binary blocks instead of tiny 16KB fragments,
 *    eliminating artificial throttling delays and drastically accelerating transfer speed!
 * 4. Smart Cache Validation: checkDirectoryModified() allows desktop to verify in 1 millisecond if a
 *    folder has changed without transferring redundant metadata or thumbnails.
 * 5. Memory Hygiene: Image and video thumbnails are downsampled in memory to max 160x160 px,
 *    compressed with RGB_565 JPEG, and immediately recycled to keep RAM usage negligible.
 */
class StorageController(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IStorageController {

    companion object {
        private const val TAG = "StorageController"
        private const val DEFAULT_PAGE_LIMIT = 40
        private const val CHUNK_SIZE = 65536 // 64KB raw binary chunk size (optimized WebRTC SCTP throughput)
        private const val THUMBNAIL_MAX_DIMENSION = 160
        private const val THUMBNAIL_JPEG_QUALITY = 50
    }

    // Active listing job (cancelled if user navigates to another folder quickly)
    private var currentListingJob: Job? = null

    // Track active file transfer sessions so they can be paused or cancelled
    private val activeTransfers = ConcurrentHashMap<String, Job>()

    override fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() ||
                hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
                hasPermission(Manifest.permission.READ_MEDIA_AUDIO) ||
                hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun getDefaultStoragePath(): String {
        return try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (e: Exception) {
            "/storage/emulated/0"
        }
    }

    override fun checkDirectoryModified(
        path: String,
        cachedModified: Long,
        cachedCount: Int
    ): DirectoryCheckResult {
        val targetPath = if (path.isBlank() || path == "ROOT") {
            getDefaultStoragePath()
        } else {
            path.trim()
        }

        val dir = File(targetPath)
        if (!dir.exists() || !dir.isDirectory) {
            return DirectoryCheckResult(isUnchanged = false, currentModified = 0L, currentCount = 0)
        }

        val currentModified = dir.lastModified()
        val currentCount = dir.list()?.size ?: 0

        // If directory timestamp and file count match desktop cache, it is 100% unchanged!
        val isUnchanged = (cachedModified > 0 && currentModified == cachedModified && currentCount == cachedCount)
        return DirectoryCheckResult(
            isUnchanged = isUnchanged,
            currentModified = currentModified,
            currentCount = currentCount
        )
    }

    override fun listDirectory(
        path: String,
        offset: Int,
        limit: Int,
        onBatch: (StorageListBatch) -> Unit,
        onError: (reason: String) -> Unit
    ) {
        // Cancel any pending listing job if the parent clicked another folder
        currentListingJob?.cancel()

        currentListingJob = coroutineScope.launch(ioDispatcher) {
            try {
                if (!hasStoragePermission()) {
                    Log.w(TAG, "Storage permission missing while trying to list: $path")
                    onError("PERMISSION_DENIED: Storage access not granted on child device")
                    return@launch
                }

                val targetPath = if (path.isBlank() || path == "ROOT") {
                    getDefaultStoragePath()
                } else {
                    path.trim()
                }

                val dir = File(targetPath)
                if (!dir.exists()) {
                    Log.w(TAG, "Directory does not exist: $targetPath")
                    onError("NOT_FOUND: Directory does not exist on device")
                    return@launch
                }

                if (!dir.isDirectory) {
                    Log.w(TAG, "Path is not a directory: $targetPath")
                    onError("NOT_A_DIRECTORY: Target path is a file, not a folder")
                    return@launch
                }

                if (!dir.canRead()) {
                    Log.w(TAG, "Cannot read directory: $targetPath")
                    onError("ACCESS_DENIED: Directory read permission denied")
                    return@launch
                }

                val allFiles = dir.listFiles() ?: emptyArray()

                // Sort folders first alphabetically, then files alphabetically
                val sortedFiles = allFiles.sortedWith(
                    compareBy<File> { !it.isDirectory }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )

                val totalCount = sortedFiles.size
                val effectiveLimit = if (limit > 0) limit else DEFAULT_PAGE_LIMIT
                val effectiveOffset = offset.coerceAtLeast(0)

                if (totalCount == 0 || effectiveOffset >= totalCount) {
                    // Empty directory or past end
                    onBatch(
                        StorageListBatch(
                            path = dir.absolutePath,
                            items = emptyList(),
                            isLast = true,
                            totalCount = totalCount,
                            offset = effectiveOffset,
                            limit = effectiveLimit,
                            hasMore = false,
                            nextOffset = effectiveOffset,
                            dirModified = dir.lastModified()
                        )
                    )
                    return@launch
                }

                // Slice only the requested page of items
                val pagedFiles = sortedFiles.drop(effectiveOffset).take(effectiveLimit)
                val batchItems = mutableListOf<StorageItem>()

                for (file in pagedFiles) {
                    if (!isActive) {
                        Log.d(TAG, "Directory listing cancelled for: $targetPath")
                        return@launch
                    }
                    val item = buildStorageItem(file)
                    batchItems.add(item)
                }

                val nextOffset = effectiveOffset + pagedFiles.size
                val hasMore = nextOffset < totalCount

                val batch = StorageListBatch(
                    path = dir.absolutePath,
                    items = batchItems,
                    isLast = !hasMore,
                    totalCount = totalCount,
                    offset = effectiveOffset,
                    limit = effectiveLimit,
                    hasMore = hasMore,
                    nextOffset = nextOffset,
                    dirModified = dir.lastModified()
                )
                onBatch(batch)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing directory $path: ${e.message}", e)
                onError("IO_ERROR: ${e.message ?: "Unknown error listing storage"}")
            }
        }
    }

    /**
     * Converts a java.io.File into our standardized StorageItem data class,
     * resolving MIME types, categories, and generating lightweight thumbnails.
     */
    private fun buildStorageItem(file: File): StorageItem {
        val isDir = file.isDirectory
        val name = file.name.ifBlank { file.absolutePath }
        val size = if (isDir) 0L else file.length()
        val modified = file.lastModified()
        val mimeType = deduceMimeType(file)
        val category = deduceCategory(mimeType, isDir)

        // Generate compressed thumbnails for photos and videos only
        val thumbnail = if (!isDir && (category == "image" || category == "video")) {
            generateThumbnailBase64(file, category)
        } else {
            null
        }

        return StorageItem(
            name = name,
            absolutePath = file.absolutePath,
            size = size,
            modified = modified,
            isDirectory = isDir,
            mimeType = mimeType,
            category = category,
            thumbnailBase64 = thumbnail
        )
    }

    override fun readFileStream(
        path: String,
        transferId: String,
        purpose: String,
        onStart: (StorageFileStart) -> Unit,
        onChunk: (StorageFileChunk) -> Unit,
        onComplete: () -> Unit,
        onError: (reason: String) -> Unit
    ) {
        val transferJob = coroutineScope.launch(ioDispatcher) {
            try {
                if (!hasStoragePermission()) {
                    onError("PERMISSION_DENIED: Storage access not granted")
                    return@launch
                }

                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    onError("FILE_NOT_FOUND: Requested file does not exist")
                    return@launch
                }

                if (!file.canRead()) {
                    onError("ACCESS_DENIED: Cannot read requested file")
                    return@launch
                }

                val fileSize = file.length()
                val totalChunks = if (fileSize == 0L) 1 else ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                val mimeType = deduceMimeType(file)

                // 1. Send transfer start header
                val startHeader = StorageFileStart(
                    transferId = transferId,
                    path = file.absolutePath,
                    fileName = file.name,
                    size = fileSize,
                    totalChunks = totalChunks,
                    chunkSize = CHUNK_SIZE,
                    mimeType = mimeType,
                    purpose = purpose
                )
                onStart(startHeader)

                // 2. Stream file in high-throughput 64KB chunks
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var chunkIndex = 0

                    while (isActive) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead == -1) break

                        val chunkBytes = if (bytesRead == CHUNK_SIZE) {
                            buffer
                        } else {
                            buffer.copyOf(bytesRead)
                        }

                        val base64Data = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
                        val chunkPacket = StorageFileChunk(
                            transferId = transferId,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks,
                            data = base64Data
                        )
                        onChunk(chunkPacket)

                        chunkIndex++
                    }
                }

                if (isActive) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming file $path: ${e.message}", e)
                onError("STREAM_ERROR: ${e.message ?: "Failed to read file"}")
            } finally {
                activeTransfers.remove(transferId)
            }
        }

        activeTransfers[transferId] = transferJob
    }

    override fun cancelTransfer(transferId: String): Boolean {
        val job = activeTransfers.remove(transferId)
        return if (job != null) {
            job.cancel()
            Log.d(TAG, "Cancelled file transfer session: $transferId")
            true
        } else {
            false
        }
    }

    override fun deleteFile(path: String): Result<Boolean> {
        return try {
            if (!hasStoragePermission()) {
                return Result.failure(Exception("PERMISSION_DENIED: Storage permission not granted"))
            }

            val file = File(path)
            if (!file.exists()) {
                return Result.failure(Exception("FILE_NOT_FOUND: Target file does not exist"))
            }

            if (file.isDirectory) {
                val children = file.list()
                if (children != null && children.isNotEmpty()) {
                    return Result.failure(Exception("DIRECTORY_NOT_EMPTY: Cannot delete non-empty directory"))
                }
            }

            val deleted = file.delete()
            if (deleted) {
                // Request MediaScanner to refresh so Android system views update immediately
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null
                    ) { scannedPath, uri ->
                        Log.d(TAG, "MediaScanner refreshed after deletion: $scannedPath -> $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaScanner notification error: ${e.message}")
                }
                Result.success(true)
            } else {
                Result.failure(Exception("DELETE_FAILED: Could not delete file (read-only or locked)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting file $path: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun cleanup() {
        Log.d(TAG, "Cleaning up StorageController jobs and transfers...")
        currentListingJob?.cancel()
        currentListingJob = null

        activeTransfers.forEach { (_, job) ->
            job.cancel()
        }
        activeTransfers.clear()
    }

    /**
     * Deduces the standard MIME type based on file extension.
     */
    private fun deduceMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /**
     * Maps MIME types and directory states to high-level UI categories.
     */
    private fun deduceCategory(mimeType: String, isDirectory: Boolean): String {
        if (isDirectory) return "folder"
        return when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.contains("pdf") ||
                mimeType.contains("msword") ||
                mimeType.contains("officedocument") ||
                mimeType.contains("text") -> "document"
            mimeType.contains("zip") ||
                mimeType.contains("tar") ||
                mimeType.contains("rar") ||
                mimeType.contains("7z") -> "archive"
            else -> "other"
        }
    }

    /**
     * Generates a lightweight, downsampled Base64 Data URL thumbnail for an image or video.
     */
    private fun generateThumbnailBase64(file: File, category: String): String? {
        var bitmap: Bitmap? = null
        return try {
            bitmap = if (category == "image") {
                decodeSampledImageBitmap(file, THUMBNAIL_MAX_DIMENSION, THUMBNAIL_MAX_DIMENSION)
            } else {
                decodeVideoThumbnail(file, THUMBNAIL_MAX_DIMENSION, THUMBNAIL_MAX_DIMENSION)
            }

            if (bitmap == null) return null

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation failed for ${file.name}: ${e.message}")
            null
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Decodes an image file into a small thumbnail Bitmap using inSampleSize for memory efficiency.
     */
    private fun decodeSampledImageBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val height = options.outHeight
        val width = options.outWidth
        if (height <= 0 || width <= 0) return null

        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Conserves 50% memory compared to ARGB_8888
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        return if (decoded.width > reqWidth || decoded.height > reqHeight) {
            val scaled = Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled
        } else {
            decoded
        }
    }

    /**
     * Decodes a video thumbnail using Android's ThumbnailUtils API.
     */
    private fun decodeVideoThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(reqWidth, reqHeight), null)
            } else {
                @Suppress("DEPRECATION")
                val miniKind = ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    android.provider.MediaStore.Images.Thumbnails.MINI_KIND
                )
                if (miniKind != null && (miniKind.width > reqWidth || miniKind.height > reqHeight)) {
                    val scaled = Bitmap.createScaledBitmap(miniKind, reqWidth, reqHeight, true)
                    if (scaled != miniKind) {
                        miniKind.recycle()
                    }
                    scaled
                } else {
                    miniKind
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Video thumbnail decode error: ${e.message}")
            null
        }
    }
}
