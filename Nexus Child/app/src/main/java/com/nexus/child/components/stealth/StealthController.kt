package com.nexus.child.components.stealth

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.nexus.child.MainActivity
import com.nexus.child.widget.StealthWidgetProvider

/**
 * Production implementation of IStealthController.
 *
 * Hey friend! Here is how Stealth Mode works under the hood on Android:
 *
 * 1. Component State Mechanics:
 *    The Android launcher displays an app because its launcher Activity has an intent-filter
 *    with ACTION_MAIN and CATEGORY_LAUNCHER.
 *    By calling PackageManager.setComponentEnabledSetting() targeting MainActivity with
 *    COMPONENT_ENABLED_STATE_DISABLED, Android OS instructs all launcher packages to immediately
 *    remove the app icon from the home screen and app drawer.
 *
 * 2. Process Survival (DONT_KILL_APP):
 *    Normally, changing component state might prompt Android to terminate the application process.
 *    We pass PackageManager.DONT_KILL_APP so our persistent NexusBackgroundService and
 *    WatchdogService processes continue running in the background completely uninterrupted!
 *
 * 3. Default State Resolution:
 *    On a fresh install, getComponentEnabledSetting() returns COMPONENT_ENABLED_STATE_DEFAULT (0).
 *    In our manifest, MainActivity is enabled by default, so COMPONENT_ENABLED_STATE_DEFAULT
 *    means the app is visible (stealth mode = false).
 *
 * 4. Dual Persistence & Widget Broadcast:
 *    In addition to PackageManager, we persist the stealth flag in SharedPreferences so state
 *    reads can occur quickly, and we broadcast ACTION_APPWIDGET_UPDATE so the 1x1 home screen
 *    widget stays in sync.
 */
class StealthController(
    private val context: Context
) : IStealthController {

    companion object {
        private const val TAG = "StealthController"

        private const val PREFS_NAME = "nexus_stealth_prefs"
        private const val KEY_STEALTH_ACTIVE = "key_stealth_active"

        @Volatile
        private var sharedListener: ((isStealth: Boolean) -> Unit)? = null

        /**
         * Registers a global listener for stealth state transitions.
         * Used by NexusBackgroundService to capture state changes triggered by the 1x1 widget.
         */
        fun registerGlobalListener(listener: ((isStealth: Boolean) -> Unit)?) {
            sharedListener = listener
        }

        /**
         * Dispatches a state change event to the active global listener.
         */
        fun notifyGlobalStateChanged(isStealth: Boolean) {
            try {
                sharedListener?.invoke(isStealth)
            } catch (e: Exception) {
                Log.w(TAG, "Error invoking global stealth state listener: ${e.message}")
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var stateChangeListener: ((isStealth: Boolean) -> Unit)? = null

    /**
     * Determines whether the app is currently hidden from the launcher.
     */
    override fun isStealthMode(): Boolean {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            val state = pm.getComponentEnabledSetting(componentName)

            // COMPONENT_ENABLED_STATE_DISABLED (2) indicates stealth is active
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read launcher component state: ${e.message}", e)
            prefs.getBoolean(KEY_STEALTH_ACTIVE, false)
        }
    }

    /**
     * Sets stealth mode state by enabling or disabling the launcher component.
     */
    override fun setStealthMode(enable: Boolean): Boolean {
        val targetState = if (enable) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)

            Log.d(TAG, "Setting launcher component state to ${if (enable) "DISABLED (Stealth)" else "ENABLED (Visible)"}...")
            pm.setComponentEnabledSetting(
                componentName,
                targetState,
                PackageManager.DONT_KILL_APP
            )

            // Persist to SharedPreferences
            prefs.edit().putBoolean(KEY_STEALTH_ACTIVE, enable).apply()

            // Refresh 1x1 widget instances
            notifyWidgetUpdate()

            // Dispatch state change callbacks
            stateChangeListener?.invoke(enable)
            notifyGlobalStateChanged(enable)

            Log.d(TAG, "Stealth mode successfully updated to enable=$enable")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update stealth mode component state: ${e.message}", e)
            false
        }
    }

    /**
     * Toggles the current stealth mode state.
     */
    override fun toggleStealthMode(): Boolean {
        val currentlyHidden = isStealthMode()
        val newState = !currentlyHidden
        setStealthMode(newState)
        return newState
    }

    /**
     * Registers an instance listener for state changes.
     */
    override fun setOnStateChangeListener(listener: ((isStealth: Boolean) -> Unit)?) {
        stateChangeListener = listener
    }

    /**
     * Dispatches an update broadcast to refresh all instances of the 1x1 Stealth Control Widget.
     */
    private fun notifyWidgetUpdate() {
        try {
            val widgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, StealthWidgetProvider::class.java)
            val ids = widgetManager.getAppWidgetIds(component)

            if (ids != null && ids.isNotEmpty()) {
                val intent = Intent(context, StealthWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Dispatched widget update broadcast for ${ids.size} widget instances")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch widget update broadcast: ${e.message}")
        }
    }
}
