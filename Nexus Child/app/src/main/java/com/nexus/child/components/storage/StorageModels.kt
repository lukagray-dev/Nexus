package com.nexus.child.components.storage

/**
 * Data model representing a file or directory item on the child device's internal storage.
 *
 * Hey friend! This model holds all relevant file metadata that the parent console needs:
 * - Basic details: file name, absolute filesystem path, size in bytes, and last modified epoch millis.
 * - Flag: whether this entry is a directory (folder) or a regular file.
 * - Categorization: standard MIME type and normalized category (image, video, audio, document, archive, other).
 * - Thumbnail: an optional lightweight compressed Base64 JPEG string for fast visual previewing in the file list.
 */
data class StorageItem(
    val name: String,
    val absolutePath: String,
    val size: Long,
    val modified: Long,
    val isDirectory: Boolean,
    val mimeType: String,
    val category: String,
    val thumbnailBase64: String? = null
) {
    /**
     * Serializes this StorageItem into a valid JSON string.
     * We serialize manually to avoid mock/stub problems during pure JVM unit testing.
     */
    fun toJsonString(): String {
        val thumbJson = if (thumbnailBase64 != null) "\"$thumbnailBase64\"" else "null"
        return "{" +
            "\"name\":\"${escapeJson(name)}\"," +
            "\"absolutePath\":\"${escapeJson(absolutePath)}\"," +
            "\"size\":$size," +
            "\"modified\":$modified," +
            "\"isDirectory\":$isDirectory," +
            "\"mimeType\":\"${escapeJson(mimeType)}\"," +
            "\"category\":\"${escapeJson(category)}\"," +
            "\"thumbnail\":$thumbJson" +
            "}"
    }

    companion object {
        /**
         * Safely escapes special characters for JSON values.
         */
        fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }
}

/**
 * Encapsulates a progressive paginated batch of storage items sent across the DataChannel.
 *
 * Hey friend! In large folders like DCIM with thousands of photos, processing all thumbnails
 * at once would choke CPU, memory, and WebRTC queues. Instead, we use on-demand pagination:
 * child only processes a slice (e.g. 40 items) at a time, informing parent if more items exist!
 */
data class StorageListBatch(
    val path: String,
    val items: List<StorageItem>,
    val isLast: Boolean,
    val totalCount: Int,
    val offset: Int = 0,
    val limit: Int = 40,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val dirModified: Long = 0L,
    val requestId: String? = null
) {
    /**
     * Serializes the batch into a DataChannel JSON packet.
     */
    fun toJsonString(): String {
        val itemsJson = items.joinToString(separator = ",", prefix = "[", postfix = "]") {
            it.toJsonString()
        }
        val reqJson = if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"
        return "{" +
            "\"type\":\"STORAGE_LIST_BATCH\"," +
            "\"path\":\"${StorageItem.escapeJson(path)}\"," +
            "\"items\":$itemsJson," +
            "\"isLast\":$isLast," +
            "\"totalCount\":$totalCount," +
            "\"offset\":$offset," +
            "\"limit\":$limit," +
            "\"hasMore\":$hasMore," +
            "\"nextOffset\":$nextOffset," +
            "\"dirModified\":$dirModified," +
            "\"requestId\":$reqJson" +
            "}"
    }
}

/**
 * Result of a lightweight directory modification check for smart desktop caching.
 */
data class DirectoryCheckResult(
    val isUnchanged: Boolean,
    val currentModified: Long,
    val currentCount: Int
)

/**
 * Metadata header initializing a chunked file transfer session (for download or in-app preview).
 */
data class StorageFileStart(
    val transferId: String,
    val path: String,
    val fileName: String,
    val size: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val mimeType: String,
    val purpose: String = "download", // "download" or "preview"
    val requestId: String? = null
) {
    fun toJsonString(): String {
        val reqJson = if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"
        return "{" +
            "\"type\":\"STORAGE_FILE_START\"," +
            "\"transferId\":\"${StorageItem.escapeJson(transferId)}\"," +
            "\"path\":\"${StorageItem.escapeJson(path)}\"," +
            "\"fileName\":\"${StorageItem.escapeJson(fileName)}\"," +
            "\"size\":$size," +
            "\"totalChunks\":$totalChunks," +
            "\"chunkSize\":$chunkSize," +
            "\"mimeType\":\"${StorageItem.escapeJson(mimeType)}\"," +
            "\"purpose\":\"${StorageItem.escapeJson(purpose)}\"," +
            "\"requestId\":$reqJson" +
            "}"
    }
}

/**
 * A single slice of file data (64KB Base64) transmitted over the DataChannel.
 */
data class StorageFileChunk(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: String
) {
    fun toJsonString(): String {
        return "{" +
            "\"type\":\"STORAGE_FILE_CHUNK\"," +
            "\"transferId\":\"${StorageItem.escapeJson(transferId)}\"," +
            "\"chunkIndex\":$chunkIndex," +
            "\"totalChunks\":$totalChunks," +
            "\"data\":\"$data\"" +
            "}"
    }
}
