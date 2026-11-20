package com.nexus.child.service.watchdog

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.receiver.UnlockReceiver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WatchdogService runs as a standalone daemon in an isolated process (:watchdog).
 *
 * Hey friend! Here is why this separate process is our ultimate shield against OEM task killers:
 * 1. Process Isolation: Android OEMs (especially Xiaomi/MIUI, Samsung, Oppo, Vivo) run task cleaners
 *    like "OneKeyClean" when the user clears Recent Apps. These cleaners kill the process associated
 *    with the activity window (com.nexus.child).
 * 2. Surviving Recents Clearing: Because WatchdogService runs in a separate Linux process (:watchdog)
 *    and never displays an Activity, OneKeyClean does not kill it!
 * 3. Mutual Resurrection via Binder Death Recipient: WatchdogService binds to NexusBackgroundService.
 *    The moment the Linux kernel terminates com.nexus.child, onServiceDisconnected() or onBindingDied()
 *    fires inside :watchdog in under 10 milliseconds.
 * 4. Instant Restart: WatchdogService immediately calls NexusBackgroundService.start() and re-binds,
 *    resurrecting the monitoring engine instantly without waiting for backoff timers!
 * 5. Dynamic Unlock Detection: Keeps a dynamically registered UnlockReceiver so screen unlock events
 *    (ACTION_USER_PRESENT) are always captured without Android 8+ manifest broadcast restrictions.
 */
class WatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"

        /**
         * Helper to start the WatchdogService in the :watchdog process.
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, WatchdogService::class.java)
                context.startService(intent)
                Log.d(TAG, "Dispatched startService for WatchdogService (:watchdog)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WatchdogService: ${e.message}", e)
            }
        }
    }

    private val localBinder = WatchdogBinder()
    private var unlockReceiver: UnlockReceiver? = null
    private var isBoundToMain = false
    private val isBindingInProgress = AtomicBoolean(false)

    inner class WatchdogBinder : Binder() {
        fun getService(): WatchdogService = this@WatchdogService
    }

    private val mainServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBoundToMain = true
            isBindingInProgress.set(false)
            Log.d(TAG, "Successfully bound to NexusBackgroundService from :watchdog process")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBoundToMain = false
            isBindingInProgress.set(false)
            Log.w(TAG, "NexusBackgroundService disconnected (process was killed)! Resurrecting now...")
            resurrectMainService()
        }

        override fun onBindingDied(name: ComponentName?) {
            isBoundToMain = false
            isBindingInProgress.set(false)
            Log.w(TAG, "Binding to NexusBackgroundService died! Resurrecting now...")
            resurrectMainService()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WatchdogService (:watchdog process) created")

        // Dynamically register UnlockReceiver to avoid Android 8+ manifest implicit broadcast blocks
        registerUnlockReceiver()

        // Bind to the primary service in com.nexus.child
        bindToMainService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WatchdogService onStartCommand (startId=$startId)")
        if (!isBoundToMain && !isBindingInProgress.get()) {
            bindToMainService()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onDestroy() {
        Log.w(TAG, "WatchdogService (:watchdog process) being destroyed")
        unregisterUnlockReceiver()
        safeUnbind()
        super.onDestroy()
    }

    /**
     * Binds to NexusBackgroundService to monitor its Linux process lifecycle via Binder IPC.
     * Guarded by isBindingInProgress to prevent repeated IPC binding storms.
     */
    private fun bindToMainService() {
        if (isBoundToMain || isBindingInProgress.getAndSet(true)) {
            Log.d(TAG, "Binding to NexusBackgroundService already active or in progress. Skipping redundant bind call.")
            return
        }
        try {
            val intent = Intent(this, NexusBackgroundService::class.java)
            val success = bindService(intent, mainServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "bindService to NexusBackgroundService dispatched (success=$success)")
            if (!success) {
                isBindingInProgress.set(false)
            }
        } catch (e: Exception) {
            isBindingInProgress.set(false)
            Log.e(TAG, "Error binding to NexusBackgroundService: ${e.message}", e)
        }
    }

    /**
     * Safely unbinds from NexusBackgroundService.
     */
    private fun safeUnbind() {
        isBindingInProgress.set(false)
        if (isBoundToMain) {
            try {
                unbindService(mainServiceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from main service: ${e.message}")
            } finally {
                isBoundToMain = false
            }
        }
    }

    /**
     * Resurrects NexusBackgroundService immediately when its process dies.
     */
    private fun resurrectMainService() {
        try {
            Log.d(TAG, "Reviving NexusBackgroundService from :watchdog process...")
            NexusBackgroundService.start(applicationContext)
            // Re-bind to monitor the new process
            bindToMainService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resurrect NexusBackgroundService from watchdog: ${e.message}", e)
        }
    }

    /**
     * Dynamically registers UnlockReceiver.
     * Android 8+ strictly forbids implicit broadcasts like USER_PRESENT in Manifest,
     * but dynamically registered receivers receive them with 100% reliability!
     */
    private fun registerUnlockReceiver() {
        if (unlockReceiver == null) {
            try {
                unlockReceiver = UnlockReceiver()
                val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                registerReceiver(unlockReceiver, filter)
                Log.d(TAG, "Dynamic UnlockReceiver registered successfully in :watchdog process")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register dynamic UnlockReceiver: ${e.message}", e)
            }
        }
    }

    /**
     * Safely unregisters UnlockReceiver.
     */
    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Dynamic UnlockReceiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering UnlockReceiver: ${e.message}")
            } finally {
                unlockReceiver = null
            }
        }
    }
}
