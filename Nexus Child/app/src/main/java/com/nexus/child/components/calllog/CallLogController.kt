package com.nexus.child.components.calllog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexus.child.components.telephony.SimCardManager

/**
 * CallLogController handles Android CallLog provider querying, contact name
 * resolution via PhoneLookup, and real-time ContentObserver event dispatching.
 *
 * Hey friend! Here is how CallLogController works under the hood:
 * 1. Background Worker Thread:
 *    - To avoid lagging the Android UI, we spin up a dedicated HandlerThread ("NexusCallLogWatcher").
 *    - All database queries and cursor iterations execute safely on this background thread!
 * 2. Contact Name Resolution:
 *    - Android stores a cached name in CallLog.Calls.CACHED_NAME.
 *    - But what if the user saved the contact AFTER the phone call was made?
 *    - If CACHED_NAME is blank, we query ContactsContract.PhoneLookup using the phone number
 *      to fetch the fresh, live contact name (e.g. "Mom", "Dad", "School").
 * 3. Real-Time Observer:
 *    - When monitoring starts, we register a ContentObserver on CallLog.Calls.CONTENT_URI.
 *    - Whenever Android logs an incoming, outgoing, or missed call, our observer triggers,
 *      queries the newest record, and invokes the onNewCall callback immediately!
 */
