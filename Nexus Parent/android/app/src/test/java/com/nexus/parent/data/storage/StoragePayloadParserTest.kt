package com.nexus.parent.data.storage

import com.nexus.parent.state.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StoragePayloadParserTest.kt - Verifies JSON packet parsing for all 10 Child storage responses.
 */
class StoragePayloadParserTest {

    @Test
    fun `parses STORAGE_LIST_BATCH with folders, thumbnails, categories, and pagination metadata`() {
        val raw = """
            {
              "type": "STORAGE_LIST_BATCH",
              "path": "/storage/emulated/0/DCIM",
              "items": [
                {
                  "name": "Camera",
                  "absolutePath": "/storage/emulated/0/DCIM/Camera",
                  "size": 0,
                  "modified": 1750000000000,
                  "isDirectory": true,
                  "mimeType": "inode/directory",
                  "category": "folder",
                  "thumbnail": null
                },
                {
                  "name": "vacation.jpg",
                  "absolutePath": "/storage/emulated/0/DCIM/vacation.jpg",
                  "size": 2097152,
                  "modified": 1750000000000,
                  "isDirectory": false,
                  "mimeType": "image/jpeg",
                  "category": "image",
                  "thumbnail": "data:image/jpeg;base64,/9j/4AAQ"
                },
                {
                  "name": "archive.bin",
                  "absolutePath": "/storage/emulated/0/DCIM/archive.bin",
                  "size": 1024,
                  "modified": 1750000000000,
                  "isDirectory": false,
                  "mimeType": "application/octet-stream",
                  "category": "other",
                  "thumbnail": null
                }
              ],
              "offset": 0,
              "limit": 40,
              "hasMore": true,
              "nextOffset": 40,
              "isLast": false,
              "totalCount": 115,
              "dirModified": 1750000000000
            }
        """.trimIndent()

        val packet = StoragePayloadParser.parse(raw, nowMillis = 1750000005000L)
        assertTrue(packet is StorageIncomingPacket.ListBatch)
        val batch = packet as StorageIncomingPacket.ListBatch

        assertEquals("/storage/emulated/0/DCIM", batch.path)
        assertEquals(3, batch.items.size)
        assertTrue(batch.hasMore)
        assertEquals(40, batch.nextOffset)
        assertEquals(115, batch.totalCount)

        val folder = batch.items[0]
        assertTrue(folder.isDirectory)
        assertEquals("--", folder.sizeLabel)
        assertEquals("DIR", folder.extension)

        val photo = batch.items[1]
        assertFalse(photo.isDirectory)
        assertEquals(StorageCategory.IMAGE, photo.category)
        assertEquals("2.0 MB", photo.sizeLabel)
        assertEquals("JPG", photo.extension)
        assertEquals("data:image/jpeg;base64,/9j/4AAQ", photo.thumbnailBase64)

        val other = batch.items[2]
        assertEquals(StorageCategory.OTHER, other.category)
    }

    @Test
    fun `parses cache checks, chunked transfer lifecycle, and delete outcomes cleanly`() {
        assertNull(StoragePayloadParser.parse("OPUS_AUDIO:AAAA"))
        assertNull(StoragePayloadParser.parse("""{"type":"DEVICE_TELEMETRY","batteryPercent":88}"""))

        val unchanged = StoragePayloadParser.parse("""{"type":"STORAGE_DIR_UNCHANGED","path":"/storage/emulated/0"}""")
        assertTrue(unchanged is StorageIncomingPacket.DirUnchanged)

        val changed = StoragePayloadParser.parse(
            """{"type":"STORAGE_DIR_CHANGED","path":"/storage/emulated/0","currentModified":999,"totalCount":12}"""
        ) as? StorageIncomingPacket.DirChanged
        assertNotNull(changed)
        assertEquals(12, changed?.totalCount)

        val start = StoragePayloadParser.parse(
            """{"type":"STORAGE_FILE_START","transferId":"t1","path":"/a/b.png","fileName":"b.png","size":123,"totalChunks":2,"chunkSize":65536,"mimeType":"image/png","purpose":"preview"}"""
        ) as? StorageIncomingPacket.FileStart
        assertEquals("preview", start?.purpose)
        assertEquals(2, start?.totalChunks)

        val chunk = StoragePayloadParser.parse(
            """{"type":"STORAGE_FILE_CHUNK","transferId":"t1","chunkIndex":1,"totalChunks":2,"data":"YWJj"}"""
        ) as? StorageIncomingPacket.FileChunk
        assertEquals("YWJj", chunk?.dataBase64)

        val complete = StoragePayloadParser.parse(
            """{"type":"STORAGE_FILE_COMPLETE","transferId":"t1","path":"/a/b.png"}"""
        ) as? StorageIncomingPacket.FileComplete
        assertEquals("t1", complete?.transferId)

        val delOk = StoragePayloadParser.parse("""{"type":"STORAGE_DELETE_SUCCESS","path":"/a/b.png"}""")
        assertTrue(delOk is StorageIncomingPacket.DeleteSuccess)
    }
}
