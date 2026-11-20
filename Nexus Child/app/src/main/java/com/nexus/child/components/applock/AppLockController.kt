package com.nexus.child.components.applock

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AppLockController coordinates App Lock monitoring, record persistence, and WebRTC telemetry streaming.
 *
 * Hey friend! Here is how telemetry coordination works in this controller:
 * 1. Dual Ingestion: When an unlock attempt occurs in AppLockActivity, it calls AppLockController.notifyAttempt().
 *    The record is immediately saved to local persistent storage (AppLockRepository).
 * 2. Live Synchronization: If the desktop parent console has activated monitoring (via the Play button),
 *    the attempt is instantly streamed across WebRTC DataChannel via an "APPLOCK_NEW" JSON packet.
 * 3. Chunked Offline Flushes: When the parent starts monitoring ("APPLOCK_ON"), the controller reads
 *    buffered offline attempts and flushes them in chunks of 5 records. This ensures WebRTC's safe buffer
 *    threshold (64KB - 256KB) is never exceeded when transmitting Base64 selfies!
 * 4. High Command Remote Status: Provides real-time status packets when the parent queries or toggles lock states.
 */
class AppLockController(
    private val context: Context,
    private val dataSender: (String) -> Boolean
) : IAppLockController {

    companion object {
        private const val TAG = "AppLockController"
        private const val CHUNK_SIZE = 5

        @Volatile
        private var activeInstance: AppLockController? = null

        /**
         * Global entry point for AppLockActivity to report an unlock attempt.
         * Guarantees persistence even if WebRTC or the background service is starting up.
         */
        fun notifyAttempt(context: Context, record: AppLockRecord) {
            val instance = activeInstance
            if (instance != null) {
                instance.recordAttempt(record)
            } else {
                Log.d(TAG, "Active controller instance null; writing record directly to repository")
                AppLockRepository(context).saveRecord(record)
            }
        }
    }

    private val repository = AppLockRepository(context)
    private val isMonitoring = AtomicBoolean(false)

    init {
        activeInstance = this
        Log.d(TAG, "AppLockController initialized and registered as active instance")
    }

    /**
     * Activates live monitoring session and flushes historical offline attempts.
     */
    override fun startMonitoring() {
        Log.d(TAG, "Starting App Lock live monitoring session...")
        isMonitoring.set(true)

        // 1. Send confirmation acknowledgement
        dataSender(AppLockConstants.RESP_APPLOCK_STARTED)

        // 2. Transmit current lock status
        val statusJson = getStatus().toJson()
        dataSender(statusJson.toString())

        // 3. Flush buffered offline records in chunks
        flushHistoryInChunks()
    }

    /**
     * Deactivates the active live monitoring session.
     */
    override fun stopMonitoring() {
        Log.d(TAG, "Stopping App Lock live monitoring session...")
        isMonitoring.set(false)
        dataSender(AppLockConstants.RESP_APPLOCK_STOPPED)
    }

    /**
     * Returns whether live monitoring is currently active.
     */
    override fun isMonitoringActive(): Boolean = isMonitoring.get()

    /**
     * Records an unlock attempt. Persists to repository and sends live if monitoring is active.
     */
    override fun recordAttempt(record: AppLockRecord) {
        // Persist to local offline repository
        repository.saveRecord(record)

        // If parent monitoring is active, stream live attempt
        if (isMonitoring.get()) {
            val packet = JSONObject().apply {
                put("type", AppLockConstants.TYPE_APPLOCK_NEW)
                put("record", record.toJson())
            }
            val sent = dataSender(packet.toString())
            Log.d(TAG, "Dispatched live unlock attempt packet (sent=$sent)")
        }
    }

    /**
     * Flushes stored records in chunked batches to safely deliver selfies over DataChannel.
     */
    private fun flushHistoryInChunks() {
        try {
            val allRecords = repository.getAllRecords()
            if (allRecords.isEmpty()) {
                val emptySnapshot = JSONObject().apply {
                    put("type", AppLockConstants.TYPE_APPLOCK_SNAPSHOT)
                    put("chunkIndex", 0)
                    put("totalChunks", 1)
                    put("count", 0)
                    put("records", JSONArray())
                }
                dataSender(emptySnapshot.toString())
                return
            }

            val chunks = allRecords.chunked(CHUNK_SIZE)
            val totalChunks = chunks.size

            chunks.forEachIndexed { index, chunk ->
                val snapshot = JSONObject().apply {
                    put("type", AppLockConstants.TYPE_APPLOCK_SNAPSHOT)
                    put("chunkIndex", index)
                    put("totalChunks", totalChunks)
                    put("count", chunk.size)
                    val array = JSONArray()
                    chunk.forEach { array.put(it.toJson()) }
                    put("records", array)
                }
                dataSender(snapshot.toString())
                Log.d(TAG, "Flushed AppLock history chunk [${index + 1}/$totalChunks] (${chunk.size} records)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AppLock history: ${e.message}", e)
        }
    }

    /**
     * Retrieves all recorded unlock attempts from the repository.
     */
    override fun getAllRecords(): List<AppLockRecord> = repository.getAllRecords()

    /**
     * Wipes all recorded unlock attempts and cached selfies.
     */
    override fun clearRecords() {
        repository.clearRecords()
        Log.d(TAG, "AppLock repository wiped upon command")
    }

    /**
     * Retrieves the current operational status of the lock system.
     */
    override fun getStatus(): AppLockStatus = AppLockManager.getStatus(context)

    /**
     * Cleans up resources when the background service terminates.
     */
    override fun cleanup() {
        isMonitoring.set(false)
        if (activeInstance === this) {
            activeInstance = null
        }
        Log.d(TAG, "AppLockController cleaned up")
    }
}
