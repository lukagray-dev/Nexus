package com.nexus.child.components.storage

/**
 * Interface defining internal storage operations, file system enumeration,
 * chunked file streaming, and remote deletion on the child Android device.
 *
 * Hey friend! This interface isolates our Android file system and MediaStore calls
 * from the WebRTC command handler, making unit testing clean and mockable.
 */
interface IStorageController {

    /**
     * Checks if the app has permission to access files on external/internal storage.
     * On Android 11+ (API 30+), verifies Environment.isExternalStorageManager().
     * On older versions, checks READ_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(): Boolean

    /**
     * Returns the primary internal shared storage root path,
     * typically "/storage/emulated/0".
     */
    fun getDefaultStoragePath(): String

    /**
     * Checks whether directory contents have changed since desktop last cached them.
     * This provides instantaneous verification without re-scanning thousands of files.
     *
     * @param path Directory path to check
     * @param cachedModified Desktop's cached directory lastModified epoch
     * @param cachedCount Desktop's cached file/folder count
     */
    fun checkDirectoryModified(
        path: String,
        cachedModified: Long,
        cachedCount: Int
    ): DirectoryCheckResult

    /**
     * Enumerates a slice of directory contents on the child device with pagination,
     * generating compressed thumbnails only for the requested slice (e.g. 40 items).
     *
     * @param path Target filesystem directory path to list.
     * @param offset Starting index of the item slice (0 for first page).
     * @param limit Maximum number of items to return in this page.
     * @param onBatch Callback invoked with the paginated batch.
     * @param onError Callback invoked if reading fails or permission is denied.
     */
    fun listDirectory(
        path: String,
        offset: Int = 0,
        limit: Int = 40,
        onBatch: (StorageListBatch) -> Unit,
        onError: (reason: String) -> Unit
    )

    /**
     * Reads a file from storage and streams it in 64KB Base64 chunks over the DataChannel.
     *
     * @param path Absolute path of the file to stream.
     * @param transferId Unique identifier for this transfer session.
     * @param purpose Transfer intent: "download" (local file save) or "preview" (in-app viewer).
     * @param onStart Callback invoked with transfer header metadata before streaming begins.
     * @param onChunk Callback invoked for each 64KB chunk.
     * @param onComplete Callback invoked when the entire file has been transmitted.
     * @param onError Callback invoked if the transfer fails.
     */
    fun readFileStream(
        path: String,
        transferId: String,
        purpose: String,
        onStart: (StorageFileStart) -> Unit,
        onChunk: (StorageFileChunk) -> Unit,
        onComplete: () -> Unit,
        onError: (reason: String) -> Unit
    )

    /**
     * Cancels an ongoing file transfer session by transferId.
     *
     * @param transferId The ID of the transfer session to abort.
     * @return true if an active transfer was found and cancelled, false otherwise.
     */
    fun cancelTransfer(transferId: String): Boolean

    /**
     * Permanently deletes a file from the child device's storage.
     * Also requests the Android MediaScanner to rescan the path so the system
     * gallery/MediaStore updates immediately.
     *
     * @param path Absolute path of the file to delete.
     * @return Result.success(true) if deleted, or Result.failure with error message.
     */
    fun deleteFile(path: String): Result<Boolean>

    /**
     * Cancels any active directory enumeration and ongoing file transfers,
     * releasing coroutine scopes and memory buffers.
     */
    fun cleanup()
}
