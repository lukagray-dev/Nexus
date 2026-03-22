package nexus.android.child.components.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Production-grade chat payload models for DataChannel.
 * Structured for professional parent-side UI rendering.
 */

@Serializable
data class ChatPayload(
    val version: Int = 2,
    val type: String, // "CONVERSATION_UPDATE", "TYPING_INDICATOR", "APP_OPENED"
    val ts: Long = System.currentTimeMillis(),
    val childId: String,
    val data: ChatPayloadData
)

@Serializable
data class ChatPayloadData(
    val app: String,                    // Package name
    val appName: String,                // Human-readable name
    val conversationId: String,         // Unique conversation identifier
    val contactName: String? = null,    // Contact display name
    val contactId: String? = null,      // Contact identifier (phone/username)
    val messages: List<MessageData> = emptyList(),
    val isTyping: Boolean? = null,      // For typing indicators
    val conversationMeta: ConversationMeta? = null
)

@Serializable
data class ConversationMeta(
    val isGroup: Boolean = false,
    val participantCount: Int? = null,
    val lastActivityTs: Long = System.currentTimeMillis()
)

@Serializable
data class MessageData(
    val msgId: String,                  // Unique message ID (content-based hash)
    val text: String? = null,
    val sender: String? = null,         // Sender name (for group chats)
    val direction: String,              // "IN" or "OUT"
    val type: String = "TEXT",          // "TEXT", "IMAGE", "STICKER", "MEDIA", "DELETED"
    val ts: Long,                       // Message timestamp
    val isDeleted: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

object ChatJson {
    val json = Json { 
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun toJson(payload: ChatPayload): String = json.encodeToString(ChatPayload.serializer(), payload)
    
    fun fromJson(s: String): ChatPayload = json.decodeFromString(ChatPayload.serializer(), s)
}
