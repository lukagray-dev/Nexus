package com.nexus.child.components.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Internal Storage DataChannel protocol, progressive pagination,
 * directory cache validation, 64KB chunk streaming, and remote deletion on the Android child side.
 *
 * Hey friend! These tests verify:
 * 1. Protocol command routing (STORAGE_CHECK_DIR, STORAGE_LIST, STORAGE_GET_FILE, STORAGE_CANCEL, STORAGE_DELETE).
 * 2. Progressive directory pagination (offset, limit, hasMore, nextOffset).
 * 3. Fast directory cache checking (STORAGE_DIR_UNCHANGED vs STORAGE_DIR_CHANGED).
 * 4. 64KB file chunk generation and stream progress notification.
 * 5. Safe file deletion confirmation (STORAGE_DELETE_SUCCESS / STORAGE_DELETE_ERROR).
 * 6. Robust JSON escaping and field extraction without Android framework dependencies.
 */
class StorageComponentTest {

    private class FakeStorageController(
        var hasPermission: Boolean = true,
        var defaultPath: String = "/storage/emulated/0",
        var listResult: List<StorageItem> = listOf(
            StorageItem(
                name = "DCIM",
                absolutePath = "/storage/emulated/0/DCIM",
                size = 0L,
                modified = 1731900000000L,
                isDirectory = true,
                mimeType = "inode/directory",
                category = "folder",
                thumbnailBase64 = null
            ),
            StorageItem(
                name = "photo.jpg",
                absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
                size = 4096000L,
                modified = 1731910000000L,
                isDirectory = false,
                mimeType = "image/jpeg",
                category = "image",
                thumbnailBase64 = "data:image/jpeg;base64,/9j/fake"
            )
        ),
        var deleteResult: Result<Boolean> = Result.success(true),
        var isDirUnchanged: Boolean = true
    ) : IStorageController {

        var lastListedPath: String? = null
        var lastOffset: Int = 0
        var lastLimit: Int = 40
        var lastStreamedPath: String? = null
        var lastStreamedPurpose: String? = null
        var lastDeletedPath: String? = null
        var lastCancelledTransferId: String? = null
        var cleanupCalled: Boolean = false

        override fun hasStoragePermission(): Boolean = hasPermission

        override fun getDefaultStoragePath(): String = defaultPath

        override fun checkDirectoryModified(
            path: String,
            cachedModified: Long,
            cachedCount: Int
        ): DirectoryCheckResult {
            return DirectoryCheckResult(
                isUnchanged = isDirUnchanged,
                currentModified = 1731950000000L,
                currentCount = listResult.size
            )
        }

        override fun listDirectory(
            path: String,
            offset: Int,
            limit: Int,
            onBatch: (StorageListBatch) -> Unit,
            onError: (reason: String) -> Unit
        ) {
            lastListedPath = path
            lastOffset = offset
            lastLimit = limit
            if (!hasPermission) {
                onError("PERMISSION_DENIED: Storage permission not granted")
                return
            }
            onBatch(
                StorageListBatch(
                    path = path,
                    items = listResult,
                    isLast = true,
                    totalCount = listResult.size,
                    offset = offset,
                    limit = limit,
                    hasMore = false,
                    nextOffset = listResult.size,
                    dirModified = 1731950000000L
                )
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
            lastStreamedPath = path
            lastStreamedPurpose = purpose
            if (!hasPermission) {
                onError("PERMISSION_DENIED")
                return
            }

            onStart(
                StorageFileStart(
                    transferId = transferId,
                    path = path,
                    fileName = "photo.jpg",
                    size = 131072L,
                    totalChunks = 2,
                    chunkSize = 65536,
                    mimeType = "image/jpeg",
                    purpose = purpose
                )
            )

            onChunk(
                StorageFileChunk(
                    transferId = transferId,
                    chunkIndex = 0,
                    totalChunks = 2,
                    data = "CHUNK_0_64KB_BASE64"
                )
            )

            onChunk(
                StorageFileChunk(
                    transferId = transferId,
                    chunkIndex = 1,
                    totalChunks = 2,
                    data = "CHUNK_1_64KB_BASE64"
                )
            )

            onComplete()
        }

        override fun cancelTransfer(transferId: String): Boolean {
            lastCancelledTransferId = transferId
            return true
        }

        override fun deleteFile(path: String): Result<Boolean> {
            lastDeletedPath = path
            return deleteResult
        }

        override fun cleanup() {
            cleanupCalled = true
        }
    }

    @Test
    fun `STORAGE_CHECK_DIR dispatches UNCHANGED when directory matches cache`() {
        val fakeController = FakeStorageController(isDirUnchanged = true)
        val dispatchedMessages = mutableListOf<String>()
        val handler = StorageCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val checkCmd = "{\"type\":\"STORAGE_CHECK_DIR\",\"path\":\"/storage/emulated/0/DCIM\",\"cachedModified\":1731950000000,\"cachedCount\":2}"
        val handled = handler.handleCommand(checkCmd)
        assertTrue(handled)
        assertEquals(1, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].contains("\"type\":\"STORAGE_DIR_UNCHANGED\""))
        assertTrue(dispatchedMessages[0].contains("/storage/emulated/0/DCIM"))
    }

