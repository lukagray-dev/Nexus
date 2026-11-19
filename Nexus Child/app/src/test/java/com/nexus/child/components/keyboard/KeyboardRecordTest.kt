package com.nexus.child.components.keyboard

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for KeyboardRecord serialization, escaping, and formatting.
 *
 * Hey friend! These tests ensure that typed strings, search queries, and masked
 * password inputs serialize into valid JSON without breaking WebRTC DataChannel delivery.
 */
class KeyboardRecordTest {

    @Test
    fun testJsonSerializationStandardText() {
        val record = KeyboardRecord(
            id = "test_101",
            app = "com.android.chrome",
            appName = "Chrome",
            text = "science fair project solar panel efficiency",
            fieldHint = "Search or type URL",
            fieldId = "com.android.chrome:id/url_bar",
            inputType = "SEARCH",
            isPassword = false,
            timestamp = 1732015000000L
        )

        val json = record.toJsonString()

        assertTrue(json.contains("\"id\":\"test_101\""))
        assertTrue(json.contains("\"app\":\"com.android.chrome\""))
        assertTrue(json.contains("\"appName\":\"Chrome\""))
        assertTrue(json.contains("\"text\":\"science fair project solar panel efficiency\""))
        assertTrue(json.contains("\"fieldHint\":\"Search or type URL\""))
        assertTrue(json.contains("\"fieldId\":\"com.android.chrome:id/url_bar\""))
        assertTrue(json.contains("\"inputType\":\"SEARCH\""))
        assertTrue(json.contains("\"isPassword\":false"))
        assertTrue(json.contains("\"timestamp\":1732015000000"))
    }

    @Test
    fun testJsonSerializationMaskedPassword() {
        val record = KeyboardRecord(
            id = "test_102",
            app = "com.example.bank",
            appName = "My Bank",
            text = "••••••••",
            fieldHint = "Enter Password",
            fieldId = "com.example.bank:id/password_input",
            inputType = "PASSWORD",
            isPassword = true,
            timestamp = 1732015100000L
        )

        val json = record.toJsonString()

        assertTrue(json.contains("\"text\":\"••••••••\""))
        assertTrue(json.contains("\"isPassword\":true"))
        assertTrue(json.contains("\"inputType\":\"PASSWORD\""))
    }

    @Test
    fun testJsonEscapingSpecialCharacters() {
        val input = "Hello \"World\"\nLine 2\tTabbed \\ Backslash"
        val escaped = KeyboardRecord.escapeJson(input)

        assertEquals("Hello \\\"World\\\"\\nLine 2\\tTabbed \\\\ Backslash", escaped)
    }

    @Test
    fun testJsonSerializationWithNullOptionalFields() {
        val record = KeyboardRecord(
            id = "test_103",
            app = "com.whatsapp",
            appName = "WhatsApp",
            text = "See you tomorrow",
            fieldHint = null,
            fieldId = null,
            inputType = "TEXT",
            isPassword = false,
            timestamp = 1732015200000L
        )

        val json = record.toJsonString()

        assertFalse(json.contains("\"fieldHint\""))
        assertFalse(json.contains("\"fieldId\""))
        assertTrue(json.contains("\"text\":\"See you tomorrow\""))
    }
}
