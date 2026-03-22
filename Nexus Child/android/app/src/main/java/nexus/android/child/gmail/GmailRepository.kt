@file:Suppress("DEPRECATION")

package nexus.android.child.gmail

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class GmailRepository(private val appContext: Context) {

    companion object {
        private const val TAG = "GmailRepository"
        private const val MAX_INCREMENTAL = 50
    }

    private fun buildService(): Gmail? {
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
        if (account == null) {
            Log.w(TAG, "No signed-in Google account. Cannot build Gmail service.")
            return null
        }
        if (!GmailAuthRepository.isConnected(appContext)) {
            Log.d(TAG, "Gmail not marked as connected; skipping service build")
            return null
        }

        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                appContext,
                listOf(GmailScopes.GMAIL_READONLY)
            ).apply {
                selectedAccount = account.account
            }

            Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Delta Child")
                .build()
        } catch (e: ExceptionInInitializerError) {
            // Known issue: Gmail Java client can throw during static init on some Android/ROM combos
            Log.e(TAG, "Failed to initialize Gmail client; disabling Gmail sync", e)
            GmailAuthRepository.clear(appContext)
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build Gmail service", t)
            null
        }
    }

    suspend fun fetchInitial(limit: Int): List<GmailEmail> =
        fetchEmailsInternal(limit.coerceAtLeast(1), useLastInternalDate = false)

    suspend fun fetchIncremental(): List<GmailEmail> =
        fetchEmailsInternal(MAX_INCREMENTAL, useLastInternalDate = true)

    private suspend fun fetchEmailsInternal(
        maxResults: Int,
        useLastInternalDate: Boolean
    ): List<GmailEmail> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext emptyList()
        try {
            val user = "me"
            val lastInternalDate = if (useLastInternalDate) {
                GmailSyncStateStore.getLastInternalDate(appContext)
            } else 0L

            val query = buildQuery(lastInternalDate)
            val listReq = service.users().messages().list(user)
                .setMaxResults(maxResults.toLong())
                .setQ(query)
                .setIncludeSpamTrash(false)

            val listResponse = listReq.execute()
            val messages = listResponse.messages ?: return@withContext emptyList()
            if (messages.isEmpty()) return@withContext emptyList()

            val result = mutableListOf<GmailEmail>()
            var newestInternalDate = lastInternalDate

            for (m in messages) {
                val full = service.users().messages().get(user, m.id).setFormat("full").execute()
                val email = full.toDomainEmail()
                result.add(email)
                val internalDate = full.internalDate ?: 0L
                if (internalDate > newestInternalDate) {
                    newestInternalDate = internalDate
                }
            }

            if (newestInternalDate > 0L) {
                GmailSyncStateStore.updateLastInternalDate(appContext, newestInternalDate)
            }

            result
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "User-recoverable auth error; clearing Gmail state", e)
            GmailAuthRepository.clear(appContext)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Gmail messages", e)
            emptyList()
        }
    }

    private fun buildQuery(lastInternalDate: Long): String {
        val base = StringBuilder("in:inbox")
        if (lastInternalDate > 0L) {
            val seconds = lastInternalDate / 1000L
            base.append(" after:").append(seconds)
        } else {
            base.append(" newer_than:7d")
        }
        return base.toString()
    }

    private fun Message.toDomainEmail(): GmailEmail {
        val hdrs = this.payload?.headers ?: emptyList()
        fun h(name: String): String =
            hdrs.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value ?: ""

        val subject = h("Subject")
        val from = h("From")
        val toRaw = h("To")
        val toList = toRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val ts = this.internalDate ?: System.currentTimeMillis()
        val snippet = this.snippet ?: ""
        val isUnread = this.labelIds?.contains("UNREAD") == true

        val textParts = mutableListOf<String>()
        val htmlParts = mutableListOf<String>()
        this.payload?.let { collectBodies(it, textParts, htmlParts) }

        val bodyText = textParts.joinToString("\n").ifBlank { null }
        val bodyHtml = htmlParts.joinToString("\n").ifBlank { null }

        return GmailEmail(
            id = this.id ?: "",
            threadId = this.threadId,
            subject = subject,
            from = from,
            to = toList,
            timestamp = ts,
            snippet = snippet,
            bodyText = bodyText,
            bodyHtml = bodyHtml,
            isUnread = isUnread
        )
    }

    private fun collectBodies(
        part: MessagePart,
        textParts: MutableList<String>,
        htmlParts: MutableList<String>
    ) {
        val mime = part.mimeType ?: ""
        val body = part.body
        val data = body?.data
        if (!data.isNullOrEmpty()) {
            val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP)
            val text = bytes.toString(Charset.forName("UTF-8"))
            when {
                mime.startsWith("text/plain") -> textParts.add(text)
                mime.startsWith("text/html") -> htmlParts.add(text)
            }
        }
        val children = part.parts ?: emptyList()
        for (child in children) {
            collectBodies(child, textParts, htmlParts)
        }
    }
}
