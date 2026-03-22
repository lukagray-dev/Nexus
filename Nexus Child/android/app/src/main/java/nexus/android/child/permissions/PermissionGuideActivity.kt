@file:Suppress("DEPRECATION")

package nexus.android.child.permissions

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import nexus.android.child.R
import nexus.android.child.utils.BatteryOptimizationHelper
import androidx.core.content.edit

/**
 * Guided permission activity that shows a looping screen recording video
 * to walk the user through granting a specific special permission.
 *
 * When the user returns from the settings screen (onResume), the activity
 * automatically checks if the permission was granted. If granted, it moves
 * to the next pending permission or finishes with RESULT_OK.
 *
 * Usage:
 *   PermissionGuideActivity.start(activity, listOf(
 *       PermissionGuideType.BATTERY_OPTIMIZATION,
 *       PermissionGuideType.ACCESSIBILITY,
 *       ...
 *   ))
 */
class PermissionGuideActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PermissionGuide"
        private const val EXTRA_PERMISSION_TYPES = "extra_permission_types"
        private const val EXTRA_CURRENT_INDEX = "extra_current_index"

        /**
         * Launch the guided permission flow for the given list of permission types.
         */
        fun start(activity: android.app.Activity, types: List<PermissionGuideType>) {
            if (types.isEmpty()) return
            val intent = Intent(activity, PermissionGuideActivity::class.java)
            intent.putExtra(EXTRA_PERMISSION_TYPES, ArrayList(types.map { it.name }))
            activity.startActivity(intent)
        }
    }

    private lateinit var videoView: VideoView
    private lateinit var titleText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var stepIndicator: TextView
    private lateinit var openSettingsButton: MaterialButton
    private lateinit var skipButton: MaterialButton
    private lateinit var grantedIcon: ImageView
    private lateinit var grantedText: TextView
    private lateinit var guideContainer: View

    private var permissionTypes: List<PermissionGuideType> = emptyList()
    private var currentIndex = 0
    private var hasNavigatedToSettings = false

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isCurrentPermissionGranted()) {
                onPermissionGranted()
            } else {
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)

        // Parse permission types from intent
        val typeNames = intent.getStringArrayListExtra(EXTRA_PERMISSION_TYPES) ?: run {
            finish()
            return
        }
        permissionTypes = typeNames.mapNotNull {
            try { PermissionGuideType.valueOf(it) } catch (_: Exception) { null }
        }
        currentIndex = savedInstanceState?.getInt(EXTRA_CURRENT_INDEX, 0) ?: 0

        if (permissionTypes.isEmpty()) {
            finish()
            return
        }

        initViews()
        showCurrentPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_CURRENT_INDEX, currentIndex)
    }

    override fun onResume() {
        super.onResume()
        if (hasNavigatedToSettings) {
            // Start polling for permission grant
            handler.post(pollRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun initViews() {
        videoView = findViewById(R.id.guide_video)
        titleText = findViewById(R.id.guide_title)
        descriptionText = findViewById(R.id.guide_description)
        stepIndicator = findViewById(R.id.guide_step_indicator)
        openSettingsButton = findViewById(R.id.guide_open_settings_button)
        skipButton = findViewById(R.id.guide_skip_button)
        grantedIcon = findViewById(R.id.guide_granted_icon)
        grantedText = findViewById(R.id.guide_granted_text)
        guideContainer = findViewById(R.id.guide_content_container)

        openSettingsButton.setOnClickListener {
            openSettingsForCurrentPermission()
        }

        skipButton.setOnClickListener {
            moveToNext()
        }
    }

    private fun showCurrentPermission() {
        if (currentIndex >= permissionTypes.size) {
            setResult(RESULT_OK)
            finish()
            return
        }

        // Skip already-granted permissions
        if (isCurrentPermissionGranted()) {
            moveToNext()
            return
        }

        val type = permissionTypes[currentIndex]
        hasNavigatedToSettings = false

        // Update UI
        titleText.text = getString(type.titleRes)
        descriptionText.text = getString(type.descriptionRes)
        stepIndicator.text = getString(R.string.guide_step_indicator, currentIndex + 1, permissionTypes.size)

        // Show guide content, hide granted state
        guideContainer.visibility = View.VISIBLE
        grantedIcon.visibility = View.GONE
        grantedText.visibility = View.GONE
        openSettingsButton.visibility = View.VISIBLE
        skipButton.visibility = View.VISIBLE

        // Setup video
        setupVideo(type)
    }

    private fun setupVideo(type: PermissionGuideType) {
        val videoResId = type.videoRawRes
        if (videoResId != 0) {
            try {
                val videoUri = "android.resource://$packageName/$videoResId".toUri()
                videoView.setVideoURI(videoUri)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f) // Mute
                    videoView.start()
                }
                videoView.setOnErrorListener { _, _, _ ->
                    Log.w(TAG, "Video playback error for ${type.name}")
                    videoView.visibility = View.GONE
                    true
                }
                videoView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.w(TAG, "Could not load video for ${type.name}", e)
                videoView.visibility = View.GONE
            }
        } else {
            videoView.visibility = View.GONE
        }
    }

    private fun openSettingsForCurrentPermission() {
        val type = permissionTypes[currentIndex]
        hasNavigatedToSettings = true

        try {
            val intent = type.getSettingsIntent(this)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings for ${type.name}", e)
            Toast.makeText(this, R.string.toast_unable_to_open_settings, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCurrentPermissionGranted(): Boolean {
        if (currentIndex >= permissionTypes.size) return true
        return permissionTypes[currentIndex].isGranted(this)
    }

    private fun onPermissionGranted() {
        handler.removeCallbacks(pollRunnable)

        // Show granted state briefly
        guideContainer.visibility = View.VISIBLE
        videoView.stopPlayback()
        videoView.visibility = View.GONE
        openSettingsButton.visibility = View.GONE
        skipButton.visibility = View.GONE
        grantedIcon.visibility = View.VISIBLE
        grantedText.visibility = View.VISIBLE

        // Auto-advance after a brief delay
        handler.postDelayed({
            moveToNext()
        }, 1200)
    }

    private fun moveToNext() {
        currentIndex++
        if (currentIndex >= permissionTypes.size) {
            setResult(RESULT_OK)
            finish()
        } else {
            showCurrentPermission()
        }
    }
}

/**
 * Defines each special permission that requires a guided flow.
 * Each type maps to:
 *  - A title and description for the guide screen
 *  - A raw video resource (0 if no video is available yet)
 *  - A method to check if granted
 *  - A method to get the settings intent
 */
enum class PermissionGuideType(
    val titleRes: Int,
    val descriptionRes: Int,
    val videoRawRes: Int // 0 = no video available
) {
    BATTERY_OPTIMIZATION(
        R.string.guide_battery_title,
        R.string.guide_battery_desc,
        0 // Replace with R.raw.guide_battery when video is added
    ) {
        override fun isGranted(context: Context): Boolean {
            return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        }
        override fun getSettingsIntent(context: Context): Intent {
            return try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        }
    },

    ALL_FILES_ACCESS(
        R.string.guide_all_files_title,
        R.string.guide_all_files_desc,
        0
    ) {
        override fun isGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }
        override fun getSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                } catch (_: Exception) {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            }
        }
    },

    ACCESSIBILITY_SERVICE(
        R.string.guide_accessibility_title,
        R.string.guide_accessibility_desc,
        0
    ) {
        override fun isGranted(context: Context): Boolean {
            val prefString = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return prefString?.contains(context.packageName) == true
        }
        override fun getSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    },

    NOTIFICATION_LISTENER(
        R.string.guide_notification_title,
        R.string.guide_notification_desc,
        0
    ) {
        override fun isGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }
        override fun getSettingsIntent(context: Context): Intent {
            return Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    },

    UNRESTRICTED_DATA(
        R.string.guide_data_title,
        R.string.guide_data_desc,
        0
    ) {
        override fun isGranted(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return when (cm.restrictBackgroundStatus) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> false
                else -> true
            }
        }
        override fun getSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
    },

    AUTO_START(
        R.string.guide_autostart_title,
        R.string.guide_autostart_desc,
        0
    ) {
        override fun isGranted(context: Context): Boolean {
            // Auto-start cannot be reliably queried on most OEMs.
            // We mark it as "granted" after the user visits settings once.
            val prefs = context.getSharedPreferences("permission_guide", Context.MODE_PRIVATE)
            return prefs.getBoolean("autostart_visited", false)
        }
        override fun getSettingsIntent(context: Context): Intent {
            // Mark as visited since we can't programmatically check
            context.getSharedPreferences("permission_guide", Context.MODE_PRIVATE)
                .edit {putBoolean("autostart_visited", true) }

            // Try manufacturer-specific auto-start intents
            val autoStartIntents = listOf(
                Intent("miui.intent.action.OP_AUTO_START").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                },
                Intent("huawei.intent.action.STARTUP_MANAGER").apply {
                    setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                },
                Intent("oppo.intent.action.OP_AUTO_START").apply {
                    setClassName("com.coloros.safecenter", "com.coloros.safecenter.sysclear.ui.mainpage.MainActivity")
                },
                Intent("vivo.intent.action.VIVO_AUTO_START").apply {
                    setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                },
                Intent("samsung.intent.action.AUTO_START").apply {
                    setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                }
            )

            for (intent in autoStartIntents) {
                if (intent.resolveActivity(context.packageManager) != null) {
                    return intent
                }
            }

            // Fallback to app details settings
            return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
    };

    abstract fun isGranted(context: Context): Boolean
    abstract fun getSettingsIntent(context: Context): Intent
}
