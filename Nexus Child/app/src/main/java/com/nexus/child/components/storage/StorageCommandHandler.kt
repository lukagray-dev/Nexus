package com.nexus.child.components.storage

import android.util.Log
import java.util.UUID

/**
 * StorageCommandHandler dispatches incoming DataChannel storage commands to the
 * StorageController and formats outbound JSON responses and telemetry batches.
 *
 * Hey friend! Here is the updated high-performance command matrix handled here:
 * 1. "STORAGE_CHECK_DIR" or {"type":"STORAGE_CHECK_DIR", "path":"...", "cachedModified":..., "cachedCount":...}:
 *    Validates desktop directory cache. If unchanged, replies with STORAGE_DIR_UNCHANGED. If modified, replies
 *    with STORAGE_DIR_CHANGED and streams the updated first page!
 * 2. "STORAGE_LIST" with pagination ("path", "offset", "limit"):
 *    Streams on-demand 40-item slices (STORAGE_LIST_BATCH) with thumbnail generation ONLY for that slice.
 * 3. "STORAGE_GET_FILE" with 64KB high-speed throughput streaming.
 * 4. "STORAGE_CANCEL_TRANSFER" to abort in-flight transfers.
 * 5. "STORAGE_DELETE_FILE" to remotely delete child files.
 */
