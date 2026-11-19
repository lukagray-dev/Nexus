package com.nexus.child.service.scheduler

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.watchdog.WatchdogService

/**
 * PersistentJobService acts as a periodic watchdog scheduler for Nexus Child.
 *
 * Hey friend! Here is why this JobService is our reliable fallback safety net:
 * Android's JobScheduler has special system privileges. When a job is registered with
 * setPersisted(true), Android stores the job schedule in an internal XML database in system_server.
 *
 * Even if the app is killed, the phone is rebooted, or the app process is force-stopped,
 * the Android OS JobScheduler will periodically wake up this JobService (every 15 minutes minimum).
 * When it runs, it inspects NexusBackgroundService.isRunning() and revives it if needed!
 */
class PersistentJobService : JobService() {

    companion object {
        private const val TAG = "PersistentJobService"
        const val JOB_ID = 8802
        private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes (Android system minimum)

        /**
         * Schedules the periodic watchdog job with JobScheduler.
         */
        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as? JobScheduler
                val component = ComponentName(context, PersistentJobService::class.java)

                val jobInfo = JobInfo.Builder(JOB_ID, component)
                    .setPersisted(true) // Survives device reboots (requires RECEIVE_BOOT_COMPLETED)
                    .setPeriodic(PERIODIC_INTERVAL_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Fires on WiFi or Cellular
                    .build()

                val result = scheduler?.schedule(jobInfo)
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.d(TAG, "PersistentJobService scheduled successfully (period=15m)")
                } else {
                    Log.w(TAG, "Failed to schedule PersistentJobService (result code=$result)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling PersistentJobService: ${e.message}", e)
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Watchdog job awakened. Verifying Nexus services status...")
        try {
            if (!NexusBackgroundService.isRunning()) {
                Log.d(TAG, "NexusBackgroundService is down! Reviving from JobService...")
                NexusBackgroundService.start(applicationContext)
            } else {
                Log.d(TAG, "NexusBackgroundService is already healthy and running")
            }
            WatchdogService.start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/reviving service from JobService: ${e.message}", e)
        }
        // Return false to indicate that work is finished synchronously
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Watchdog job interrupted prematurely")
        // Return true so Android will reschedule this job execution
        return true
    }
}
