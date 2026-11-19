package com.nexus.child.components.keyboard

/**
 * Data model representing an audited typing event or search query on the child device.
 *
 * Hey friend! Here is why this data model is structured this way:
 * 1. app: Stores the Android package name (e.g., "com.android.chrome") for technical tracking.
 * 2. appName: The human-readable name (e.g., "Chrome") so parents immediately recognize the app.
 * 3. text: The consolidated text entered by the child. For password fields, this is strictly masked.
 * 4. fieldHint: Helpful context like "Search Google" or "Type a message", showing what the input was for.
 * 5. inputType: Categorized input category (TEXT, SEARCH, PASSWORD, EMAIL, URL, NUMBER).
 * 6. isPassword: Flag indicating whether this was an authenticated password/PIN field.
 * 7. timestamp: Milliseconds timestamp when the typing event concluded.
 */
data class KeyboardRecord(
    val id: String,
    val app: String,
    val appName: String,
    val text: String,
    val fieldHint: String? = null,
    val fieldId: String? = null,
    val inputType: String = "TEXT",
    val isPassword: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {

    /**
     * Serializes this record into a compact, standardized JSON string for WebRTC DataChannel delivery.
     */
    fun toJsonString(): String {
        val hintPart = if (fieldHint != null) "\"fieldHint\":\"${escapeJson(fieldHint)}\"," else ""
        val fieldIdPart = if (fieldId != null) "\"fieldId\":\"${escapeJson(fieldId)}\"," else ""

        return "{" +
                "\"id\":\"${escapeJson(id)}\"," +
                "\"app\":\"${escapeJson(app)}\"," +
                "\"appName\":\"${escapeJson(appName)}\"," +
                "\"text\":\"${escapeJson(text)}\"," +
                hintPart +
                fieldIdPart +
                "\"inputType\":\"${escapeJson(inputType)}\"," +
                "\"isPassword\":$isPassword," +
                "\"timestamp\":$timestamp" +
                "}"
    }

    companion object {
        /**
         * Safely escapes special JSON control characters, quotes, and backslashes.
         */
        fun escapeJson(value: String): String {
            val sb = StringBuilder(value.length + 16)
            for (char in value) {
                when (char) {
                    '\"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (char.code in 0x00..0x1F) {
                            val hex = Integer.toHexString(char.code).padStart(4, '0')
                            sb.append("\\u").append(hex)
                        } else {
                            sb.append(char)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }
}