class StorageCommandHandler(
    private val storageController: IStorageController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "StorageCmdHandler"

        // Commands
        const val CMD_STORAGE_CHECK_DIR = "STORAGE_CHECK_DIR"
        const val CMD_STORAGE_LIST_PREFIX = "STORAGE_LIST"
        const val CMD_STORAGE_GET_FILE_PREFIX = "STORAGE_GET_FILE"
        const val CMD_STORAGE_CANCEL_PREFIX = "STORAGE_CANCEL"
        const val CMD_STORAGE_DELETE_PREFIX = "STORAGE_DELETE"

        const val TYPE_STORAGE_CHECK_DIR = "STORAGE_CHECK_DIR"
        const val TYPE_STORAGE_LIST = "STORAGE_LIST"
        const val TYPE_STORAGE_GET_FILE = "STORAGE_GET_FILE"
        const val TYPE_STORAGE_CANCEL_TRANSFER = "STORAGE_CANCEL_TRANSFER"
        const val TYPE_STORAGE_DELETE_FILE = "STORAGE_DELETE_FILE"

        // Response types
        const val TYPE_STORAGE_DIR_UNCHANGED = "STORAGE_DIR_UNCHANGED"
        const val TYPE_STORAGE_DIR_CHANGED = "STORAGE_DIR_CHANGED"
        const val TYPE_STORAGE_LIST_BATCH = "STORAGE_LIST_BATCH"
        const val TYPE_STORAGE_LIST_ERROR = "STORAGE_LIST_ERROR"
        const val TYPE_STORAGE_FILE_START = "STORAGE_FILE_START"
        const val TYPE_STORAGE_FILE_CHUNK = "STORAGE_FILE_CHUNK"
        const val TYPE_STORAGE_FILE_COMPLETE = "STORAGE_FILE_COMPLETE"
        const val TYPE_STORAGE_FILE_ERROR = "STORAGE_FILE_ERROR"
        const val TYPE_STORAGE_DELETE_SUCCESS = "STORAGE_DELETE_SUCCESS"
        const val TYPE_STORAGE_DELETE_ERROR = "STORAGE_DELETE_ERROR"
    }

    /**
     * Inspects an incoming DataChannel message and executes the appropriate storage command.
     *
     * @param command Raw message received across the DataChannel.
     * @return true if handled by StorageCommandHandler; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        val trimmed = command.trim()

        return when {
            // Case 1: Directory Cache Validation (STORAGE_CHECK_DIR)
            trimmed.startsWith("$CMD_STORAGE_CHECK_DIR:") ||
                (trimmed.startsWith("{") && trimmed.contains("\"$TYPE_STORAGE_CHECK_DIR\"")) -> {
                handleCheckDir(trimmed)
                true
            }

            // Case 2: Simple prefix commands (e.g. "STORAGE_LIST:/path:0:40" or "STORAGE_LIST")
            trimmed == CMD_STORAGE_LIST_PREFIX || trimmed.startsWith("$CMD_STORAGE_LIST_PREFIX:") -> {
                val parts = trimmed.removePrefix(CMD_STORAGE_LIST_PREFIX).removePrefix(":").split(":")
                val path = parts.getOrNull(0)?.trim()?.ifBlank { null } ?: storageController.getDefaultStoragePath()
                val offset = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val limit = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 40
                executeList(path = path, offset = offset, limit = limit, requestId = null)
                true
            }

            trimmed.startsWith("$CMD_STORAGE_DELETE_PREFIX:") -> {
                val path = trimmed.substringAfter(":").trim()
                executeDelete(path = path, requestId = null)
                true
            }

            trimmed.startsWith("$CMD_STORAGE_CANCEL_PREFIX:") -> {
                val transferId = trimmed.substringAfter(":").trim()
                executeCancel(transferId = transferId)
                true
            }

            // Case 3: JSON-formatted commands
            trimmed.startsWith("{") && trimmed.contains("\"$TYPE_STORAGE_LIST\"") -> {
                val path = extractJsonField(trimmed, "path") ?: storageController.getDefaultStoragePath()
                val offset = extractJsonLongField(trimmed, "offset")?.toInt() ?: 0
                val limit = extractJsonLongField(trimmed, "limit")?.toInt() ?: 40
                val requestId = extractJsonField(trimmed, "requestId")
                executeList(path = path, offset = offset, limit = limit, requestId = requestId)
                true
            }

            trimmed.startsWith("{") && trimmed.contains("\"$TYPE_STORAGE_GET_FILE\"") -> {
                val path = extractJsonField(trimmed, "path")
                if (path.isNullOrBlank()) {
                    sendError(TYPE_STORAGE_FILE_ERROR, "MISSING_PATH", null, null)
                    return true
                }
                val transferId = extractJsonField(trimmed, "transferId") ?: UUID.randomUUID().toString()
                val purpose = extractJsonField(trimmed, "purpose") ?: "download"
                val requestId = extractJsonField(trimmed, "requestId")
                executeGetFile(path = path, transferId = transferId, purpose = purpose, requestId = requestId)
                true
            }

            trimmed.startsWith("{") && trimmed.contains("\"$TYPE_STORAGE_CANCEL_TRANSFER\"") -> {
                val transferId = extractJsonField(trimmed, "transferId") ?: ""
                executeCancel(transferId = transferId)
                true
            }

            trimmed.startsWith("{") && trimmed.contains("\"$TYPE_STORAGE_DELETE_FILE\"") -> {
                val path = extractJsonField(trimmed, "path")
                if (path.isNullOrBlank()) {
                    sendError(TYPE_STORAGE_DELETE_ERROR, "MISSING_PATH", null, null)
                    return true
                }
                val requestId = extractJsonField(trimmed, "requestId")
                executeDelete(path = path, requestId = requestId)
                true
            }

            else -> false
        }
    }

    private fun handleCheckDir(raw: String) {
        val path: String
        val cachedModified: Long
        val cachedCount: Int

        if (raw.startsWith("{")) {
            path = extractJsonField(raw, "path") ?: storageController.getDefaultStoragePath()
            cachedModified = extractJsonLongField(raw, "cachedModified") ?: 0L
            cachedCount = extractJsonLongField(raw, "cachedCount")?.toInt() ?: 0
        } else {
            val parts = raw.removePrefix("$CMD_STORAGE_CHECK_DIR:").split(":")
            path = parts.getOrNull(0)?.trim()?.ifBlank { null } ?: storageController.getDefaultStoragePath()
            cachedModified = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
            cachedCount = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
        }

        val check = storageController.checkDirectoryModified(path, cachedModified, cachedCount)
        if (check.isUnchanged) {
            Log.d(TAG, "Directory '$path' is UNCHANGED. Cache is 100% valid.")
            val unchangedJson = "{" +
                "\"type\":\"$TYPE_STORAGE_DIR_UNCHANGED\"," +
                "\"path\":\"${StorageItem.escapeJson(path)}\"" +
                "}"
            dataSender(unchangedJson)
        } else {
            Log.d(TAG, "Directory '$path' has CHANGED. Dispatching delta notification and fresh page 0.")
            val changedJson = "{" +
                "\"type\":\"$TYPE_STORAGE_DIR_CHANGED\"," +
                "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                "\"currentModified\":${check.currentModified}," +
                "\"totalCount\":${check.currentCount}" +
                "}"
            dataSender(changedJson)

            // Immediately send fresh first page
            executeList(path = path, offset = 0, limit = 40, requestId = null)
        }
    }

    private fun executeList(path: String, offset: Int, limit: Int, requestId: String?) {
        Log.d(TAG, "Executing paginated list for path: '$path' (offset=$offset, limit=$limit, requestId=$requestId)")

        storageController.listDirectory(
            path = path,
            offset = offset,
            limit = limit,
            onBatch = { batch ->
                val finalBatch = if (requestId != null && batch.requestId == null) {
                    batch.copy(requestId = requestId)
                } else {
                    batch
                }
                dataSender(finalBatch.toJsonString())
            },
            onError = { reason ->
                Log.w(TAG, "List failed for '$path': $reason")
                val json = "{" +
                    "\"type\":\"$TYPE_STORAGE_LIST_ERROR\"," +
                    "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                    "\"reason\":\"${StorageItem.escapeJson(reason)}\"," +
                    "\"requestId\":${if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"}" +
                    "}"
                dataSender(json)
            }
        )
    }

    private fun executeGetFile(
        path: String,
        transferId: String,
        purpose: String,
        requestId: String?
    ) {
        Log.d(TAG, "Executing high-speed 64KB file streaming: $path (id=$transferId, purpose=$purpose)")

        storageController.readFileStream(
            path = path,
            transferId = transferId,
            purpose = purpose,
            onStart = { startHeader ->
                val finalHeader = if (requestId != null && startHeader.requestId == null) {
                    startHeader.copy(requestId = requestId)
                } else {
                    startHeader
                }
                dataSender(finalHeader.toJsonString())
            },
            onChunk = { chunk ->
                dataSender(chunk.toJsonString())
            },
            onComplete = {
                val completeJson = "{" +
                    "\"type\":\"$TYPE_STORAGE_FILE_COMPLETE\"," +
                    "\"transferId\":\"${StorageItem.escapeJson(transferId)}\"," +
                    "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                    "\"requestId\":${if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"}" +
                    "}"
                dataSender(completeJson)
                Log.d(TAG, "File streaming complete for: $path (id=$transferId)")
            },
            onError = { reason ->
                Log.e(TAG, "Streaming failed for $path: $reason")
                val errJson = "{" +
                    "\"type\":\"$TYPE_STORAGE_FILE_ERROR\"," +
                    "\"transferId\":\"${StorageItem.escapeJson(transferId)}\"," +
                    "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                    "\"reason\":\"${StorageItem.escapeJson(reason)}\"," +
                    "\"requestId\":${if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"}" +
                    "}"
                dataSender(errJson)
            }
        )
    }

    private fun executeCancel(transferId: String) {
        Log.d(TAG, "Executing cancel for transferId: $transferId")
        storageController.cancelTransfer(transferId)
    }

    private fun executeDelete(path: String, requestId: String?) {
        Log.d(TAG, "Executing file deletion for: $path (requestId=$requestId)")
        val result = storageController.deleteFile(path)

        if (result.isSuccess) {
            val json = "{" +
                "\"type\":\"$TYPE_STORAGE_DELETE_SUCCESS\"," +
                "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                "\"requestId\":${if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"}" +
                "}"
            dataSender(json)
            Log.d(TAG, "File deletion confirmed: $path")
        } else {
            val err = result.exceptionOrNull()?.message ?: "Failed to delete file"
            Log.w(TAG, "File deletion failed for $path: $err")
            val json = "{" +
                "\"type\":\"$TYPE_STORAGE_DELETE_ERROR\"," +
                "\"path\":\"${StorageItem.escapeJson(path)}\"," +
                "\"reason\":\"${StorageItem.escapeJson(err)}\"," +
                "\"requestId\":${if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"}" +
                "}"
            dataSender(json)
        }
    }

    private fun sendError(type: String, reason: String, path: String?, requestId: String?) {
        val pathJson = if (path != null) "\"${StorageItem.escapeJson(path)}\"" else "null"
        val reqJson = if (requestId != null) "\"${StorageItem.escapeJson(requestId)}\"" else "null"
        val json = "{" +
            "\"type\":\"$type\"," +
            "\"path\":$pathJson," +
            "\"reason\":\"${StorageItem.escapeJson(reason)}\"," +
            "\"requestId\":$reqJson" +
            "}"
        dataSender(json)
    }

    /**
     * Extracts a top-level string value from a simple JSON payload without relying on org.json.JSONObject.
     */
    fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts a top-level numeric/long value from a simple JSON payload.
     */
    fun extractJsonLongField(json: String, fieldName: String): Long? {
        val pattern = "\"$fieldName\"\\s*:\\s*([0-9]+)".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Fail-safe disconnection callback invoked when WebRTC connection or DataChannel drops.
     * Immediately aborts any in-flight 64KB file streaming transfers and background thumbnail scans.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Cancelling in-flight file transfers and directory scans...")
        cleanup()
    }

    fun cleanup() {
        storageController.cleanup()
    }
}
