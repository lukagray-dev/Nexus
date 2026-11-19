package com.nexus.child.components.notification

/**
 * NotificationRecord represents an intercepted system or application push notification.
 *
 * Hey friend! Here is what every notification record encapsulates:
 * - Identity: System notification ID, unique key string across Android OS, and package name.
 * - Application Info: The human-readable app label resolved from PackageManager (e.g. "WhatsApp", "Instagram").
 * - Content & Text: Main title, body preview text, expanded big text (for emails or long chats), subtext, and summaries.
 * - Messaging Thread: For chat applications using MessagingStyle, contains individual message records within the thread.
 * - Interactive Controls: String titles of interactive notification action buttons (e.g. "Reply", "Mark as read").
 * - System Flags: Post timestamp, system category (msg, call, social, etc.), ongoing persistent state, and group bundling keys.
 *
 * Note: Uses direct JSON string formatting to prevent Android stub issues in host JVM unit tests
 * and eliminate runtime JSON wrapper object allocations.
 */
data class NotificationRecord(
    val id: Int,
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    val category: String = "unknown",
    val channelId: String? = null,
    val postTime: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val groupKey: String? = null,
    val actions: List<String> = emptyList(),
    val messages: List<NotificationMessageRecord> = emptyList()
) {
    /**
     * Serializes this notification record into a clean JSON string for DataChannel transmission.
     */
    fun toJsonString(): String {
        val actionsJson = actions.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
        val messagesJson = messages.joinToString(separator = ",", prefix = "[", postfix = "]") { it.toJsonString() }

        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"id\":").append(id).append(",")
        sb.append("\"key\":\"").append(escapeJson(key)).append("\",")
        sb.append("\"packageName\":\"").append(escapeJson(packageName)).append("\",")
        sb.append("\"appName\":\"").append(escapeJson(appName)).append("\",")
        sb.append("\"title\":\"").append(escapeJson(title)).append("\",")
        sb.append("\"text\":\"").append(escapeJson(text)).append("\",")
        if (bigText != null) sb.append("\"bigText\":\"").append(escapeJson(bigText)).append("\",")
        if (subText != null) sb.append("\"subText\":\"").append(escapeJson(subText)).append("\",")
        if (summaryText != null) sb.append("\"summaryText\":\"").append(escapeJson(summaryText)).append("\",")
        if (infoText != null) sb.append("\"infoText\":\"").append(escapeJson(infoText)).append("\",")
        sb.append("\"category\":\"").append(escapeJson(category)).append("\",")
        if (channelId != null) sb.append("\"channelId\":\"").append(escapeJson(channelId)).append("\",")
        sb.append("\"postTime\":").append(postTime).append(",")
        sb.append("\"isOngoing\":").append(isOngoing).append(",")
        sb.append("\"isClearable\":").append(isClearable).append(",")
        if (groupKey != null) sb.append("\"groupKey\":\"").append(escapeJson(groupKey)).append("\",")
        sb.append("\"actions\":").append(actionsJson).append(",")
        sb.append("\"messages\":").append(messagesJson)
        sb.append("}")
        return sb.toString()
    }

    companion object {
        /**
         * Escapes control characters and quotes for compliant JSON string literals.
         */
        fun escapeJson(str: String): String {
            val sb = StringBuilder()
            for (c in str) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\b' -> sb.append("\\b")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            sb.append(String.format("\\u%04x", c.code))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }
}

/**
 * NotificationMessageRecord encapsulates an individual message entry within
 * a Notification.MessagingStyle conversation thread (e.g. WhatsApp, Telegram, Google Messages).
 */
data class NotificationMessageRecord(
    val sender: String,
    val text: String,
    val timestamp: Long,
    val dataMimeType: String? = null,
    val dataUri: String? = null
) {
    /**
     * Serializes this message entry into JSON string format.
     */
    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"sender\":\"").append(NotificationRecord.escapeJson(sender)).append("\",")
        sb.append("\"text\":\"").append(NotificationRecord.escapeJson(text)).append("\",")
        sb.append("\"timestamp\":").append(timestamp)
        if (dataMimeType != null) sb.append(",\"dataMimeType\":\"").append(NotificationRecord.escapeJson(dataMimeType)).append("\"")
        if (dataUri != null) sb.append(",\"dataUri\":\"").append(NotificationRecord.escapeJson(dataUri)).append("\"")
        sb.append("}")
        return sb.toString()
    }
}

/**
 * NotificationRemovedRecord represents an event where a notification is swiped away,
 * clicked, or canceled by the application on the child device.
 */
data class NotificationRemovedRecord(
    val key: String,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: Int = 0
) {
    /**
     * Serializes this removal event into JSON string format.
     */
    fun toJsonString(): String {
        return "{\"key\":\"${NotificationRecord.escapeJson(key)}\"," +
                "\"packageName\":\"${NotificationRecord.escapeJson(packageName)}\"," +
                "\"timestamp\":$timestamp," +
                "\"reason\":$reason}"
    }
}
