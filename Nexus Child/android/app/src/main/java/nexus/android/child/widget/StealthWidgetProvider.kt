package nexus.android.child.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import nexus.android.child.MainActivity
import nexus.android.child.R

/**
 * App Widget that toggles stealth mode (hide/unhide app from launcher).
 *
 * This widget persists in the home screen widget drawer even when the app
 * is hidden from the app drawer, providing a reliable way to unhide the app
 * if the parent connection fails.
 */
class StealthWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "StealthWidget"
        private const val ACTION_TOGGLE_STEALTH = "nexus.android.child.TOGGLE_STEALTH"
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
            toggleStealth(context)

            // Refresh all widget instances
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StealthWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_stealth)

        // Set click to toggle stealth
        val toggleIntent = Intent(context, StealthWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_STEALTH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun toggleStealth(context: Context) {
        val pm = context.packageManager
        val mainComponent = ComponentName(context, MainActivity::class.java)

        val currentState = pm.getComponentEnabledSetting(mainComponent)
        val isHidden = currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        try {
            if (isHidden) {
                // Unhide: re-enable the launcher activity
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Toast.makeText(context, context.getString(R.string.toast_stealth_off), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "App is now visible (stealth off)")
            } else {
                // Hide: disable the launcher activity
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Toast.makeText(context, context.getString(R.string.toast_stealth_on), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "App is now hidden (stealth on)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle stealth mode", e)
        }
    }
}
