package com.nexus.child.components.callalert

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CallAlertController monitors live cellular call activity on the Android child device
 * and emits real-time + 30-second repeating alerts to the Desktop Parent while a call is active.
 *
 * Hey friend! Here is how live call alerting works across Android versions:
 * 1. Lazy Lifecycle: Listeners are registered ONLY when the parent WebRTC DataChannel opens,
 *    and immediately unregistered on disconnect so zero battery or background resources are wasted.
 * 2. Dual Telephony Hooks:
 *    - Android 12+ (API 31+): Registers `TelephonyCallback.CallStateListener` for immediate state
 *      transitions (RINGING / OFFHOOK / IDLE).
 *    - Android 10-11 (API 29-30): Registers `PhoneStateListener.LISTEN_CALL_STATE`.
 *    - Dynamic `ACTION_PHONE_STATE_CHANGED` BroadcastReceiver: Captures `EXTRA_INCOMING_NUMBER`
 *      when `READ_CALL_LOG` is granted and resolves contact names via `ContactsContract.PhoneLookup`.
 * 3. Repeating Call Reminders: While the child remains in RINGING or OFFHOOK and `isAlertEnabled`
 *    is true, a worker Handler dispatches an updated `CALL_STATE` reminder every 30 seconds until
 *    the call returns to IDLE.
 */
