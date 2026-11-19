package com.nexus.child.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.watchdog.WatchdogService

/**
 * UnlockReceiver intercepts the user unlocking their device screen (ACTION_USER_PRESENT or ACTION_SCREEN_ON).
 *
 * Hey friend! Here is how this keep-alive mechanism works:
 * On Android 8+, ACTION_USER_PRESENT is an implicit broadcast. It cannot be declared statically
 * in AndroidManifest.xml (Android blocks it with "Background execution not allowed").
 *
 * Instead, this receiver is registered dynamically in NexusBackgroundService and WatchdogService.
 * When the child unlocks their phone, this receiver verifies that both monitoring engines are healthy.
 */
class UnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UnlockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
            Log.d(TAG, "Screen activity detected ($action). Verifying background services...")
            try {
                if (!NexusBackgroundService.isRunning()) {
                    Log.d(TAG, "NexusBackgroundService not running. Reviving now...")
                    NexusBackgroundService.start(context.applicationContext)
                }
                WatchdogService.start(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to revive services on screen activity: ${e.message}", e)
            }
        }
    }
}
