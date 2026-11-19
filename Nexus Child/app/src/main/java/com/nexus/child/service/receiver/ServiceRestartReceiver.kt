package com.nexus.child.service.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.watchdog.WatchdogService

/**
 * ServiceRestartReceiver is triggered via AlarmManager or onTaskRemoved to resurrect
 * NexusBackgroundService if it was terminated unexpectedly.
 *
 * Hey friend! Here is why this restart receiver is so resilient:
 * 1. Post-Death Alarm: When onTaskRemoved fires, an immediate alarm (250-500ms) is dispatched.
 * 2. Proactive Heartbeat Watchdog: While NexusBackgroundService is running, it continually schedules
 *    a standing hardware alarm 20-30 seconds into the future. If a sudden SIGKILL (like MIUI OneKeyClean)
 *    terminates the process without calling onDestroy, the alarm timer in the Linux RTC kernel will
 *    fire and awaken this receiver!
 * 3. Mutual Process Revival: Upon firing, this receiver resurrects BOTH NexusBackgroundService
 *    and the WatchdogService (:watchdog) daemon.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART = "com.nexus.child.action.RESTART_SERVICE"
        private const val REQUEST_CODE_RESTART = 991
        private const val REQUEST_CODE_WATCHDOG = 992

        /**
         * Schedules a one-shot wake-up alarm to resurrect the background service.
         *
         * @param context Application or service context.
         * @param delayMs Delay in milliseconds before firing the alarm.
         */
        fun scheduleRestart(context: Context, delayMs: Long = 500L) {
            setAlarm(context, REQUEST_CODE_RESTART, delayMs)
            Log.d(TAG, "Scheduled immediate restart alarm to fire in ${delayMs}ms")
        }

        /**
         * Schedules a proactive watchdog alarm.
         * While the service is running, it keeps refreshing this alarm 20-30 seconds in advance.
         * If the process is killed unexpectedly, this alarm wakes up the app automatically!
         */
        fun scheduleHeartbeatWatchdog(context: Context, delayMs: Long = 25_000L) {
            setAlarm(context, REQUEST_CODE_WATCHDOG, delayMs)
        }

        private fun setAlarm(context: Context, requestCode: Int, delayMs: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                    action = ACTION_RESTART
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = System.currentTimeMillis() + delayMs

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } catch (_: SecurityException) {
                        // Fallback if exact alarm permission is restricted on Android 12+
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm (code=$requestCode): ${e.message}", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_RESTART) {
            Log.d(TAG, "Received restart broadcast. Reviving Nexus monitoring engine...")
            try {
                NexusBackgroundService.start(context.applicationContext)
                WatchdogService.start(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start services from restart receiver: ${e.message}", e)
            }
        }
    }
}
