package com.nexus.parent.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * ParentConnectionService.kt - Foreground service preserving WebRTC & auto-reconnect when the
 * parent's phone screen turns off.
 *
 * Hey friend! Unlike Desktop laptops where users usually leave monitors on, Android aggressively
 * freezes background network sockets within seconds of locking the screen. Running a lightweight
 * `dataSync` foreground service alongside a partial WakeLock keeps our WebRTC PeerConnection,
 * DataChannel heartbeats, and 9-attempt backoff timer uninterrupted even when locked in a pocket.
 */
class ParentConnectionService : Service() {

    companion object {
        private const val TAG = "ParentConnService"
        private const val ACTION_START_OR_UPDATE = "com.nexus.parent.action.START_OR_UPDATE"
        private const val ACTION_STOP = "com.nexus.parent.action.STOP"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_BODY = "extra_body"
        private const val WAKE_LOCK_TAG = "NexusParent::WebRtcConnectionWakeLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L

        fun startOrUpdate(context: Context, title: String, body: String) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, ParentConnectionService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure { err ->
                Log.w(TAG, "Unable to start/update foreground service: ${err.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, ParentConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                appCtx.stopService(intent)
            }.onFailure { err ->
                Log.w(TAG, "Unable to stop foreground service cleanly: ${err.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Nexus Parent Active"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: "Maintaining encrypted P2P session"
        val notification = ParentNotificationManager.buildNotification(this, title, body)

        if (!isForegroundStarted) {
            startForegroundCompat(notification)
            acquireWakeLock()
            isForegroundStarted = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(ParentNotificationManager.NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopForegroundCompat()
        isForegroundStarted = false
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        startForeground(
            ParentNotificationManager.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
            }
        }
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
        }
    }
}
