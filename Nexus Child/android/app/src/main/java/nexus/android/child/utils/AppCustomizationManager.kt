package nexus.android.child.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit

/**
 * Manages app icon and name customization.
 * Uses activity aliases to switch between different icons and labels.
 */
object AppCustomizationManager {
    private const val TAG = "AppCustomizationManager"
    
    // Activity alias components for different icons
    private const val COMPONENT_MAIN = "com.android.child.MainActivity"
    private const val COMPONENT_ICON_GMAIL = "com.android.child.MainActivityIconGmail"
    private const val COMPONENT_ICON_WHATSAPP = "com.android.child.MainActivityIconWhatsApp"
    private const val COMPONENT_ICON_INSTAGRAM = "com.android.child.MainActivityIconInstagram"
    private const val COMPONENT_ICON_SETTINGS = "com.android.child.MainActivityIconSettings"
    private const val COMPONENT_ICON_YOUTUBE_MUSIC = "com.android.child.MainActivityIconYouTubeMusic"
    private const val COMPONENT_ICON_CALCULATOR = "com.android.child.MainActivityIconCalculator"
    private const val COMPONENT_ICON_TELEGRAM = "com.android.child.MainActivityIconTelegram"
    private const val COMPONENT_ICON_SECURITY = "com.android.child.MainActivityIconSecurity"
    private const val COMPONENT_ICON_CHATGPT = "com.android.child.MainActivityIconChatGPT"
    private const val COMPONENT_ICON_GOOGLE = "com.android.child.MainActivityIconGoogle"
    private const val COMPONENT_ICON_GAME_BOOSTER = "com.android.child.MainActivityIconGameBooster"
    private const val COMPONENT_ICON_GEMINI = "com.android.child.MainActivityIconGemini"
    private const val COMPONENT_ICON_GPAY = "com.android.child.MainActivityIconGPay"
    private const val COMPONENT_ICON_MAPS = "com.android.child.MainActivityIconMaps"
    private const val COMPONENT_ICON_MESSAGES = "com.android.child.MainActivityIconMessages"
    private const val COMPONENT_ICON_GOOGLE_ONE = "com.android.child.MainActivityIconGoogleOne"
    private const val COMPONENT_ICON_PLAY_STORE = "com.android.child.MainActivityIconPlayStore"
    private const val COMPONENT_ICON_SPOTIFY = "com.android.child.MainActivityIconSpotify"
    private const val COMPONENT_ICON_X = "com.android.child.MainActivityIconX"
    private const val COMPONENT_ICON_YOUTUBE = "com.android.child.MainActivityIconYouTube"
    
    // SharedPreferences keys
    private const val PREFS_NAME = "app_customization_prefs"
    private const val KEY_SELECTED_ICON = "selected_icon"
    
    // Icon identifiers - each icon has its corresponding name in the manifest
    enum class IconType(
        val componentName: String,
        val drawableRes: String,
    ) {
        DEFAULT(COMPONENT_MAIN, "ic_launcher"),
        GMAIL(COMPONENT_ICON_GMAIL, "ic_app_icon_gmail"),
        WHATSAPP(COMPONENT_ICON_WHATSAPP, "ic_app_icon_whatsapp"),
        INSTAGRAM(COMPONENT_ICON_INSTAGRAM, "ic_app_icon_instagram"),
        SETTINGS(COMPONENT_ICON_SETTINGS, "ic_app_icon_settings"),
        YOUTUBE_MUSIC(COMPONENT_ICON_YOUTUBE_MUSIC, "ic_app_icon_youtube_music"),
        CALCULATOR(COMPONENT_ICON_CALCULATOR, "ic_app_icon_calculator"),
        TELEGRAM(COMPONENT_ICON_TELEGRAM, "ic_app_icon_telegram"),
        SECURITY(COMPONENT_ICON_SECURITY, "ic_app_icon_security"),
        CHATGPT(COMPONENT_ICON_CHATGPT, "ic_app_icon_chatgpt"),
        GOOGLE(COMPONENT_ICON_GOOGLE, "ic_app_icon_google"),
        GAME_BOOSTER(COMPONENT_ICON_GAME_BOOSTER, "ic_app_icon_game_booster"),
        GEMINI(COMPONENT_ICON_GEMINI, "ic_app_icon_gemini"),
        GPAY(COMPONENT_ICON_GPAY, "ic_app_icon_gpay"),
        MAPS(COMPONENT_ICON_MAPS, "ic_app_icon_maps"),
        MESSAGES(COMPONENT_ICON_MESSAGES, "ic_app_icon_messages"),
        GOOGLE_ONE(COMPONENT_ICON_GOOGLE_ONE, "ic_app_icon_google_one"),
        PLAY_STORE(COMPONENT_ICON_PLAY_STORE, "ic_app_icon_play_store"),
        SPOTIFY(COMPONENT_ICON_SPOTIFY, "ic_app_icon_spotify"),
        X(COMPONENT_ICON_X, "ic_app_icon_x"),
        YOUTUBE(COMPONENT_ICON_YOUTUBE, "ic_app_icon_youtube")
    }
    
    /**
     * Changes the app icon to the specified type.
     * The app name will automatically change to match the icon.
     */
    fun changeAppIcon(context: Context, iconType: IconType): Boolean {
        return try {
            val pm = context.packageManager
            
            // Disable all icon aliases and main activity first
            IconType.entries.forEach { type ->
                try {
                    val component = ComponentName(context, type.componentName)
                    val currentState = pm.getComponentEnabledSetting(component)
                    if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to disable component ${type.componentName}", e)
                }
            }
            
            // Disable or enable the main activity based on icon type
            val mainActivity = ComponentName(context, COMPONENT_MAIN)
            if (iconType == IconType.DEFAULT) {
                // Enable main activity for default icon
                pm.setComponentEnabledSetting(
                    mainActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // Disable main activity and enable the selected icon alias
                pm.setComponentEnabledSetting(
                    mainActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                val component = ComponentName(context, iconType.componentName)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            
            // Save selection
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_SELECTED_ICON, iconType.name)
                }
            
            Log.d(TAG, "App icon and name changed to: ${iconType.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change app icon", e)
            false
        }
    }
    
    /**
     * Gets the currently selected icon type.
     */
    fun getCurrentIconType(context: Context): IconType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val iconName = prefs.getString(KEY_SELECTED_ICON, IconType.DEFAULT.name)
        return try {
            IconType.valueOf(iconName ?: IconType.DEFAULT.name)
        } catch (_: Exception) {
            IconType.DEFAULT
        }
    }
    
}

