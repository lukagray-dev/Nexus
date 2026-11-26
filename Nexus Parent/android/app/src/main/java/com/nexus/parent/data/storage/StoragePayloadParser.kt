package com.nexus.parent.data.storage

import com.nexus.parent.state.StorageCategory
import com.nexus.parent.state.StorageFileItem
import org.json.JSONObject
import java.util.Locale

/**
 * StorageIncomingPacket - Strongly typed representation of Child's storage JSON messages.
 */
sealed class StorageIncomingPacket {
    data class DirUnchanged(val path: String) : StorageIncomingPacket()

    data class DirChanged(
        val path: String,
        val currentModified: Long,
        val totalCount: Int
    ) : StorageIncomingPacket()

    data class ListBatch(
        val path: String,
        val items: List<StorageFileItem>,
        val isLast: Boolean,
        val totalCount: Int,
        val offset: Int,
        val limit: Int,
        val hasMore: Boolean,
        val nextOffset: Int,
        val dirModified: Long
    ) : StorageIncomingPacket()

    data class ListError(val path: String, val reason: String) : StorageIncomingPacket()

    data class FileStart(
        val transferId: String,
        val path: String,
        val fileName: String,
        val size: Long,
        val totalChunks: Int,
        val chunkSize: Int,
        val mimeType: String,
        val purpose: String
    ) : StorageIncomingPacket()

    data class FileChunk(
        val transferId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val dataBase64: String
    ) : StorageIncomingPacket()

    data class FileComplete(val transferId: String, val path: String) : StorageIncomingPacket()

    data class FileError(
        val transferId: String,
        val path: String,
        val reason: String
    ) : StorageIncomingPacket()

    data class DeleteSuccess(val path: String) : StorageIncomingPacket()

    data class DeleteError(val path: String, val reason: String) : StorageIncomingPacket()
}

/**
 * StoragePayloadParser.kt - Parses incoming WebRTC DataChannel JSON packets for Remote Storage.
 *
 * Hey friend! Performs a fast prefix/marker check before instantiating `JSONObject`, so non-storage
 * packets (Opus audio frames, telemetry, monitor feeds) pass right through with zero allocations.
 */
object StoragePayloadParser {

