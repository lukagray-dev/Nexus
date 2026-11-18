package com.nexus.child.components.sms

/**
 * SmsRecord represents an individual SMS message intercepted on the Android child device.
 *
 * Hey friend! Every SMS record contains:
 * - Address & Identity: Phone number or sender ID, plus the contact name resolved via PhoneLookup.
 * - Message Content: Full text body.
 * - Direction: "inbox" (received) or "sent" (outgoing from child).
 * - Timestamps: Both received timestamp (date) and carrier dispatch timestamp (dateSent).
 * - Metadata: Read status and SIM card subscription slot.
 */
data class SmsRecord(
    val id: String,
    val address: String,
    val senderName: String,
    val body: String,
    val smsType: String,
    val timestamp: Long,
    val dateSent: Long = 0L,
    val read: Boolean = true,
    val simId: String = ""
)

/**
 * ISmsController defines the interface contract for querying and monitoring
 * SMS messages on the Android child device.
 */
interface ISmsController {

    /**
     * Checks whether runtime READ_SMS permission is currently granted.
     */
    fun hasSmsPermission(): Boolean

    /**
     * Returns true if active real-time SMS observation is underway.
     */
    fun isMonitoring(): Boolean

    /**
     * Retrieves the latest N SMS messages ordered by descending timestamp.
     *
     * @param limit Maximum number of records to return (defaults to 100).
     * @return List of resolved SmsRecord objects.
     */
    fun fetchLatestSms(limit: Int = 100): List<SmsRecord>

    /**
     * Begins real-time monitoring of incoming and outgoing SMS via ContentObserver.
     *
     * @param onNewSms Callback invoked whenever a new SMS is inserted into the system provider.
     * @return true if monitoring observer was registered successfully; false otherwise.
     */
    fun startSmsMonitoring(onNewSms: (SmsRecord) -> Unit): Boolean

    /**
     * Halts real-time SMS observation and unregisters listeners.
     *
     * @return true if an active observer was stopped; false if it was already idle.
     */
    fun stopSmsMonitoring(): Boolean

    /**
     * Releases background threads, unregisters observers, and clears caches.
     */
    fun cleanup()
}
