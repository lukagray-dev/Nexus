package nexus.android.child.commands

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for storage contract data classes.
 * Tests default values and data class behavior.
 */
class StorageContractsTest {

    @Test
    fun testFileListRequest_defaultValues() {
        // When
        val request = FileListRequest()

        // Then
        assertNull(request.directoryPath)
        assertFalse(request.includeHidden)
        assertEquals(FileListRequest.DEFAULT_BATCH_SIZE, request.batchSize)
        assertNull(request.pageToken)
        assertNull(request.requestId)
    }

    @Test
    fun testFileListRequest_withAllParameters() {
        // When
        val request = FileListRequest(
            directoryPath = "/sdcard/Documents",
            includeHidden = true,
            batchSize = 100,
            pageToken = "token123",
            requestId = "req456"
        )

        // Then
        assertEquals("/sdcard/Documents", request.directoryPath)
        assertTrue(request.includeHidden)
        assertEquals(100, request.batchSize)
        assertEquals("token123", request.pageToken)
        assertEquals("req456", request.requestId)
    }

    @Test
    fun testFileListRequest_defaultBatchSizeConstant() {
        // Then
        assertEquals(60, FileListRequest.DEFAULT_BATCH_SIZE)
    }

    @Test
    fun testFileListRequest_copy_modifiesOnlySpecifiedFields() {
        // Given
        val original = FileListRequest(directoryPath = "/sdcard", batchSize = 50)

        // When
        val modified = original.copy(includeHidden = true)

        // Then
        assertEquals("/sdcard", modified.directoryPath)
        assertTrue(modified.includeHidden)
        assertEquals(50, modified.batchSize)
    }

    @Test
    fun testFileStreamRequest_defaultValues() {
        // When
        val request = FileStreamRequest(absolutePath = "/sdcard/file.txt")

        // Then
        assertEquals("/sdcard/file.txt", request.absolutePath)
        assertEquals(0L, request.resumeOffset)
        assertEquals(FileStreamRequest.DEFAULT_CHUNK_BYTES, request.chunkSizeBytes)
        assertNull(request.requestId)
        assertNull(request.transferId)
    }

    @Test
    fun testFileStreamRequest_withAllParameters() {
        // When
        val request = FileStreamRequest(
            absolutePath = "/sdcard/video.mp4",
            resumeOffset = 1024L,
            chunkSizeBytes = 32768,
            requestId = "req789",
            transferId = "transfer123"
        )

        // Then
        assertEquals("/sdcard/video.mp4", request.absolutePath)
        assertEquals(1024L, request.resumeOffset)
        assertEquals(32768, request.chunkSizeBytes)
        assertEquals("req789", request.requestId)
        assertEquals("transfer123", request.transferId)
    }

    @Test
    fun testFileStreamRequest_defaultChunkSizeConstant() {
        // Then
        assertEquals(64 * 1024, FileStreamRequest.DEFAULT_CHUNK_BYTES)
        assertEquals(65536, FileStreamRequest.DEFAULT_CHUNK_BYTES)
    }

    @Test
    fun testFileStreamRequest_copy_modifiesOnlySpecifiedFields() {
        // Given
        val original = FileStreamRequest(
            absolutePath = "/sdcard/file.txt",
            resumeOffset = 500L
        )

        // When
        val modified = original.copy(chunkSizeBytes = 8192)

        // Then
        assertEquals("/sdcard/file.txt", modified.absolutePath)
        assertEquals(500L, modified.resumeOffset)
        assertEquals(8192, modified.chunkSizeBytes)
    }

    @Test
    fun testFileStreamRequest_withLargeResumeOffset() {
        // When
        val request = FileStreamRequest(
            absolutePath = "/sdcard/large.bin",
            resumeOffset = Long.MAX_VALUE
        )

        // Then
        assertEquals(Long.MAX_VALUE, request.resumeOffset)
    }

    @Test
    fun testFileListRequest_equality() {
        // Given
        val request1 = FileListRequest(directoryPath = "/sdcard", batchSize = 50)
        val request2 = FileListRequest(directoryPath = "/sdcard", batchSize = 50)
        val request3 = FileListRequest(directoryPath = "/sdcard", batchSize = 60)

        // Then
        assertEquals(request1, request2)
        assertNotEquals(request1, request3)
    }

    @Test
    fun testFileStreamRequest_equality() {
        // Given
        val request1 = FileStreamRequest(absolutePath = "/sdcard/file.txt", resumeOffset = 100L)
        val request2 = FileStreamRequest(absolutePath = "/sdcard/file.txt", resumeOffset = 100L)
        val request3 = FileStreamRequest(absolutePath = "/sdcard/file.txt", resumeOffset = 200L)

        // Then
        assertEquals(request1, request2)
        assertNotEquals(request1, request3)
    }
}
