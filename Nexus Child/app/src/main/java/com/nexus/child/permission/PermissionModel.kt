package com.nexus.child.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nexus.child.R
import com.nexus.child.service.BatteryOptimizationManager

/**
 * PermissionType specifies every individual permission and capability required
 * by Nexus Child for complete, uninterrupted monitoring.
 *
 * Covers:
 * 1. Runtime permissions (Camera, Microphone, Location, Call Logs, Contacts,
 *    Phone, SMS, Photos & Videos, Music & Audio, Notifications).
 * 2. Special system privileges (All Files Access, Battery Optimization, Auto-Start,
 *    Accessibility Service, Notification Listener).
 */
enum class PermissionType(
    @get:StringRes val titleRes: Int,
    @get:StringRes val descriptionRes: Int,
    val categoryName: String,
    @get:DrawableRes val iconRes: Int,
    val isRuntimePermission: Boolean = false
) {
    // -------------------------------------------------------------------------
    // RUNTIME PERMISSIONS (Requested via standard Android OS dialogs)
    // -------------------------------------------------------------------------

    CAMERA(
        titleRes = R.string.perm_camera_title,
        descriptionRes = R.string.perm_camera_desc,
        categoryName = "A/V SURVEILLANCE",
        iconRes = R.drawable.ic_camera,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(Manifest.permission.CAMERA)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    MICROPHONE(
        titleRes = R.string.perm_mic_title,
        descriptionRes = R.string.perm_mic_desc,
        categoryName = "A/V SURVEILLANCE",
        iconRes = R.drawable.ic_mic,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(Manifest.permission.RECORD_AUDIO)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    LOCATION(
        titleRes = R.string.perm_location_title,
        descriptionRes = R.string.perm_location_desc,
        categoryName = "GEOLOCATION TELEMETRY",
        iconRes = R.drawable.ic_map_pin,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine && coarse
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    CALL_LOGS(
        titleRes = R.string.perm_call_logs_title,
        descriptionRes = R.string.perm_call_logs_desc,
        categoryName = "TELEPHONY & CALLS",
        iconRes = R.drawable.ic_phone_log,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(Manifest.permission.READ_CALL_LOG)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    CONTACTS(
        titleRes = R.string.perm_contacts_title,
        descriptionRes = R.string.perm_contacts_desc,
        categoryName = "CONTACTS TELEMETRY",
        iconRes = R.drawable.ic_contact,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(Manifest.permission.READ_CONTACTS)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    PHONE(
        titleRes = R.string.perm_phone_title,
        descriptionRes = R.string.perm_phone_desc,
        categoryName = "CELLULAR STATUS",
        iconRes = R.drawable.ic_phone,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            val phoneState = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            return phoneState
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE
            )
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    SMS(
        titleRes = R.string.perm_sms_title,
        descriptionRes = R.string.perm_sms_desc,
        categoryName = "COMMUNICATIONS",
        iconRes = R.drawable.ic_message_square,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            val readSms = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            val receiveSms = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            return readSms && receiveSms
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    PHOTOS_AND_VIDEOS(
        titleRes = R.string.perm_photos_videos_title,
        descriptionRes = R.string.perm_photos_videos_desc,
        categoryName = "MEDIA TELEMETRY",
        iconRes = R.drawable.ic_images,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val images = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                val video = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                images && video
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    MUSIC_AND_AUDIO(
        titleRes = R.string.perm_music_audio_title,
        descriptionRes = R.string.perm_music_audio_desc,
        categoryName = "MEDIA TELEMETRY",
        iconRes = R.drawable.ic_audio_lines,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    NOTIFICATIONS(
        titleRes = R.string.perm_notifications_runtime_title,
        descriptionRes = R.string.perm_notifications_runtime_desc,
        categoryName = "SYSTEM ALERTS",
        iconRes = R.drawable.ic_bell,
        isRuntimePermission = true
    ) {
        override fun isGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Granted automatically by OS prior to Android 13
            }
        }

        override fun getRequiredRuntimePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
        }

        override fun getSettingsIntent(context: Context): Intent {
            return PermissionHelper.createAppDetailsIntent(context)
        }
    },

    // -------------------------------------------------------------------------
    // SPECIAL SYSTEM PRIVILEGES (Configured in dedicated OS settings pages)
    // -------------------------------------------------------------------------

    ALL_FILES_ACCESS(
        titleRes = R.string.perm_storage_title,
        descriptionRes = R.string.perm_storage_desc,
        categoryName = "FILE SYSTEM ACCESS",
        iconRes = R.drawable.ic_folder,
        isRuntimePermission = false
    ) {
        override fun isGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
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
                PermissionHelper.createAppDetailsIntent(context)
            }
        }
    },

    BATTERY_OPTIMIZATION(
        titleRes = R.string.perm_battery_title,
        descriptionRes = R.string.perm_battery_desc,
        categoryName = "BACKGROUND POWER",
        iconRes = R.drawable.ic_battery,
        isRuntimePermission = false
    ) {
        override fun isGranted(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
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

    AUTO_START(
        titleRes = R.string.perm_autostart_title,
        descriptionRes = R.string.perm_autostart_desc,
        categoryName = "SYSTEM RESILIENCE",
        iconRes = R.drawable.ic_zap,
        isRuntimePermission = false
    ) {
        override fun isGranted(context: Context): Boolean {
            val prefs = context.getSharedPreferences("nexus_permission_prefs", Context.MODE_PRIVATE)
            val visited = prefs.getBoolean("autostart_granted", false)
            if (visited) return true

            // On devices without vendor-specific autostart managers (e.g. Pixel),
            // standard battery optimization exemption is sufficient.
            return if (!BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller()) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                false
            }
        }

        override fun getSettingsIntent(context: Context): Intent {
            // Mark granted/visited in preferences so user advances after configuring
            context.getSharedPreferences("nexus_permission_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("autostart_granted", true)
                .apply()

            return BatteryOptimizationManager.getAutoStartSettingsIntent(context)
        }
    },

    ACCESSIBILITY_SERVICE(
        titleRes = R.string.perm_accessibility_title,
        descriptionRes = R.string.perm_accessibility_desc,
        categoryName = "SYSTEM INTERCEPTION",
        iconRes = R.drawable.ic_accessibility,
        isRuntimePermission = false
    ) {
        override fun isGranted(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    },

    NOTIFICATION_LISTENER(
        titleRes = R.string.perm_notifications_title,
        descriptionRes = R.string.perm_notifications_desc,
        categoryName = "NOTIFICATION INTERCEPTION",
        iconRes = R.drawable.ic_bell,
        isRuntimePermission = false
    ) {
        override fun isGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.contains(context.packageName)
        }

        override fun getSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    };

    /**
     * Programmatically checks whether this permission is currently granted.
     */
    abstract fun isGranted(context: Context): Boolean

    /**
     * Resolves the Intent required to launch the corresponding Android system settings screen.
     */
    abstract fun getSettingsIntent(context: Context): Intent

    /**
     * Array of runtime permissions needed if [isRuntimePermission] is true.
     */
    open fun getRequiredRuntimePermissions(): Array<String> = emptyArray()
}
