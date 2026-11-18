package com.nexus.child.components.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexus.child.components.telephony.SimCardManager

/**
 * SmsController handles Android SMS content provider querying, contact name
 * resolution via ContactsContract.PhoneLookup, and real-time ContentObserver event dispatching.
 *
 * Hey friend! Here is how SmsController works:
 * 1. Background Worker Thread:
 *    - All database queries run on a dedicated HandlerThread ("NexusSmsWatcher").
 *    - This guarantees smooth 60fps performance on the phone without locking the UI.
 * 2. Contact Name Matching:
 *    - Android's SMS database stores the raw phone number (or alphanumeric shortcode like "VERIFY")
 *      in the "address" column.
 *    - We query ContactsContract.PhoneLookup with the address to resolve the saved contact name
 *      (e.g., "Sarah", "Dad", "Bank Alert").
 * 3. Real-Time Observer:
 *    - We register a ContentObserver on "content://sms/".
 *    - Whenever the phone sends or receives a text message, Android fires our observer,
 *      we extract the newest message, and dispatch it live to the parent console!
 */
class SmsController(
    private val context: Context
) : ISmsController {

    companion object {
        private const val TAG = "SmsController"
        private val SMS_URI: Uri = Uri.parse("content://sms/")
    }

    private val simCardManager = SimCardManager(context)

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var smsObserver: ContentObserver? = null
    private var isMonitoringActive: Boolean = false
    private var newSmsCallback: ((SmsRecord) -> Unit)? = null

    private val observedSmsIds = mutableSetOf<String>()
    private var highestObservedTimestamp: Long = 0L

    init {
        initWorkerThread()
    }

    private fun initWorkerThread() {
        if (workerThread == null) {
            workerThread = HandlerThread("NexusSmsWatcher").apply {
                start()
                workerHandler = Handler(looper)
            }
        }
    }

    override fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isMonitoring(): Boolean = isMonitoringActive

    override fun fetchLatestSms(limit: Int): List<SmsRecord> {
        if (!hasSmsPermission()) {
            Log.w(TAG, "Cannot fetch SMS: READ_SMS permission not granted.")
            return emptyList()
        }

        val messages = mutableListOf<SmsRecord>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                SMS_URI,
                getSmsProjection(),
                null,
                null,
                "date DESC"
            )

            if (cursor != null) {
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val record = parseSmsRecordFromCursor(cursor)
                    if (record != null) {
                        messages.add(record)
                        observedSmsIds.add(record.id)
                        if (record.timestamp > highestObservedTimestamp) {
                            highestObservedTimestamp = record.timestamp
                        }
                        count++
                    }
                }
            }
            Log.d(TAG, "Fetched ${messages.size} SMS messages from Android content provider.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query SMS content provider: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        return messages
    }

    override fun startSmsMonitoring(onNewSms: (SmsRecord) -> Unit): Boolean {
        if (!hasSmsPermission()) {
            Log.w(TAG, "Cannot start SMS monitoring: READ_SMS permission missing.")
            return false
        }

        if (isMonitoringActive) {
            Log.d(TAG, "SMS monitoring is already active.")
            newSmsCallback = onNewSms
            return true
        }

        initWorkerThread()
        val handler = workerHandler ?: Handler(context.mainLooper)
        newSmsCallback = onNewSms

        smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "SMS database change detected. Querying newest messages...")
                checkAndDispatchNewSms()
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                SMS_URI,
                true,
                smsObserver!!
            )
            isMonitoringActive = true
            Log.d(TAG, "Registered SMS ContentObserver successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS ContentObserver: ${e.message}", e)
            smsObserver = null
            isMonitoringActive = false
            return false
        }
    }

    override fun stopSmsMonitoring(): Boolean {
        if (!isMonitoringActive && smsObserver == null) {
            return false
        }

        smsObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
                Log.d(TAG, "Unregistered SMS ContentObserver.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering SMS ContentObserver: ${e.message}")
            }
        }

        smsObserver = null
        isMonitoringActive = false
        newSmsCallback = null
        observedSmsIds.clear()
        highestObservedTimestamp = 0L
        return true
    }

    override fun cleanup() {
        stopSmsMonitoring()
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    /**
     * Checks the database for any newly inserted SMS records since the last query.
     */
    private fun checkAndDispatchNewSms() {
        if (!hasSmsPermission()) return

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                SMS_URI,
                getSmsProjection(),
                null,
                null,
                "date DESC"
            )

            if (cursor != null) {
                val newMessages = mutableListOf<SmsRecord>()
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val record = parseSmsRecordFromCursor(cursor)
                    if (record != null && !observedSmsIds.contains(record.id)) {
                        observedSmsIds.add(record.id)
                        if (record.timestamp > highestObservedTimestamp) {
                            highestObservedTimestamp = record.timestamp
                        }
                        newMessages.add(record)
                    }
                    count++
                }

                // Dispatch chronologically (oldest to newest)
                newMessages.reversed().forEach { msg ->
                    Log.d(TAG, "New live SMS observed: from=${msg.senderName} (${msg.address}) type=${msg.smsType}")
                    newSmsCallback?.invoke(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for new SMS messages: ${e.message}", e)
        } finally {
            cursor?.close()
        }
    }

    /**
     * Safely reads and converts an individual SMS row into an SmsRecord.
     */
    private fun parseSmsRecordFromCursor(cursor: Cursor): SmsRecord? {
        return try {
            val idCol = cursor.getColumnIndex("_id")
            val addressCol = cursor.getColumnIndex("address")
            val bodyCol = cursor.getColumnIndex("body")
            val dateCol = cursor.getColumnIndex("date")
            val dateSentCol = cursor.getColumnIndex("date_sent")
            val typeCol = cursor.getColumnIndex("type")
            val readCol = cursor.getColumnIndex("read")
            val subIdCol = cursor.getColumnIndex("sub_id")

            if (addressCol == -1 || dateCol == -1) {
                return null
            }

            val id = if (idCol != -1) cursor.getString(idCol) ?: "" else ""
            val address = cursor.getString(addressCol) ?: "Unknown"
            val body = if (bodyCol != -1) cursor.getString(bodyCol) ?: "" else ""
            val date = cursor.getLong(dateCol)
            val dateSent = if (dateSentCol != -1) cursor.getLong(dateSentCol) else 0L
            val typeInt = if (typeCol != -1) cursor.getInt(typeCol) else 1
            val read = if (readCol != -1) cursor.getInt(readCol) == 1 else true

            var simId = ""
            if (subIdCol != -1) {
                val subId = cursor.getString(subIdCol)
                simId = simCardManager.resolveSimLabel(subId)
            }

            // Resolve contact name for address via PhoneLookup if contacts permission granted
            var senderName = ""
            if (address.isNotBlank() && hasContactsPermission()) {
                senderName = resolveContactName(address)
            }

            SmsRecord(
                id = if (id.isNotBlank()) id else "${address}_$date",
                address = address,
                senderName = senderName,
                body = body,
                smsType = mapSmsType(typeInt),
                timestamp = date,
                dateSent = dateSent,
                read = read,
                simId = simId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SmsRecord from cursor: ${e.message}", e)
            null
        }
    }

    /**
     * Queries ContactsContract.PhoneLookup to find the contact name matching a phone number.
     */
    private fun resolveContactName(phoneNumber: String): String {
        var name = ""
        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx != -1) {
                    name = cursor.getString(idx) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PhoneLookup failed for SMS address: ${e.message}")
        } finally {
            cursor?.close()
        }
        return name
    }

    /**
     * Converts Android SMS integer types into readable protocol tokens.
     */
    private fun mapSmsType(type: Int): String {
        return when (type) {
            1 -> "inbox"
            2 -> "sent"
            3 -> "draft"
            4 -> "outbox"
            5 -> "failed"
            6 -> "queued"
            else -> "unknown"
        }
    }

    private fun getSmsProjection(): Array<String> {
        return arrayOf(
            "_id",
            "address",
            "body",
            "date",
            "date_sent",
            "type",
            "read",
            "sub_id"
        )
    }
}
