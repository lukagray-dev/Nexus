package nexus.android.child.commands

/**
 * Request descriptor for LIST_FILES.
 */
data class FileListRequest(
    val directoryPath: String? = null,
    val includeHidden: Boolean = false,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val pageToken: String? = null,
    val requestId: String? = null
) {
    companion object {
        const val DEFAULT_BATCH_SIZE = 60
    }
}

/**
 * Request descriptor for GET_FILE streaming.
 */
data class FileStreamRequest(
    val absolutePath: String,
    val resumeOffset: Long = 0L,
    val chunkSizeBytes: Int = DEFAULT_CHUNK_BYTES,
    val requestId: String? = null,
    val transferId: String? = null
) {
    companion object {
        const val DEFAULT_CHUNK_BYTES = 64 * 1024
    }
}

/**
 * Contract that allows CommandHandlerImpl to delegate file-system operations
 * without knowing the underlying implementation.
 */
interface StorageHandler {
    fun listFiles(request: FileListRequest)
    fun streamFile(request: FileStreamRequest)
}

