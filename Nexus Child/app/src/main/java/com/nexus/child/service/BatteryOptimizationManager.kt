package com.nexus.child.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

/**
 * BatteryOptimizationManager handles battery optimization exemption checks and
 * OEM-specific autostart management.
 *
 * Hey friend! Many Android OEMs (like Xiaomi, Samsung, Oppo, Vivo, and Huawei) ship custom OS skins
 * containing aggressive task-killers. Even if an app starts a standard Android Foreground Service,
 * these OEM systems will kill background processes unless the user explicitly grants them
 * "Auto-Start" and "Unrestricted Battery" permissions!
 *
 * This manager provides:
 * 1. Checks for whether battery optimization is ignored.
 * 2. System intents to request battery optimization exemption directly.
 * 3. OEM-specific autostart manager launch intents that automatically probe vendor activities
 *    (MIUI Security Center, Huawei Startup Manager, Oppo SafeCenter, Vivo BgStartUp, Samsung Battery).
 * 4. Fallback to standard Application Details Settings when running on generic Android devices.
 */
object BatteryOptimizationManager {

    private const val TAG = "BatteryOptimizationMgr"

    /**
     * Checks if Nexus Child is already whitelisted from standard Android battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Directly prompts the user with the system dialog to ignore battery optimizations for this app.
     * Uses Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS with a package URI.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Dispatched ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch battery optimization dialog: ${e.message}", e)
            openGeneralBatterySettings(context)
        }
    }

    /**
     * Opens the overall system Battery Optimization list screen as a fallback.
     */
    fun openGeneralBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened general battery optimization settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch general battery settings: ${e.message}", e)
        }
    }

    /**
     * Resolves the best available Auto-Start settings intent across various OEM vendor skins.
     *
     * How this works:
     * We define explicit component intents for known OEM autostart activities in priority order.
     * We query PackageManager using resolveActivity(). If the target vendor component exists
     * on the child's phone, we return that direct intent. If none match, we fall back to
     * ACTION_APPLICATION_DETAILS_SETTINGS where the user can manage permissions manually.
     */
    fun getAutoStartSettingsIntent(context: Context): Intent {
        val oemIntents = listOf(
            // Xiaomi / Redmi / Poco (MIUI & HyperOS)
            Intent("miui.intent.action.OP_AUTO_START").setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Huawei / Honor (EMUI / MagicOS)
            Intent("huawei.intent.action.STARTUP_MANAGER").setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            // Oppo / Realme (ColorOS / Realme UI)
            Intent("oppo.intent.action.OP_AUTO_START").setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.sysclear.ui.mainpage.MainActivity"
            ),
            // Vivo / iQOO (Funtouch OS & OriginOS)
            Intent("vivo.intent.action.VIVO_AUTO_START").setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // Samsung (One UI Device Care / Battery)
            Intent("samsung.intent.action.AUTO_START").setClassName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )

        val pm = context.packageManager
        for (intent in oemIntents) {
            if (intent.resolveActivity(pm) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Log.d(TAG, "Found matching OEM autostart activity: ${intent.component?.className}")
                return intent
            }
        }

        // Generic Android fallback (App Details in system settings)
        Log.d(TAG, "No OEM autostart activity matched; falling back to App Details settings")
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks if the device manufacturer belongs to an OEM brand known for aggressive background killing.
     * Allows passing a custom manufacturer string for testing and deterministic evaluation.
     */
    fun isPopularOemWithAggressiveTaskKiller(manufacturer: String = Build.MANUFACTURER ?: ""): Boolean {
        val m = manufacturer.lowercase()
        return m in listOf(
            "xiaomi", "redmi", "poco",
            "huawei", "honor",
            "oppo", "realme",
            "vivo", "iqoo",
            "samsung",
            "oneplus"
        )
    }
}
