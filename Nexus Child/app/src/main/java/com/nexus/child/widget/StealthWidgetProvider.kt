package com.nexus.child.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.nexus.child.R
import com.nexus.child.components.stealth.StealthController

/**
 * 1x1 Stealth Control Widget Provider for Nexus Child.
 *
 * Hey friend! Here is why this widget is so crucial for the app:
 * When Stealth Mode is activated, the app icon is hidden from the Android launcher and app drawer.
 * If the parent loses internet connectivity or cannot reach the phone, how does the user ever
 * unhide or configure the app again?
 *
 * Answer: The 1x1 Home Screen Widget!
 * Android widgets remain permanently pinned on the home screen even when the main app icon
 * is hidden from the app drawer.
 *
 * Tapping this 1x1 widget sends the broadcast ACTION_TOGGLE_STEALTH:
 * 1. Toggles stealth mode via StealthController.
 * 2. If previously hidden, re-enables MainActivity so the app icon reappears in the drawer.
 * 3. Shows a clear status Toast on screen.
 * 4. Automatically dispatches status telemetry to the parent console if WebRTC is connected!
 */
class StealthWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "StealthWidgetProvider"

        /**
         * Broadcast action triggered when the 1x1 widget icon is tapped.
         */
        const val ACTION_TOGGLE_STEALTH = "com.nexus.child.TOGGLE_STEALTH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE_STEALTH) {
            Log.d(TAG, "Received ACTION_TOGGLE_STEALTH from widget tap. Toggling stealth mode...")
            val controller = StealthController(context)
            val isNowHidden = controller.toggleStealthMode()

            // Display tactical feedback toast on device screen
            val toastMessage = if (isNowHidden) {
                context.getString(R.string.toast_stealth_activated)
            } else {
                context.getString(R.string.toast_stealth_deactivated)
            }
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Stealth mode toggled via widget: isNowHidden=$isNowHidden")

            // Refresh all widget instances
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StealthWidgetProvider::class.java))
            if (ids != null && ids.isNotEmpty()) {
                onUpdate(context, manager, ids)
            }
        }
    }

    /**
     * Binds click events and layout configuration to a single widget instance.
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_stealth)

        // Configure click intent to toggle stealth mode
        val toggleIntent = Intent(context, StealthWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_STEALTH
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