    fun parse(rawMessage: String, nowMillis: Long = System.currentTimeMillis()): StorageIncomingPacket? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"STORAGE_")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            when (json.optString("type")) {
                StorageProtocol.TYPE_STORAGE_DIR_UNCHANGED -> {
                    StorageIncomingPacket.DirUnchanged(path = json.optString("path"))
                }

                StorageProtocol.TYPE_STORAGE_DIR_CHANGED -> {
                    StorageIncomingPacket.DirChanged(
                        path = json.optString("path"),
                        currentModified = json.optLong("currentModified", 0L),
                        totalCount = json.optInt("totalCount", 0)
                    )
                }

                StorageProtocol.TYPE_STORAGE_LIST_BATCH -> parseListBatch(json, nowMillis)

                StorageProtocol.TYPE_STORAGE_LIST_ERROR -> {
                    StorageIncomingPacket.ListError(
                        path = json.optString("path"),
                        reason = json.optString("reason", "Failed to list directory")
                    )
                }

                StorageProtocol.TYPE_STORAGE_FILE_START -> {
                    StorageIncomingPacket.FileStart(
                        transferId = json.optString("transferId"),
                        path = json.optString("path"),
                        fileName = json.optString("fileName"),
                        size = json.optLong("size", 0L),
                        totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
                        chunkSize = json.optInt("chunkSize", 65536),
                        mimeType = json.optString("mimeType", "application/octet-stream")
                            .ifBlank { "application/octet-stream" },
                        purpose = json.optString("purpose", StorageProtocol.PURPOSE_DOWNLOAD)
                            .ifBlank { StorageProtocol.PURPOSE_DOWNLOAD }
                    )
                }

                StorageProtocol.TYPE_STORAGE_FILE_CHUNK -> {
                    StorageIncomingPacket.FileChunk(
                        transferId = json.optString("transferId"),
                        chunkIndex = json.optInt("chunkIndex", 0),
                        totalChunks = json.optInt("totalChunks", 1).coerceAtLeast(1),
                        dataBase64 = json.optString("data", "")
                    )
                }

                StorageProtocol.TYPE_STORAGE_FILE_COMPLETE -> {
                    StorageIncomingPacket.FileComplete(
                        transferId = json.optString("transferId"),
                        path = json.optString("path")
                    )
                }

                StorageProtocol.TYPE_STORAGE_FILE_ERROR -> {
                    StorageIncomingPacket.FileError(
                        transferId = json.optString("transferId"),
                        path = json.optString("path"),
                        reason = json.optString("reason", "Transfer failed")
                    )
                }

                StorageProtocol.TYPE_STORAGE_DELETE_SUCCESS -> {
                    StorageIncomingPacket.DeleteSuccess(path = json.optString("path"))
                }

                StorageProtocol.TYPE_STORAGE_DELETE_ERROR -> {
                    StorageIncomingPacket.DeleteError(
                        path = json.optString("path"),
                        reason = json.optString("reason", "Could not delete file")
                    )
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun parseListBatch(json: JSONObject, nowMillis: Long): StorageIncomingPacket.ListBatch {
        val rawItems = json.optJSONArray("items")
        val parsedItems = buildList {
            if (rawItems != null) {
                for (i in 0 until rawItems.length()) {
                    val obj = rawItems.optJSONObject(i) ?: continue
                    val name = obj.optString("name").ifBlank { continue }
                    val absPath = obj.optString("absolutePath").ifBlank { continue }
                    val isDir = obj.optBoolean("isDirectory", false)
                    val rawBytes = obj.optLong("size", 0L)
                    val modifiedMs = obj.optLong("modified", 0L)
                    val mimeType = obj.optString("mimeType", "application/octet-stream")
                        .ifBlank { "application/octet-stream" }
                    val rawCategory = obj.optString("category", "other")
                    val thumbnail = if (obj.isNull("thumbnail")) null else obj.optString("thumbnail").ifBlank { null }
                    val extension = if (isDir) {
                        "DIR"
                    } else {
                        name.substringAfterLast('.', "").uppercase(Locale.ROOT)
                    }

                    add(
                        StorageFileItem(
                            path = absPath,
                            name = name,
                            isDirectory = isDir,
                            category = mapCategory(rawCategory, isDir),
                            rawBytes = rawBytes,
                            sizeLabel = if (isDir) "--" else StorageProtocol.formatBytes(rawBytes),
                            modifiedMillis = modifiedMs,
                            modifiedLabel = StorageProtocol.formatRelativeTime(modifiedMs, nowMillis),
                            extension = extension,
                            mimeType = mimeType,
                            thumbnailBase64 = thumbnail
                        )
                    )
                }
            }
        }

        val offset = json.optInt("offset", 0)
        val totalCount = json.optInt("totalCount", parsedItems.size)
        val limit = json.optInt("limit", StorageProtocol.DEFAULT_PAGE_LIMIT)
        val hasMore = json.optBoolean("hasMore", false)
        val nextOffset = json.optInt("nextOffset", offset + parsedItems.size)
        val dirModified = json.optLong("dirModified", 0L)

        return StorageIncomingPacket.ListBatch(
            path = json.optString("path"),
            items = parsedItems,
            isLast = json.optBoolean("isLast", !hasMore),
            totalCount = totalCount,
            offset = offset,
            limit = limit,
            hasMore = hasMore,
            nextOffset = nextOffset,
            dirModified = dirModified
        )
    }

    private fun mapCategory(rawCategory: String, isDirectory: Boolean): StorageCategory {
        if (isDirectory) return StorageCategory.ALL
        return when (rawCategory.trim().lowercase(Locale.ROOT)) {
            "image" -> StorageCategory.IMAGE
            "video" -> StorageCategory.VIDEO
            "audio" -> StorageCategory.AUDIO
            "document" -> StorageCategory.DOCUMENT
            "archive" -> StorageCategory.ARCHIVE
            else -> StorageCategory.OTHER
        }
    }
}
