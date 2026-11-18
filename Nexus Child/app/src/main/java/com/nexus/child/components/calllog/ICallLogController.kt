package com.nexus.child.components.calllog

/**
 * CallRecord represents a rich, fully-resolved call log entry from the Android device.
 *
 * Hey friend! Every call record packages:
 * - Contact identity: name, phone number, and phone number classification (Mobile, Home, Work).
 * - Call dynamics: direction (incoming, outgoing, missed, rejected, blocked), duration in seconds,
 *   and exact millisecond timestamp.
 * - Hardware attributes: geographic location, SIM card indicator (SIM 1 / SIM 2),
 *   features (HD Voice, Video call), and whether a missed call was read.
 */
data class CallRecord(
    val id: String,
    val number: String,
    val name: String,
    val numberType: String,
    val callType: String,
    val duration: Long,
    val timestamp: Long,
    val location: String = "",
    val simId: String = "",
    val features: List<String> = emptyList(),
    val isRead: Boolean = true
)

/**
 * ICallLogController defines the architectural contract for inspecting and monitoring
 * telephony call history on Android.
 *
 * Separation of Concerns:
 * - Decouples Android ContentResolver and ContentObserver logic from the DataChannel network layer.
 * - Facilitates 100% deterministic unit testing without requiring real Android telephony hardware.
 */
interface ICallLogController {

    /**
     * Checks whether runtime READ_CALL_LOG permission is currently granted.
     */
    fun hasCallLogPermission(): Boolean

    /**
     * Returns true if active real-time call log observation is underway.
     */
    fun isMonitoring(): Boolean

    /**
     * Retrieves the latest N call records ordered by descending timestamp.
     *
     * @param limit Maximum number of records to return (defaults to 100).
     * @return List of resolved CallRecord objects.
     */
    fun fetchLatestCallLogs(limit: Int = 100): List<CallRecord>

    /**
     * Begins real-time monitoring of incoming and outgoing calls via ContentObserver.
     *
     * @param onNewCall Callback invoked whenever a new call log entry is recorded by the OS.
     * @return true if monitoring observer was registered successfully; false otherwise.
     */
    fun startCallLogMonitoring(onNewCall: (CallRecord) -> Unit): Boolean

    /**
     * Halts real-time call log observation and unregisters OS listeners.
     *
     * @return true if an active observer was stopped; false if it was already idle.
     */
    fun stopCallLogMonitoring(): Boolean

    /**
     * Releases background threads, unregisters observers, and clears in-memory caches.
     */
    fun cleanup()
}
