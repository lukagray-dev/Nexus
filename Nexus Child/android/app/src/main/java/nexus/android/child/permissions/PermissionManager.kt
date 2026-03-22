package nexus.android.child.permissions

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView

import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import nexus.android.child.R
import nexus.android.child.utils.BatteryOptimizationHelper

/**
 * `PermissionManager` orchestrates the entire permission workflow for the app.
 * It first requests all required foreground permissions (camera, audio, location, etc.).
 * If the device runs Android Q or higher, it then requests background location access
 *   – but only after the fine location permission has been granted.
 * Finally, it handles special permissions such as ignoring battery optimizations
 *   and enabling the Accessibility Service, providing callbacks to report the
 *   overall permission outcome to the caller.
 */

class PermissionManager(private val activity: Activity) { // Activity context is needed for permission requests and dialogs
    companion object { // Request codes for permission requests
        const val REQUEST_FOREGROUND = 1001 // Request code for foreground permissions
        const val REQUEST_BACKGROUND = 1002 // Request code for background location permission
    }

    private val foregroundPermissions: List<String> = buildList {
        // Permissions specific to Android 13 (TIRAMISU) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // Permissions specific to Android Q (10) and above
        // ACCESS_MEDIA_LOCATION is relevant for Q+ including Tiramisu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            // READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE are effectively deprecated for broad access from R (Android 11)
            // They are primarily useful for Android Q (10) for broad access.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Permissions that are generally required across various Android versions
        add(Manifest.permission.CAMERA) // Camera permission
        add(Manifest.permission.RECORD_AUDIO) // Microphone permission
        add(Manifest.permission.ACCESS_FINE_LOCATION) // Fine location permission
        add(Manifest.permission.ACCESS_COARSE_LOCATION) // Coarse location permission
        add(Manifest.permission.READ_CALL_LOG) // Call log permission
        add(Manifest.permission.READ_SMS) // SMS permission
        add(Manifest.permission.READ_PHONE_STATE) // Phone state permission
        add(Manifest.permission.READ_CONTACTS) // Contacts permission
        
        // Digital Wellbeing permissions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            add(Manifest.permission.PACKAGE_USAGE_STATS) // PACKAGE_USAGE_STATS for older Android (pre-API 31)
        } else {
            add(Manifest.permission.QUERY_ALL_PACKAGES) // QUERY_ALL_PACKAGES for modern Android (API 31+)
        }
    }

    private var lastToastTime = 0L

    fun safeToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime < 1500) return   // prevent spam
        lastToastTime = now
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    }


    fun startPermissionFlow() {
        val denied = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, denied.toTypedArray(), REQUEST_FOREGROUND)
        } else {
            requestBackgroundLocationIfNeeded()
            // DO NOT request special permissions here
        }
    }



    fun showPermissionRationaleDialog(onGrant: () -> Unit, onCancel: () -> Unit) {
        val view: View = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_permission_rationale, null)

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(activity.getString(R.string.permissions_dialog_positive)) { _, _ ->
                onGrant()
            }
            .setNegativeButton(activity.getString(R.string.permissions_dialog_negative)) { dialogInterface, _ ->
                dialogInterface.dismiss()
                onCancel()
            }
            .setNeutralButton(activity.getString(R.string.permissions_dialog_neutral)) { dialogInterface, _ ->
                dialogInterface.dismiss()
                Toast.makeText(
                    activity,
                    activity.getString(R.string.permissions_learn_more_toast, activity.packageName),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .create()

        styleDialog(dialog)
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(activity, R.color.permissions_dialog_background).toDrawable()
        )
        dialog.show()
    }





    private fun isPermissionDenied(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val accentColor = ContextCompat.getColor(activity, R.color.permissions_icon_tint)
            val secondaryColor = ContextCompat.getColor(activity, R.color.permissions_text_secondary)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondaryColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondaryColor)
        }

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(activity, R.color.permissions_dialog_background).toDrawable()
        )
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Only for Android Q and above
            val fineLocGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED // Check if fine location is granted
            val bgGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED // Check if background location is granted
            if (fineLocGranted && !bgGranted) { // Only request if fine location is granted and background is not
                ActivityCompat.requestPermissions( // Request background location permission
                    activity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQUEST_BACKGROUND // Request code for background location
                )
            }
        }
    }

    fun requestSpecialPermissions() {
        // Build list of special permissions that still need to be granted
        val pending = mutableListOf<PermissionGuideType>()

        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity)) {
            pending.add(PermissionGuideType.BATTERY_OPTIMIZATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            pending.add(PermissionGuideType.ALL_FILES_ACCESS)
        }
        if (!isAccessibilityServiceEnabled(activity)) {
            pending.add(PermissionGuideType.ACCESSIBILITY_SERVICE)
        }
        if (!isNotificationServiceEnabled(activity)) {
            pending.add(PermissionGuideType.NOTIFICATION_LISTENER)
        }
        if (!isUnrestrictedDataEnabled()) {
            pending.add(PermissionGuideType.UNRESTRICTED_DATA)
        }
        if (BatteryOptimizationHelper.shouldShowAutoStartInstructions()) {
            pending.add(PermissionGuideType.AUTO_START)
        }

        if (pending.isNotEmpty()) {
            PermissionGuideActivity.start(activity, pending)
        }
    }

    // ---- Delegates to BatteryOptimizationHelper ----

    private fun showBatteryOptimizationDialog() { // Show custom dialog before requesting
        val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
        infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_battery)
        startIconPulse(infoView.findViewById(R.id.dialog_icon))
        infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_battery_title)
        infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_battery_message)

        val dialog = AlertDialog.Builder(activity)
            .setView(infoView)
            .setPositiveButton(R.string.dialog_grant) { _, _ -> // On "Grant", try to request
                try {
                    BatteryOptimizationHelper.requestDisableBatteryOptimization(activity) // Request to disable optimizations directly
                } catch (_: Exception) { // Catch any exception (e.g. ActivityNotFound)q
                    BatteryOptimizationHelper.openBatteryOptimizationSettings(activity) // Open settings directly
                }
            }
            .setNegativeButton(R.string.dialog_not_now) { dialog, _ -> // On "Not now", just dismiss
                dialog.dismiss() // User chose not to grant, just dismiss
            }
            .setCancelable(false) // Force user to choose
            .create()

        styleDialog(dialog)
        dialog.show() // Show the dialog
    }

    private fun requestAutoStartPermission() { // Some manufacturers require enabling auto-start manually
        if (BatteryOptimizationHelper.shouldShowAutoStartInstructions()) { // Check if we should show instructions
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_media_play)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_autostart_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_autostart_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ ->
                    BatteryOptimizationHelper.openAutoStartSettings(activity) // Open the auto-start settings screen
                }
                .setNegativeButton(R.string.dialog_not_now, null)
                .setCancelable(false)
                .create()

            styleDialog(dialog)
            dialog.show()
        }
    }

    private fun requestAccessibilityPermission() { // Show dialog to enable Accessibility Service
        if (!isAccessibilityServiceEnabled(activity)) { // Check if service is not already enabled
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(R.drawable.ic_accessibility)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_accessibility_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_accessibility_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ -> // On "Grant", open settings
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // Intent to open Accessibility settings
                    activity.startActivity(intent)
                }
                .setNegativeButton(R.string.dialog_cancel, null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .create()

            styleDialog(dialog)
            dialog.show() // Show the dialog
        }
    }

    private fun requestNotificationListenerPermission() { // Show dialog to enable Notification Listener
        if (!isNotificationServiceEnabled(activity)) { // Check if service is not already enabled
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
            infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_popup_reminder)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))
            infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_notification_title)
            infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_notification_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_grant) { _, _ ->
                    try { // On "Grant", open settings
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") // Intent to open Notification Listener settings
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // New task flag
                        activity.startActivity(intent) // Start the settings activity
                    } catch (e: Exception) { // Catch any exception (e.g. ActivityNotFound)
                        e.printStackTrace() // Log the exception
                        Toast.makeText(activity, R.string.toast_unable_to_open_settings, Toast.LENGTH_LONG).show() // Inform user
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null) // On "Cancel", do nothing
                .setCancelable(false) // Force user to choose
                .create()

            styleDialog(dialog)
            dialog.show() // Show the dialog
        }
    }

    /* Request unrestricted data access permission */

    private fun requestUnrestrictedDataPermission() {
    if (!isUnrestrictedDataEnabled()) {
        val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
        infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_menu_upload)
        startIconPulse(infoView.findViewById(R.id.dialog_icon))
        infoView.findViewById<TextView>(R.id.dialog_title).text = activity.getString(R.string.dialog_unrestricted_data_title)
        infoView.findViewById<TextView>(R.id.dialog_message).text = activity.getString(R.string.dialog_unrestricted_data_message)

        val dialog = AlertDialog.Builder(activity)
            .setView(infoView)
            .setPositiveButton(R.string.dialog_grant) { _, _ ->
                openUnrestrictedDataSettings()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .setCancelable(false)
            .create()

        styleDialog(dialog)
        dialog.show()
    }
}


    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return
            }
        } else {
            return
        }
        
        val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)
        infoView.findViewById<ImageView>(R.id.dialog_icon).setImageResource(android.R.drawable.ic_menu_save)
        startIconPulse(infoView.findViewById(R.id.dialog_icon))
        infoView.findViewById<TextView>(R.id.dialog_title).text =
            activity.getString(R.string.dialog_all_files_title)
        infoView.findViewById<TextView>(R.id.dialog_message).text =
            activity.getString(R.string.dialog_all_files_message)

        val dialog = AlertDialog.Builder(activity)
            .setView(infoView)
            .setPositiveButton(R.string.dialog_grant) { _, _ ->
                openAllFilesAccessSettings()
            }
            .setNegativeButton(R.string.dialog_not_now, null)
            .setCancelable(false)
            .create()

        styleDialog(dialog)
        dialog.show()
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${activity.packageName}".toUri()
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    activity,
                    R.string.toast_unable_to_open_settings,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Check if Notification Listener permission is granted.
     */
    private fun isNotificationServiceEnabled(context: Context): Boolean { // Check if our Notification Listener is enabled
        val packageName = context.packageName // Get our package name
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") // Get enabled listeners
        return flat?.contains(packageName) == true // Return true if our package is in the enabled listeners list
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean { // Check if our Accessibility Service is enabled
        val prefString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val packageName = context.packageName
        return prefString?.contains(packageName) == true // Return true if our package is in the enabled services list
    }

    /* ----------------------------------------------------------
    Unrestricted data settings  are not available on all devices.
    This method checks if unrestricted data access is enabled.
    If not, it opens the settings screen to allow the user to enable it.
    -------------------------------------------------------------- */

    private fun openUnrestrictedDataSettings() {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
            data = "package:${activity.packageName}".toUri()
        }
        activity.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(activity, "Unable to open unrestricted data settings", Toast.LENGTH_LONG).show()
    }
}

    // Add this function to check for unrestricted data access
    private fun isUnrestrictedDataEnabled(): Boolean {
        val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return when (connectivityManager.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> false
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED,
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
                -> true
            else -> true
        }
    }

    // ---- Permission results ----

    fun onRequestPermissionsResult(
        // Handle permission request results
        requestCode: Int, // Request code to identify which request
        callback: (allGranted: Boolean) -> Unit, // Callback to report if all permissions are granted
    ) {
        when (requestCode) {
            REQUEST_FOREGROUND -> {
                val stillDenied = foregroundPermissions.filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }

                if (stillDenied.isEmpty()) {
                    requestBackgroundLocationIfNeeded()
                    callback(allPermissionsGranted())
                } else {
                    handleDeniedPermissions(stillDenied)
                    callback(false)
                }
            }

            REQUEST_BACKGROUND -> {
                callback(allPermissionsGranted())
            }

            else -> callback(allPermissionsGranted())
        }

    }

    private fun handleDeniedPermissions(denied: List<String>) {

        val showRationale = denied.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }

        // ------------------------------
        // CASE 1 → User denied normally
        // ------------------------------
        if (showRationale) {
            val infoView = LayoutInflater.from(activity).inflate(R.layout.dialog_dark_info, null)

            infoView.findViewById<ImageView>(R.id.dialog_icon)
                .setImageResource(android.R.drawable.ic_dialog_alert)
            startIconPulse(infoView.findViewById(R.id.dialog_icon))

            infoView.findViewById<TextView>(R.id.dialog_title).text =
                activity.getString(R.string.dialog_permissions_needed_title)

            infoView.findViewById<TextView>(R.id.dialog_message).text =
                activity.getString(R.string.dialog_permissions_needed_message)

            val dialog = AlertDialog.Builder(activity)
                .setView(infoView)
                .setPositiveButton(R.string.dialog_retry) { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        denied.toTypedArray(),
                        REQUEST_FOREGROUND
                    )
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .setCancelable(false)
                .create()

            styleDialog(dialog)
            dialog.show()
            return
        }

        // -------------------------------------
        // CASE 2 → "Don't ask again" selected
        // -------------------------------------
        val deniedPermissionsString = denied.joinToString { it.substringAfterLast('.') }
        safeToast("Permissions denied permanently: $deniedPermissionsString. Enable them from settings.")
        Log.e("PermissionManager", "Permissions denied permanently: $deniedPermissionsString")

        // Use our OEM-aware helper (Xiaomi → direct Permission page)
        PermissionHelper.openPermissionSettings(activity)
    }


    private fun startIconPulse(vararg views: View?) {
        views.forEach { view ->
            view?.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.icon_pulse))
        }
    }

    fun allPermissionsGranted(): Boolean {
        // 1. Foreground runtime permissions
        for (p in foregroundPermissions) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // 2. Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        return true
    }

    fun allSpecialPermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) return false

        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(activity))
            return false

        if (!isAccessibilityServiceEnabled(activity))
            return false

        if (!isNotificationServiceEnabled(activity))
            return false

        if (!isUnrestrictedDataEnabled())
            return false

        return true
    }

}
/** * Note: This class assumes it is used within an Activity context.
 * It handles permission requests and results, including special permissions.
 * The caller (e.g. MainActivity) should delegate onRequestPermissionsResult to this class.
 * The overall permission status is reported via the provided callback.
 */