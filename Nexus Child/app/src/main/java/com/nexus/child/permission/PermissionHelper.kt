package com.nexus.child.permission

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * PermissionHelper provides robust utilities to navigate users directly to
 * device-specific permission editors (MIUI/HyperOS, ColorOS, FuntouchOS, OxygenOS, OneUI),
 * with reliable fallbacks to standard Android App Info screens.
 */
object PermissionHelper {

    /**
     * Builds a universal Intent opening this application's App Details screen.
     * This screen allows users to grant permissions, manage storage, and configure notifications.
     */
    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Attempts to navigate to vendor-customized permission manager screens
     * on custom Android skins (Xiaomi HyperOS, Vivo, Oppo, OnePlus).
     * Returns true if a customized intent was successfully dispatched.
     */
    fun openManufacturerSettings(context: Context): Boolean {
        val brand = Build.BRAND.lowercase()
        val packageName = context.packageName

        val candidateIntents = when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("mi") -> listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", packageName)
                }
            )

            brand.contains("vivo") -> listOf(
                Intent().apply {
                    setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    putExtra("packagename", packageName)
                }
            )

            brand.contains("oppo") || brand.contains("realme") -> listOf(
                Intent().apply {
                    setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.singlepage.PermissionSinglePageActivity")
                    putExtra("packageName", packageName)
                }
            )

            brand.contains("oneplus") -> listOf(
                Intent("com.oneplus.security.action.APP_PERM_EDITOR").apply {
                    setClassName("com.oneplus.security", "com.oneplus.security.permissions.AppPermissionsActivity")
                    putExtra("packageName", packageName)
                }
            )

            else -> emptyList()
        }

        for (intent in candidateIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // If vendor intent fails, continue to next fallback
            }
        }

        // Fallback to standard App Details screen
        try {
            context.startActivity(createAppDetailsIntent(context))
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Generates a clean tactical device badge title matching the industrial surveillance theme.
     * Example: "Target: Pixel 8 Pro" or "Target: SM-S918B"
     */
    fun getDeviceModelBadge(): String {
        val model = Build.MODEL ?: "Android Device"
        return "Target: $model"
    }
}
