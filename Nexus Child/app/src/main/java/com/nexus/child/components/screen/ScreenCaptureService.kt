package com.nexus.child.components.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexus.child.R

/**
 * ScreenCaptureService runs as a foreground service with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.
 *
 * Hey friend! Here is why this service is built with this exact token handoff architecture:
 * 1. Starting with Android 14 (API 34), Google strictly enforces that MediaProjectionManager.getMediaProjection()
 *    or ScreenCapturerAndroid.startCapture() can ONLY be called while an active foreground service
 *    with type "mediaProjection" is ALREADY running.
 * 2. If startForegroundService() is called and ScreenController immediately attempts startCapture(),
 *    an asynchronous race condition occurs: startForeground() has not finished executing on the looper,
 *    causing Android 14 to throw a fatal SecurityException:
 *    "Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION".
 * 3. Solution:
 *    We pass the granted MediaProjection token Intent directly to ScreenCaptureService via start(context, resultCode, data).
 *    Inside onStartCommand(), we call ServiceCompat.startForeground() FIRST. Once Android system server has
 *    officially confirmed this process as an active mediaProjection foreground service, we THEN hand the token
 *    to ScreenController.onMediaProjectionGranted().
 *    This completely eliminates the race condition and guarantees 100% reliable initialization on Android 14+!
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "nexus_screen_capture_channel"
        private const val ACTION_START = "com.nexus.child.action.START_SCREEN_CAPTURE"
        private const val ACTION_STOP = "com.nexus.child.action.STOP_SCREEN_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        /**
         * Starts the foreground service and delivers the granted MediaProjection token once active.
         */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "ScreenCaptureService start command dispatched with token extras")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ScreenCaptureService: ${e.message}", e)
                ScreenController.onMediaProjectionDenied("Failed to start ScreenCaptureService: ${e.message}")
            }
        }

        /**
         * Stops the foreground service and dismisses the notification.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
                Log.d(TAG, "ScreenCaptureService stop command dispatched")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop ScreenCaptureService: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Received ACTION_STOP. Tearing down foreground service...")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting ScreenCaptureService as Foreground Service (type=mediaProjection)...")
        createNotificationChannel()
        val notification = buildNotification()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            Log.d(TAG, "ScreenCaptureService successfully active in foreground with mediaProjection type")

            // Now that foreground service is verified active, safely extract token and initialize ScreenController
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED

            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                Log.d(TAG, "Delivering verified MediaProjection token to ScreenController...")
                ScreenController.onMediaProjectionGranted(resultCode, resultData)
            } else {
                Log.w(TAG, "ScreenCaptureService onStartCommand missing valid token extras (code=$resultCode, data=${resultData != null})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            ScreenController.onMediaProjectionDenied("Failed to start mediaProjection foreground service: ${e.message}")
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nexus Screen Surveillance"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Protection")
            .setContentText("Screen surveillance active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