class CallAlertController(
    private val context: Context,
    private val dataSender: (String) -> Boolean
) : ICallAlertController {

    companion object {
        private const val TAG = "CallAlertController"
        const val ALERT_REPEAT_INTERVAL_MS = 30_000L
    }

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val stateMachine = CallAlertStateMachine()
    private val isMonitoringActive = AtomicBoolean(false)
    private val alertEnabled = AtomicBoolean(true)

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var telephonyCallback: Any? = null
    private var phoneStateListener: Any? = null
    private var phoneStateReceiver: BroadcastReceiver? = null

    private val repeatAlertRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoringActive.get() || !alertEnabled.get() || !stateMachine.isCallActive()) {
                return
            }
            val repeatEvent = stateMachine.buildRepeatEvent()
            if (repeatEvent != null) {
                val json = stateMachine.serializeEventJson(repeatEvent)
                val sent = dataSender(json)
                Log.d(TAG, "Dispatched 30s repeating call alert (${repeatEvent.state}, sent=$sent)")
            }
            workerHandler?.postDelayed(this, ALERT_REPEAT_INTERVAL_MS)
        }
    }

    override fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isAlertEnabled(): Boolean = alertEnabled.get()

    override fun setAlertEnabled(enabled: Boolean) {
        val previous = alertEnabled.getAndSet(enabled)
        if (previous == enabled) return

        Log.d(TAG, "Active call alerts toggled: enabled=$enabled")
        if (!enabled) {
            cancelRepeatAlerts()
            return
        }

        // If the parent re-enabled alerts while a call is already underway, emit immediately
        if (isMonitoringActive.get()) {
            evaluateCurrentOsCallState(forceCheck = true)
        }
    }

    @Synchronized
    override fun startMonitoring(): Boolean {
        if (!hasPhoneStatePermission()) {
            Log.w(TAG, "Cannot start CallAlertController: READ_PHONE_STATE permission missing.")
            return false
        }

        if (isMonitoringActive.get()) {
            Log.d(TAG, "CallAlertController is already monitoring.")
            evaluateCurrentOsCallState(forceCheck = true)
            return true
        }

        initWorkerThread()
        registerTelephonyHooks()
        registerPhoneStateBroadcastReceiver()
        isMonitoringActive.set(true)
        Log.d(TAG, "CallAlertController started successfully.")

        // Immediately inspect current call state in case child is already on a call at connect time
        evaluateCurrentOsCallState(forceCheck = false)
        return true
    }

    @Synchronized
    override fun stopMonitoring(): Boolean {
        val wasActive = isMonitoringActive.getAndSet(false)
        cancelRepeatAlerts()
        unregisterTelephonyHooks()
        unregisterPhoneStateBroadcastReceiver()
        stateMachine.reset()
        if (wasActive) {
            Log.d(TAG, "CallAlertController stopped and listeners released.")
        }
        return wasActive
    }

    override fun cleanup() {
        stopMonitoring()
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    private fun initWorkerThread() {
        if (workerThread == null) {
            workerThread = HandlerThread("NexusCallAlertWorker").apply {
                start()
                workerHandler = Handler(looper)
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readOsCallState(tm: TelephonyManager): Int {
        return tm.callState
    }

    @SuppressLint("MissingPermission")
    private fun evaluateCurrentOsCallState(forceCheck: Boolean) {
        val tm = telephonyManager ?: return
        val handler = workerHandler ?: return
        handler.post {
            try {
                val currentOsState = readOsCallState(tm)
                if (currentOsState != TelephonyManager.CALL_STATE_IDLE) {
                    if (forceCheck && stateMachine.isCallActive()) {
                        val reminder = stateMachine.buildRepeatEvent()
                        if (reminder != null && alertEnabled.get()) {
                            dataSender(stateMachine.serializeEventJson(reminder))
                            scheduleRepeatAlerts()
                        }
                    } else {
                        handleOsStateTransition(currentOsState, null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inspect initial TelephonyManager callState: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerTelephonyHooks() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerApi31TelephonyCallback(tm)
            } else {
                registerLegacyPhoneStateListener(tm)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Telephony call-state listener: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun registerApi31TelephonyCallback(tm: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                workerHandler?.post {
                    handleOsStateTransition(state, null)
                }
            }
        }
        telephonyCallback = callback
        tm.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun registerLegacyPhoneStateListener(tm: TelephonyManager) {
        val listener = object : android.telephony.PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                workerHandler?.post {
                    handleOsStateTransition(state, phoneNumber)
                }
            }
        }
        phoneStateListener = listener
        tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
    }

    @Suppress("DEPRECATION")
    private fun unregisterTelephonyHooks() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    tm.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                (phoneStateListener as? android.telephony.PhoneStateListener)?.let {
                    tm.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering telephony listener: ${e.message}")
        }
    }

    private fun registerPhoneStateBroadcastReceiver() {
        if (phoneStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
                val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val osState = when (stateExtra) {
                    TelephonyManager.EXTRA_STATE_RINGING -> CallAlertStateMachine.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> CallAlertStateMachine.CALL_STATE_OFFHOOK
                    TelephonyManager.EXTRA_STATE_IDLE -> CallAlertStateMachine.CALL_STATE_IDLE
                    else -> return
                }
                @Suppress("DEPRECATION")
                val incomingNumber = if (hasCallLogPermission()) {
                    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                } else {
                    null
                }

                workerHandler?.post {
                    handleOsStateTransition(osState, incomingNumber)
                }
            }
        }

        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            phoneStateReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "Could not register ACTION_PHONE_STATE_CHANGED receiver: ${e.message}")
        }
    }

    private fun unregisterPhoneStateBroadcastReceiver() {
        phoneStateReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering PHONE_STATE receiver: ${e.message}")
            } finally {
                phoneStateReceiver = null
            }
        }
    }

    private fun handleOsStateTransition(osState: Int, rawNumber: String?) {
        if (!isMonitoringActive.get()) return

        val cleanNumber = rawNumber?.trim().orEmpty()
        val resolvedName = if (
            cleanNumber.isNotEmpty() &&
            cleanNumber != stateMachine.getCurrentNumber() &&
            hasContactsPermission()
        ) {
            resolveContactName(cleanNumber)
        } else {
            ""
        }

        val event = stateMachine.onOsCallStateChanged(
            osState = osState,
            rawNumber = cleanNumber,
            resolvedContactName = resolvedName
        ) ?: return

        if (event.state == CallAlertStateMachine.STATE_IDLE) {
            cancelRepeatAlerts()
            if (alertEnabled.get()) {
                val json = stateMachine.serializeEventJson(event)
                dataSender(json)
                Log.d(TAG, "Call ended (${event.direction}); dispatched IDLE state to parent.")
            }
            return
        }

        if (alertEnabled.get()) {
            val json = stateMachine.serializeEventJson(event)
            val sent = dataSender(json)
            Log.d(TAG, "Call alert dispatched: state=${event.state}, dir=${event.direction}, sent=$sent")
            scheduleRepeatAlerts()
        }
    }

    private fun scheduleRepeatAlerts() {
        val handler = workerHandler ?: return
        handler.removeCallbacks(repeatAlertRunnable)
        if (alertEnabled.get() && stateMachine.isCallActive()) {
            handler.postDelayed(repeatAlertRunnable, ALERT_REPEAT_INTERVAL_MS)
        }
    }

    private fun cancelRepeatAlerts() {
        workerHandler?.removeCallbacks(repeatAlertRunnable)
    }

    private fun resolveContactName(phoneNumber: String): String {
        var cursor: Cursor? = null
        return try {
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
                if (idx != -1) cursor.getString(idx) ?: "" else ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "PhoneLookup contact resolution failed: ${e.message}")
            ""
        } finally {
            cursor?.close()
        }
    }
}
