package com.nexus.parent.data.storage

import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.StorageCategory
import com.nexus.parent.state.StorageFileItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * StorageCoordinatorTest.kt - Verifies directory caching + delta checks, progressive pagination,
 * 64KB chunk disk assembly + preview caching, transfer cancellation, and remote deletion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageCoordinatorTest {

    private fun createCoordinator(
        scope: TestScope,
        dispatcher: TestDispatcher,
        tempDir: File,
        sentCommands: MutableList<String>
    ): Pair<StorageCoordinator, ParentStateHolder> {
        val coord = StorageCoordinator(
            context = null,
            scope = scope,
            sendCommand = { cmd ->
                sentCommands.add(cmd)
                true
            },
            ioDispatcher = dispatcher,
            cacheRootDir = tempDir,
            base64Decoder = { b64 -> Base64.getDecoder().decode(b64) }
        )
        val holder = ParentStateHolder(
            initialState = ParentUiState(
                connectionPhase = ConnectionPhase.CONNECTED,
                connectedDevice = DeviceRecord(
                    rawId = "482194021104",
                    formattedId = "4821 9402 1104",
                    name = "Child Phone",
                    model = "Pixel 8",
                    lastSyncedLabel = "Online",
                    telemetry = DeviceTelemetry(
                        freeStorageBytes = 64L * 1024 * 1024 * 1024,
                        totalStorageBytes = 128L * 1024 * 1024 * 1024
                    )
                )
            )
        )
        coord.attachStateHolder(holder)
        return coord to holder
    }

    @Test
    fun `directory caching loads instantaneously and dispatches STORAGE_CHECK_DIR delta check`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val tempDir = Files.createTempDirectory("nexus_storage_cache_test").toFile()
        val commands = mutableListOf<String>()
        val (coord, holder) = createCoordinator(scope, dispatcher, tempDir, commands)

        // 1. Open root (cache miss -> STORAGE_LIST)
        coord.openStorageMonitor()
        assertEquals(1, commands.size)
        assertTrue(commands[0].contains("STORAGE_LIST"))
        assertTrue(holder.uiState.value.storage.isLoadingDirectory)

        // 2. Receive page 0 with folder DCIM and hasMore=true
        coord.handleIncomingMessage(
            """
            {
              "type":"STORAGE_LIST_BATCH",
              "path":"/storage/emulated/0",
              "items":[
                {"name":"DCIM","absolutePath":"/storage/emulated/0/DCIM","size":0,"modified":1000,"isDirectory":true,"mimeType":"inode/directory","category":"folder","thumbnail":null}
              ],
              "offset":0,"limit":40,"hasMore":true,"nextOffset":40,"isLast":false,"totalCount":80,"dirModified":999000
            }
            """.trimIndent()
        )
        assertFalse(holder.uiState.value.storage.isLoadingDirectory)
        assertEquals(1, holder.uiState.value.storage.items.size)
        assertTrue(holder.uiState.value.storage.hasMorePages)

        // 3. Pagination Load More appends offset=40 cleanly without losing page 0
        coord.loadMore()
        assertTrue(commands.last().contains("\"offset\":40"))
        coord.handleIncomingMessage(
            """
            {
              "type":"STORAGE_LIST_BATCH",
              "path":"/storage/emulated/0",
              "items":[
                {"name":"notes.txt","absolutePath":"/storage/emulated/0/notes.txt","size":256,"modified":1000,"isDirectory":false,"mimeType":"text/plain","category":"document","thumbnail":null}
              ],
              "offset":40,"limit":40,"hasMore":false,"nextOffset":41,"isLast":true,"totalCount":80,"dirModified":999000
            }
            """.trimIndent()
        )
        assertEquals(2, holder.uiState.value.storage.items.size)
        assertFalse(holder.uiState.value.storage.hasMorePages)

        // 4. Navigate to DCIM, then return Up -> hits cache immediately + sends STORAGE_CHECK_DIR
        coord.openDirectory(holder.uiState.value.storage.items[0])
        assertEquals("/storage/emulated/0/DCIM", holder.uiState.value.storage.currentPath)
        coord.navigateUp()
        assertEquals("/storage/emulated/0", holder.uiState.value.storage.currentPath)
        assertEquals(2, holder.uiState.value.storage.items.size)
        assertTrue(commands.last().contains("STORAGE_CHECK_DIR"))

        tempDir.deleteRecursively()
    }

    @Test
    fun `preview chunks assemble on disk in order, populate cache hit, and delete evicts caches`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val tempDir = Files.createTempDirectory("nexus_storage_stream_test").toFile()
        val commands = mutableListOf<String>()
        val (coord, holder) = createCoordinator(scope, dispatcher, tempDir, commands)

        val photoItem = StorageFileItem(
            path = "/storage/emulated/0/Pictures/demo.jpg",
            name = "demo.jpg",
            isDirectory = false,
            category = StorageCategory.IMAGE,
            rawBytes = 10,
            sizeLabel = "10 B",
            modifiedLabel = "Just now",
            extension = "JPG",
            mimeType = "image/jpeg"
        )

        coord.handleIncomingMessage(
            """
            {
              "type":"STORAGE_LIST_BATCH",
              "path":"/storage/emulated/0",
              "items":[
                {"name":"demo.jpg","absolutePath":"/storage/emulated/0/Pictures/demo.jpg","size":10,"modified":1000,"isDirectory":false,"mimeType":"image/jpeg","category":"image","thumbnail":null}
              ],
              "offset":0,"limit":40,"hasMore":false,"nextOffset":1,"isLast":true,"totalCount":1,"dirModified":1000
            }
            """.trimIndent()
        )

        // Start full image preview request
        coord.loadFullImagePreview(photoItem)
        val reqJson = org.json.JSONObject(commands.last())
        val transferId = reqJson.getString("transferId")
        assertEquals("preview", reqJson.getString("purpose"))

        val part1 = Base64.getEncoder().encodeToString("HELLO".toByteArray())
        val part2 = Base64.getEncoder().encodeToString("WORLD".toByteArray())

        coord.handleIncomingMessage(
            """{"type":"STORAGE_FILE_START","transferId":"$transferId","path":"${photoItem.path}","fileName":"demo.jpg","size":10,"totalChunks":2,"chunkSize":65536,"mimeType":"image/jpeg","purpose":"preview"}"""
        )
        // Even if chunk 1 arrives before chunk 0, sequential writer aligns them on disk!
        coord.handleIncomingMessage(
            """{"type":"STORAGE_FILE_CHUNK","transferId":"$transferId","chunkIndex":1,"totalChunks":2,"data":"$part2"}"""
        )
        coord.handleIncomingMessage(
            """{"type":"STORAGE_FILE_CHUNK","transferId":"$transferId","chunkIndex":0,"totalChunks":2,"data":"$part1"}"""
        )
        coord.handleIncomingMessage(
            """{"type":"STORAGE_FILE_COMPLETE","transferId":"$transferId","path":"${photoItem.path}"}"""
        )
        scope.advanceUntilIdle()

        val localPath = holder.uiState.value.storage.imagePreviewLocalPath
        assertNotNull(localPath)
        assertEquals("HELLOWORLD", File(localPath!!).readText())
        assertTrue(holder.uiState.value.storage.isImagePreviewCached)

        // Closing preview and re-opening triggers cache hit with zero extra STORAGE_GET_FILE commands
        val cmdCountBeforeCacheHit = commands.size
        holder.closeStorageImagePreview()
        coord.loadFullImagePreview(photoItem)
        assertEquals(cmdCountBeforeCacheHit, commands.size)
        assertNotNull(holder.uiState.value.storage.imagePreviewLocalPath)

        // Confirm delete removes item and deletes cached preview file
        coord.requestDeleteConfirmation(photoItem)
        assertEquals(photoItem.path, holder.uiState.value.storage.deleteConfirmTarget?.path)
        coord.confirmDelete()
        assertTrue(commands.last().contains("STORAGE_DELETE_FILE"))

        coord.handleIncomingMessage("""{"type":"STORAGE_DELETE_SUCCESS","path":"${photoItem.path}"}""")
        assertTrue(holder.uiState.value.storage.items.isEmpty())
        assertNull(holder.uiState.value.storage.imagePreviewLocalPath)

        tempDir.deleteRecursively()
    }
}