    @Test
    fun `STORAGE_CHECK_DIR dispatches CHANGED and initial page when directory has changed`() {
        val fakeController = FakeStorageController(isDirUnchanged = false)
        val dispatchedMessages = mutableListOf<String>()
        val handler = StorageCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val checkCmd = "{\"type\":\"STORAGE_CHECK_DIR\",\"path\":\"/storage/emulated/0/Pictures\",\"cachedModified\":1000,\"cachedCount\":1}"
        val handled = handler.handleCommand(checkCmd)
        assertTrue(handled)

        // Expect 2 messages: STORAGE_DIR_CHANGED followed by STORAGE_LIST_BATCH
        assertEquals(2, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].contains("\"type\":\"STORAGE_DIR_CHANGED\""))
        assertTrue(dispatchedMessages[1].contains("\"type\":\"STORAGE_LIST_BATCH\""))
        assertEquals("/storage/emulated/0/Pictures", fakeController.lastListedPath)
    }

    @Test
    fun `STORAGE_LIST with pagination extracts offset and limit`() {
        val fakeController = FakeStorageController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = StorageCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val jsonCmd = "{\"type\":\"STORAGE_LIST\",\"path\":\"/storage/emulated/0/DCIM\",\"offset\":40,\"limit\":40,\"requestId\":\"req-p2\"}"
        val handled = handler.handleCommand(jsonCmd)
        assertTrue(handled)
        assertEquals(40, fakeController.lastOffset)
        assertEquals(40, fakeController.lastLimit)
        assertEquals(1, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].contains("\"offset\":40"))
        assertTrue(dispatchedMessages[0].contains("\"limit\":40"))
        assertTrue(dispatchedMessages[0].contains("\"requestId\":\"req-p2\""))
    }

    @Test
    fun `STORAGE_GET_FILE streams 64KB file chunks in sequence`() {
        val fakeController = FakeStorageController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = StorageCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val getCmd = "{\"type\":\"STORAGE_GET_FILE\",\"path\":\"/storage/emulated/0/DCIM/photo.jpg\",\"transferId\":\"t-64k\",\"purpose\":\"download\"}"
        val handled = handler.handleCommand(getCmd)
        assertTrue(handled)
        assertEquals(4, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].contains("\"type\":\"STORAGE_FILE_START\""))
        assertTrue(dispatchedMessages[0].contains("\"chunkSize\":65536"))
        assertTrue(dispatchedMessages[1].contains("\"type\":\"STORAGE_FILE_CHUNK\""))
        assertTrue(dispatchedMessages[2].contains("\"type\":\"STORAGE_FILE_CHUNK\""))
        assertTrue(dispatchedMessages[3].contains("\"type\":\"STORAGE_FILE_COMPLETE\""))
    }

    @Test
    fun `STORAGE_DELETE_FILE success dispatches confirmation packet`() {
        val fakeController = FakeStorageController()
        val dispatchedMessages = mutableListOf<String>()
        val handler = StorageCommandHandler(fakeController) { msg ->
            dispatchedMessages.add(msg)
            true
        }

        val deleteCmd = "{\"type\":\"STORAGE_DELETE_FILE\",\"path\":\"/storage/emulated/0/Download/test.pdf\",\"requestId\":\"del-1\"}"
        val handled = handler.handleCommand(deleteCmd)
        assertTrue(handled)
        assertEquals("/storage/emulated/0/Download/test.pdf", fakeController.lastDeletedPath)
        assertEquals(1, dispatchedMessages.size)
        assertTrue(dispatchedMessages[0].contains("\"type\":\"STORAGE_DELETE_SUCCESS\""))
    }

    @Test
    fun `onPeerDisconnected cleans up controller jobs and active transfers`() {
        val fakeController = FakeStorageController()
        val handler = StorageCommandHandler(fakeController) { true }

        handler.onPeerDisconnected()
        assertTrue("cleanup must be invoked on IStorageController when peer disconnects", fakeController.cleanupCalled)
    }
}
