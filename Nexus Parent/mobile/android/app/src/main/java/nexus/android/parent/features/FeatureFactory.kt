package nexus.android.parent.features

import android.content.Context
import nexus.android.parent.features.camera.CameraFeature
import nexus.android.parent.features.screen.ScreenFeature
import nexus.android.parent.features.location.LocationFeature
import nexus.android.parent.features.sms.SmsFeature
import nexus.android.parent.features.calllog.CallLogFeature
import nexus.android.parent.features.mic.MicFeature
import nexus.android.parent.features.notifications.NotificationsFeature
import nexus.android.parent.features.chats.ChatsFeature
import nexus.android.parent.features.gmail.GmailFeature
import nexus.android.parent.features.files.FilesFeature
import nexus.android.parent.features.wellbeing.WellbeingFeature
import nexus.android.parent.features.applock.AppLockFeature
import nexus.android.parent.features.wallpaper.WallpaperFeature
import nexus.android.parent.features.vibrateflash.VibrateFlashFeature
import nexus.android.parent.features.whatsapp.WhatsAppFeature
import nexus.android.parent.features.facebook.FacebookFeature
import nexus.android.parent.features.instagram.InstagramFeature
import nexus.android.parent.features.messenger.MessengerFeature
import nexus.android.parent.features.snapchat.SnapchatFeature
import nexus.android.parent.features.telegram.TelegramFeature
import nexus.android.parent.features.chatgpt.ChatGPTFeature
import nexus.android.parent.features.claude.ClaudeFeature
import nexus.android.parent.features.grok.GrokFeature
import nexus.android.parent.managers.FeatureType

/**
 * FeatureFactory - Factory class for creating feature instances
 * Centralizes feature creation and dependency injection
 * Now supports 23 features matching desktop app
 * Features are cached as singletons to persist data across panel open/close
 */
object FeatureFactory {

    // Cache feature instances to persist data
    private val featureCache = mutableMapOf<FeatureType, BaseFeature>()

    /**
     * Create or retrieve a cached feature instance based on feature type
     * Features are singletons to maintain state across panel open/close
     */
    fun createFeature(context: Context, featureType: FeatureType): BaseFeature {
        return featureCache.getOrPut(featureType) {
            when (featureType) {
                // Core Features
                FeatureType.CAMERA -> CameraFeature(context)
                FeatureType.SCREEN -> ScreenFeature(context)
                FeatureType.LOCATION -> LocationFeature(context)
                FeatureType.SMS -> SmsFeature(context)
                FeatureType.CALLLOG -> CallLogFeature(context)
                FeatureType.MIC -> MicFeature(context)
                FeatureType.NOTIFICATIONS -> NotificationsFeature(context)
                FeatureType.CHATS -> ChatsFeature(context)
                FeatureType.GMAIL -> GmailFeature(context)
                FeatureType.FILES -> FilesFeature(context)
                FeatureType.WELLBEING -> WellbeingFeature(context)
                FeatureType.APPLOCK -> AppLockFeature(context)
                FeatureType.WALLPAPER -> WallpaperFeature(context)
                FeatureType.VIBRATEFLASH -> VibrateFlashFeature(context)
                // Social Media
                FeatureType.WHATSAPP -> WhatsAppFeature(context)
                FeatureType.FACEBOOK -> FacebookFeature(context)
                FeatureType.INSTAGRAM -> InstagramFeature(context)
                FeatureType.MESSENGER -> MessengerFeature(context)
                FeatureType.SNAPCHAT -> SnapchatFeature(context)
                FeatureType.TELEGRAM -> TelegramFeature(context)
                // AI Assistants
                FeatureType.CHATGPT -> ChatGPTFeature(context)
                FeatureType.CLAUDE -> ClaudeFeature(context)
                FeatureType.GROK -> GrokFeature(context)
            }
        }
    }

    /**
     * Clear all cached features (useful for cleanup)
     */
    fun clearCache() {
        featureCache.values.forEach { it.onStop() }
        featureCache.clear()
    }

    /**
     * Clear a specific feature from cache
     */
    fun clearFeature(featureType: FeatureType) {
        featureCache[featureType]?.onStop()
        featureCache.remove(featureType)
    }

    /**
     * Get a cached feature instance if it exists
     */
    fun getFeature(featureType: FeatureType): BaseFeature? {
        return featureCache[featureType]
    }
}
