package com.nexus.child.permission

import android.content.Context

/**
 * PermissionManager acts as the single source of truth for all device capabilities
 * and system permissions required by the Nexus Child monitoring agent.
 *
 * Coordinates both runtime Android OS permissions (Camera, Mic, Call Logs, Contacts,
 * Phone, SMS, Photos/Videos, Audio, Notifications) and special elevated privileges
 * (All Files, Battery, Accessibility, Notification Listener).
 */
object PermissionManager {

    /**
     * Complete list of all 17 managed permissions.
     */
    val allPermissions: List<PermissionType> = PermissionType.entries

    /**
     * Only runtime permissions (requested via OS dialogs).
     */
    val runtimePermissions: List<PermissionType> = allPermissions.filter { it.isRuntimePermission }

    /**
     * Only special permissions (configured in OS settings).
     */
    val specialPermissions: List<PermissionType> = allPermissions.filter { !it.isRuntimePermission }

    /**
     * Total number of required permissions.
     */
    fun getTotalCount(): Int = allPermissions.size

    /**
     * Counts how many permissions have been granted so far.
     */
    fun getGrantedCount(context: Context): Int {
        return allPermissions.count { it.isGranted(context) }
    }

    /**
     * Checks if all critical permissions have been successfully authorized.
     */
    fun areAllGranted(context: Context): Boolean {
        return allPermissions.all { it.isGranted(context) }
    }

    /**
     * Returns a list of permissions that still require user action.
     */
    fun getPendingPermissions(context: Context): List<PermissionType> {
        return allPermissions.filterNot { it.isGranted(context) }
    }

    /**
     * Collects all currently denied runtime permission strings into an array
     * for optional batch-requesting.
     */
    fun getAllPendingRuntimePermissions(context: Context): Array<String> {
        val deniedList = mutableListOf<String>()
        for (perm in runtimePermissions) {
            if (!perm.isGranted(context)) {
                deniedList.addAll(perm.getRequiredRuntimePermissions())
            }
        }
        return deniedList.distinct().toTypedArray()
    }

    /**
     * Checks whether a specific capability is authorized.
     */
    fun isPermissionGranted(context: Context, type: PermissionType): Boolean {
        return type.isGranted(context)
    }
}
