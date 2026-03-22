package nexus.android.child.permissions

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object PermissionHelper {

    // --------------------------------------------------------
    // MAIN API CALLED FROM PermissionManager
    // --------------------------------------------------------
    fun openPermissionSettings(activity: Activity, deniedPermissions: List<String>? = null) {

        // If it's about location → FORCE App Info screen
        if (deniedPermissions?.any { it.contains("LOCATION") } == true) {
            openAppDetails(activity)
            return
        }

        val brand = Build.BRAND.lowercase()
        when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("mi") -> {
                if (openXiaomiPermissionEditor(activity)) return
                openAppDetails(activity)
            }

            brand.contains("vivo") -> {
                if (openVivoPermissionEditor(activity)) return
                openAppDetails(activity)
            }

            brand.contains("oppo") || brand.contains("realme") -> {
                if (openOppoPermissionEditor(activity)) return
                openAppDetails(activity)
            }

            brand.contains("oneplus") -> {
                if (openOnePlusPermissionEditor(activity)) return
                openAppDetails(activity)
            }

            else -> openAppDetails(activity)
        }
    }

    // --------------------------------------------------------
    // UNIVERSAL → Always succeeds, never redirected
    // --------------------------------------------------------
    private fun openAppDetails(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${activity.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Exception) { }
    }

    // --------------------------------------------------------
    // XIAOMI / HYPEROS
    // --------------------------------------------------------
    private fun openXiaomiPermissionEditor(activity: Activity): Boolean {
        return try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------------
    // VIVO
    // --------------------------------------------------------
    private fun openVivoPermissionEditor(activity: Activity): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                )
                putExtra("packagename", activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------------
    // OPPO / REALME
    // --------------------------------------------------------
    private fun openOppoPermissionEditor(activity: Activity): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.singlepage.PermissionSinglePageActivity"
                )
                putExtra("packageName", activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------------
    // ONEPLUS
    // --------------------------------------------------------
    private fun openOnePlusPermissionEditor(activity: Activity): Boolean {
        return try {
            val intent = Intent("com.oneplus.security.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.oneplus.security",
                    "com.oneplus.security.permissions.AppPermissionsActivity"
                )
                putExtra("packageName", activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
