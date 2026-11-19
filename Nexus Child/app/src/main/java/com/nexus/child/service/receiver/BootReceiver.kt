package com.nexus.child.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.scheduler.PersistentJobService
import com.nexus.child.service.watchdog.WatchdogService

/**
 * BootReceiver intercepts system boot and application update events.
 *
 * Hey friend! Here is why this receiver is critical:
 * Whenever the child restarts or powers on their phone, Android broadcasts ACTION_BOOT_COMPLETED.
 * This receiver catches that event and boots NexusBackgroundService in the background,
 * plus schedules our persistent 15-minute watchdog job so monitoring never sleeps.
 *
 * Supported Actions:
 * - android.intent.action.BOOT_COMPLETED: Standard device boot.
 * - android.intent.action.LOCKED_BOOT_COMPLETED: Boot completed on Direct Boot devices before user unlocks.
 * - android.intent.action.QUICKBOOT_POWERON: Fast-boot mode used by Qualcomm/HTC chips.
 * - com.htc.intent.action.QUICKBOOT_POWERON: HTC vendor quickboot.
 * - android.intent.action.MY_PACKAGE_REPLACED: App was updated via APK or app store.
 * - android.intent.action.PACKAGE_REPLACED: Package update fallback.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received system boot/update event: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "System ready. Starting Nexus services & scheduling watchdog...")
                try {
                    NexusBackgroundService.start(context.applicationContext)
                    WatchdogService.start(context.applicationContext)
                    PersistentJobService.schedule(context.applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling boot startup: ${e.message}", e)
                }
            }
        }
    }
}
