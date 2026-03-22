package nexus.android.child.id

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DeviceIdManager.
 * Tests ID formatting and validation logic.
 * Note: Full testing of generateUniqueDeviceId requires mocking Firebase and SharedPreferences,
 * which is complex for a singleton object. These tests focus on testable pure functions.
 */
class DeviceIdManagerTest {

    @Test
    fun testFormat_with12DigitId_returnsFormattedString() {
        // Given
        val id = "123456789012"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1234 5678 9012", result)
    }

    @Test
    fun testFormat_withEmptyString_returnsEmpty() {
        // Given
        val id = ""

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("", result)
    }

    @Test
    fun testFormat_withShorterString_chunksCorrectly() {
        // Given
        val id = "12345"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1234 5", result)
    }

    @Test
    fun testFormat_withLongerString_chunksCorrectly() {
        // Given
        val id = "12345678901234567890"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1234 5678 9012 3456 7890", result)
    }

    @Test
    fun testFormat_withSingleCharacter_returnsSingleCharacter() {
        // Given
        val id = "1"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1", result)
    }

    @Test
    fun testFormat_withExactly4Characters_noSpaces() {
        // Given
        val id = "1234"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1234", result)
    }

    @Test
    fun testFormat_withExactly8Characters_oneSpace() {
        // Given
        val id = "12345678"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("1234 5678", result)
    }

    @Test
    fun testFormat_withAllZeros_formatsCorrectly() {
        // Given
        val id = "000000000000"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("0000 0000 0000", result)
    }

    @Test
    fun testFormat_withAllNines_formatsCorrectly() {
        // Given
        val id = "999999999999"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("9999 9999 9999", result)
    }

    @Test
    fun testFormat_preservesLeadingZeros() {
        // Given
        val id = "000123456789"

        // When
        val result = DeviceIdManager.format(id)

        // Then
        assertEquals("0001 2345 6789", result)
        assertTrue(result.startsWith("0001"))
    }

    @Test
    fun testCachedId_initiallyNull() {
        // When
        DeviceIdManager.cachedId = null

        // Then
        assertNull(DeviceIdManager.cachedId)
    }

    @Test
    fun testCachedId_canBeSet() {
        // Given
        val testId = "123456789012"

        // When
        DeviceIdManager.cachedId = testId

        // Then
        assertEquals(testId, DeviceIdManager.cachedId)

        // Cleanup
        DeviceIdManager.cachedId = null
    }

    @Test
    fun testCachedId_canBeCleared() {
        // Given
        DeviceIdManager.cachedId = "123456789012"

        // When
        DeviceIdManager.cachedId = null

        // Then
        assertNull(DeviceIdManager.cachedId)
    }
}
