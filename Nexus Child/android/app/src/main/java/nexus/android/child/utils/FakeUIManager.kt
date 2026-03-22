package nexus.android.child.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Manages fake UI state for disguised app icons.
 * Stores whether the user wants to show fake UI for each icon type.
 */
object FakeUIManager {
    private const val PREFS_NAME = "fake_ui_prefs"
    private const val KEY_FAKE_UI_ENABLED = "fake_ui_enabled_"
    private const val KEY_FAKE_UI_LAST_UNLOCK_TIME = "fake_ui_last_unlock_time"
    const val UNLOCK_PRESS_DURATION_MS = 10000L // 10 seconds

    /**
     * Checks if fake UI is enabled for the given icon type.
     */
    fun isFakeUIEnabled(context: Context, iconType: AppCustomizationManager.IconType): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FAKE_UI_ENABLED + iconType.name, false)
    }

    /**
     * Sets fake UI enabled state for the given icon type.
     */
    fun setFakeUIEnabled(context: Context, iconType: AppCustomizationManager.IconType, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_FAKE_UI_ENABLED + iconType.name, enabled)
        }
    }

    /**
     * Records the last time the user unlocked the real app via secret gesture.
     */
    fun recordUnlockTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_FAKE_UI_LAST_UNLOCK_TIME, System.currentTimeMillis())
        }
    }
}
