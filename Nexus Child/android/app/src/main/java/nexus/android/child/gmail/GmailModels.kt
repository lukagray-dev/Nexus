package nexus.android.child.gmail

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gmail domain models used by the sync layer and transport.
 */
@Serializable
data class GmailEmail(
    val id: String,
    val threadId: String?,
    val subject: String,
    val from: String,
    val to: List<String>,
    val timestamp: Long,
    val snippet: String,
    val bodyText: String?,
    val bodyHtml: String?,
    val isUnread: Boolean
)

@Serializable
data class GmailBatchPayload(
    val version: Int = 1,
    val type: String = "gmail_batch",
    val ts: Long = System.currentTimeMillis(),
    val childId: String,
    val account: String,
    val emails: List<GmailEmail>
)

object GmailJson {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun toJson(payload: GmailBatchPayload): String = json.encodeToString(payload)
}
