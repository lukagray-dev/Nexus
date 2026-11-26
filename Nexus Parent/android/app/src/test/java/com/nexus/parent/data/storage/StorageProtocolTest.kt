package com.nexus.parent.data.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StorageProtocolTest.kt - Verifies JSON command builders and Desktop-matching formatters.
 */
class StorageProtocolTest {

    @Test
    fun `builds valid JSON commands matching Desktop and Child StorageCommandHandler`() {
        val rawCheck = StorageProtocol.buildCheckDirCommand(
            path = "/storage/emulated/0/DCIM/Camera",
            cachedModified = 1732000000L,
            cachedCount = 42
        )
        assertFalse(rawCheck.contains("\\/"))
        assertTrue(rawCheck.contains("\"/storage/emulated/0/DCIM/Camera\""))
        val checkJson = JSONObject(rawCheck)
        assertEquals(StorageProtocol.CMD_STORAGE_CHECK_DIR, checkJson.getString("type"))
        assertEquals("/storage/emulated/0/DCIM/Camera", checkJson.getString("path"))
        assertEquals(1732000000L, checkJson.getLong("cachedModified"))
        assertEquals(42, checkJson.getInt("cachedCount"))

        val rawList = StorageProtocol.buildListCommand("/storage/emulated/0", offset = 40, limit = 40)
        assertFalse(rawList.contains("\\/"))
        assertTrue(rawList.contains("\"/storage/emulated/0\""))
        val listJson = JSONObject(rawList)
        assertEquals(StorageProtocol.CMD_STORAGE_LIST, listJson.getString("type"))
        assertEquals(40, listJson.getInt("offset"))
        assertEquals(40, listJson.getInt("limit"))

        val rawGet = StorageProtocol.buildGetFileCommand(
            path = "/storage/emulated/0/Pictures/test.jpg",
            transferId = "tr-1",
            purpose = StorageProtocol.PURPOSE_PREVIEW
        )
        assertFalse(rawGet.contains("\\/"))
        val getJson = JSONObject(rawGet)
        assertEquals(StorageProtocol.CMD_STORAGE_GET_FILE, getJson.getString("type"))
        assertEquals("preview", getJson.getString("purpose"))

        val cancelJson = JSONObject(StorageProtocol.buildCancelTransferCommand("tr-1"))
        assertEquals(StorageProtocol.CMD_STORAGE_CANCEL_TRANSFER, cancelJson.getString("type"))
        assertEquals("tr-1", cancelJson.getString("transferId"))

        val rawDelete = StorageProtocol.buildDeleteFileCommand("/storage/emulated/0/tmp.txt")
        assertFalse(rawDelete.contains("\\/"))
        val delJson = JSONObject(rawDelete)
        assertEquals(StorageProtocol.CMD_STORAGE_DELETE_FILE, delJson.getString("type"))
        assertEquals("/storage/emulated/0/tmp.txt", delJson.getString("path"))
    }

    @Test
    fun `formats bytes, relative timestamps, capacity strip, and breadcrumbs matching Desktop`() {
        assertEquals("0 B", StorageProtocol.formatBytes(0L))
        assertEquals("512 B", StorageProtocol.formatBytes(512L))
        assertEquals("4.2 MB", StorageProtocol.formatBytes((4.2 * 1024 * 1024).toLong()))

        val now = 1_750_000_000_000L
        assertEquals("Just now", StorageProtocol.formatRelativeTime(now - 10_000L, now))
        assertEquals("5 mins ago", StorageProtocol.formatRelativeTime(now - 300_000L, now))
        assertEquals("3 hours ago", StorageProtocol.formatRelativeTime(now - 10_800_000L, now))
        assertEquals("Yesterday", StorageProtocol.formatRelativeTime(now - 100_000_000L, now))

        val (usageText, fraction) = StorageProtocol.formatStorageCapacityStrip(
            freeBytes = 64L * 1024 * 1024 * 1024,
            totalBytes = 256L * 1024 * 1024 * 1024
        )
        assertEquals("192.0 GB / 256.0 GB USED (75%)", usageText)
        assertEquals(0.75f, fraction, 0.01f)

        val crumbs = StorageProtocol.buildBreadcrumbs("/storage/emulated/0/DCIM/Camera")
        assertEquals(3, crumbs.size)
        assertEquals("0 (Internal)", crumbs[0].label)
        assertEquals("/storage/emulated/0", crumbs[0].fullPath)
        assertEquals("DCIM", crumbs[1].label)
        assertEquals("/storage/emulated/0/DCIM", crumbs[1].fullPath)
        assertEquals("Camera", crumbs[2].label)
        assertEquals("/storage/emulated/0/DCIM/Camera", crumbs[2].fullPath)

        assertEquals("/storage/emulated/0/DCIM", StorageProtocol.computeParentPath("/storage/emulated/0/DCIM/Camera"))
        assertEquals("/storage/emulated/0", StorageProtocol.computeParentPath("/storage/emulated/0/DCIM"))
        assertNull(StorageProtocol.computeParentPath("/storage/emulated/0"))
        assertTrue(StorageProtocol.formatStorageCapacityStrip(0L, 0L).first.contains("UNAVAILABLE"))
    }
}
