package com.nexus.child.components.applock

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pure JVM unit tests for AppLockRepository verifying offline persistence and selfie disk management.
 *
 * Hey friend! These tests verify:
 * 1. Persistent storage and retrieval of unlock attempt records.
 * 2. Saving and reading back Base64-encoded selfie images.
 * 3. Clearing stored records and cleaning up disk files.
 */
class AppLockRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: AppLockRepository

    @Before
    fun setUp() {
        tempDir = File("build/test-applock-${System.currentTimeMillis()}").apply { mkdirs() }
        repository = AppLockRepository(customBaseDir = tempDir)
        repository.clearRecords()
    }

    @After
    fun tearDown() {
        repository.clearRecords()
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndRetrieveAttemptRecords() {
        val mockSelfie = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val record1 = AppLockRecord(
            id = "test_attempt_1",
            timestamp = 1000L,
            pinCorrect = false,
            failedAttempts = 1,
            isLockedOut = false,
            selfieBase64 = mockSelfie
        )

        val record2 = AppLockRecord(
            id = "test_attempt_2",
            timestamp = 2000L,
            pinCorrect = true,
            failedAttempts = 0,
            isLockedOut = false,
            selfieBase64 = null
        )

        repository.saveRecord(record1)
        repository.saveRecord(record2)

        val records = repository.getAllRecords()
        assertEquals(2, records.size)

        // Newest record is prepended first
        assertEquals("test_attempt_2", records[0].id)
        assertTrue(records[0].pinCorrect)

        assertEquals("test_attempt_1", records[1].id)
        assertNotNull(records[1].selfieBase64)
    }

    @Test
    fun testClearRecordsRemovesAllData() {
        val record = AppLockRecord(
            id = "test_clear_1",
            pinCorrect = false,
            failedAttempts = 1,
            isLockedOut = false
        )
        repository.saveRecord(record)
        assertEquals(1, repository.getAllRecords().size)

        repository.clearRecords()
        assertEquals(0, repository.getAllRecords().size)
    }
}