class CallLogController(
    private val context: Context
) : ICallLogController {

    companion object {
        private const val TAG = "CallLogController"
    }

    private val simCardManager = SimCardManager(context)

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var callObserver: ContentObserver? = null
    private var isMonitoringActive: Boolean = false
    private var newCallCallback: ((CallRecord) -> Unit)? = null

    // Tracks seen call IDs to prevent duplicate transmissions during live observation
    private val observedCallIds = mutableSetOf<String>()
    private var highestObservedTimestamp: Long = 0L

    init {
        initWorkerThread()
    }

    /**
     * Initializes the background thread and handler for non-blocking database queries.
     */
    private fun initWorkerThread() {
        if (workerThread == null) {
            workerThread = HandlerThread("NexusCallLogWatcher").apply {
                start()
                workerHandler = Handler(looper)
            }
        }
    }

    override fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isMonitoring(): Boolean = isMonitoringActive

    override fun fetchLatestCallLogs(limit: Int): List<CallRecord> {
        if (!hasCallLogPermission()) {
            Log.w(TAG, "Cannot fetch call logs: READ_CALL_LOG permission not granted.")
            return emptyList()
        }

        val records = mutableListOf<CallRecord>()
        var cursor: Cursor? = null

        try {
            val projection = getCallLogProjection()
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            if (cursor != null) {
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val record = parseCallRecordFromCursor(cursor)
                    if (record != null) {
                        records.add(record)
                        observedCallIds.add(record.id)
                        if (record.timestamp > highestObservedTimestamp) {
                            highestObservedTimestamp = record.timestamp
                        }
                        count++
                    }
                }
            }
            Log.d(TAG, "Fetched ${records.size} call records from Android CallLog provider.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query CallLog content provider: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        return records
    }

    override fun startCallLogMonitoring(onNewCall: (CallRecord) -> Unit): Boolean {
        if (!hasCallLogPermission()) {
            Log.w(TAG, "Cannot start CallLog monitoring: READ_CALL_LOG permission missing.")
            return false
        }

        if (isMonitoringActive) {
            Log.d(TAG, "CallLog monitoring is already active.")
            newCallCallback = onNewCall
            return true
        }

        initWorkerThread()
        val handler = workerHandler ?: Handler(context.mainLooper)
        newCallCallback = onNewCall

        // Create ContentObserver to listen for real-time call log changes
        callObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "CallLog database change detected. Querying newest calls...")
                checkAndDispatchNewCalls()
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callObserver!!
            )
            isMonitoringActive = true
            Log.d(TAG, "Registered CallLog ContentObserver successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register CallLog ContentObserver: ${e.message}", e)
            callObserver = null
            isMonitoringActive = false
            return false
        }
    }

    override fun stopCallLogMonitoring(): Boolean {
        if (!isMonitoringActive && callObserver == null) {
            return false
        }

        callObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
                Log.d(TAG, "Unregistered CallLog ContentObserver.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering CallLog ContentObserver: ${e.message}")
            }
        }

        callObserver = null
        isMonitoringActive = false
        newCallCallback = null
        observedCallIds.clear()
        highestObservedTimestamp = 0L
        return true
    }

    override fun cleanup() {
        stopCallLogMonitoring()
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    /**
     * Queries any newly recorded calls that have occurred since the last observation,
     * dispatching them to the live callback.
     */
    private fun checkAndDispatchNewCalls() {
        if (!hasCallLogPermission()) return

        var cursor: Cursor? = null
        try {
            val projection = getCallLogProjection()
            // Query recent calls without SQL LIMIT token (which violates ContentProvider sortOrder contract)
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            if (cursor != null) {
                val newRecords = mutableListOf<CallRecord>()
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val record = parseCallRecordFromCursor(cursor)
                    if (record != null && !observedCallIds.contains(record.id)) {
                        // Found a fresh call record not seen before!
                        observedCallIds.add(record.id)
                        if (record.timestamp > highestObservedTimestamp) {
                            highestObservedTimestamp = record.timestamp
                        }
                        newRecords.add(record)
                    }
                    count++
                }

                // Dispatch chronologically (oldest to newest among the new batch)
                newRecords.reversed().forEach { record ->
                    Log.d(TAG, "New live call observed: ${record.name} (${record.number}) - ${record.callType}")
                    newCallCallback?.invoke(record)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for new call logs: ${e.message}", e)
        } finally {
            cursor?.close()
        }
    }

    /**
     * Safely reads and resolves a single CallRecord from an active Cursor position.
     */
    private fun parseCallRecordFromCursor(cursor: Cursor): CallRecord? {
        return try {
            val idCol = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberCol = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameCol = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numTypeCol = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val numLabelCol = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationCol = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val typeCol = cursor.getColumnIndex(CallLog.Calls.TYPE)

            if (numberCol == -1 || dateCol == -1 || typeCol == -1) {
                return null
            }

            val id = if (idCol != -1) cursor.getString(idCol) ?: "" else ""
            val number = cursor.getString(numberCol) ?: "Unknown"
            var name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
            var numberType = formatNumberType(
                if (numTypeCol != -1) cursor.getInt(numTypeCol) else 0,
                if (numLabelCol != -1) cursor.getString(numLabelCol) else null
            )

            // Contact Name Resolution Fallback: Query PhoneLookup if CACHED_NAME is empty
            if (name.isBlank() && number.isNotBlank() && hasContactsPermission()) {
                val resolvedContact = resolveContactInfo(number)
                if (resolvedContact.first.isNotBlank()) {
                    name = resolvedContact.first
                    if (resolvedContact.second.isNotBlank()) {
                        numberType = resolvedContact.second
                    }
                }
            }

            val date = cursor.getLong(dateCol)
            val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
            val callType = mapCallType(cursor.getInt(typeCol))

            // Optional Extended Columns (API 21+)
            var location = ""
            var simId = ""
            val features = mutableListOf<String>()
            var isRead = true

            val locCol = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
            if (locCol != -1) {
                location = cursor.getString(locCol) ?: ""
            }

            val accountCol = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
            if (accountCol != -1) {
                val accountId = cursor.getString(accountCol)
                simId = simCardManager.resolveSimLabel(accountId)
            }

            val featCol = cursor.getColumnIndex(CallLog.Calls.FEATURES)
            if (featCol != -1) {
                val featMask = cursor.getInt(featCol)
                if ((featMask and CallLog.Calls.FEATURES_VIDEO) != 0) features.add("VIDEO")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if ((featMask and CallLog.Calls.FEATURES_HD_CALL) != 0) features.add("HD_VOICE")
                    if ((featMask and CallLog.Calls.FEATURES_WIFI) != 0) features.add("WIFI_CALL")
                }
            }

            val readCol = cursor.getColumnIndex(CallLog.Calls.IS_READ)
            if (readCol != -1) {
                isRead = cursor.getInt(readCol) != 0
            }

            CallRecord(
                id = if (id.isNotBlank()) id else "${number}_$date",
                number = number,
                name = name,
                numberType = numberType,
                callType = callType,
                duration = duration,
                timestamp = date,
                location = location,
                simId = simId,
                features = features,
                isRead = isRead
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CallRecord from cursor: ${e.message}", e)
            null
        }
    }

    /**
     * Resolves the contact's display name and phone label using ContactsContract.PhoneLookup.
     * Returns Pair(displayName, label).
     */
    private fun resolveContactInfo(phoneNumber: String): Pair<String, String> {
        var contactName = ""
        var contactLabel = ""
        var cursor: Cursor? = null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.TYPE,
                ContactsContract.PhoneLookup.LABEL
            )
            cursor = context.contentResolver.query(uri, projection, null, null, null)

            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val typeIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.LABEL)

                if (nameIdx != -1) {
                    contactName = cursor.getString(nameIdx) ?: ""
                }
                if (typeIdx != -1) {
                    val type = cursor.getInt(typeIdx)
                    val label = if (labelIdx != -1) cursor.getString(labelIdx) else null
                    contactLabel = formatNumberType(type, label)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ContactsContract.PhoneLookup query failed for number: ${e.message}")
        } finally {
            cursor?.close()
        }

        return Pair(contactName, contactLabel)
    }

    /**
     * Maps Android raw integer call types into standard protocol string tokens.
     */
    private fun mapCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "incoming"
            CallLog.Calls.OUTGOING_TYPE -> "outgoing"
            CallLog.Calls.MISSED_TYPE -> "missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
            CallLog.Calls.REJECTED_TYPE -> "rejected"
            CallLog.Calls.BLOCKED_TYPE -> "blocked"
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "external"
            else -> "unknown"
        }
    }

    /**
     * Formats integer phone number types (Mobile, Home, Work) into clean strings.
     */
    private fun formatNumberType(type: Int, customLabel: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "Custom"
            else -> "General"
        }
    }

    /**
     * Constructs the projection array containing all supported standard and extended columns.
     */
    private fun getCallLogProjection(): Array<String> {
        val columns = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        // Add API 21+ columns
        columns.add(CallLog.Calls.GEOCODED_LOCATION)
        columns.add(CallLog.Calls.PHONE_ACCOUNT_ID)
        columns.add(CallLog.Calls.FEATURES)
        columns.add(CallLog.Calls.IS_READ)

        return columns.toTypedArray()
    }
}
