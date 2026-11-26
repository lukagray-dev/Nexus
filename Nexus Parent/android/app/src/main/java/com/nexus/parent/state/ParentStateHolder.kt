package com.nexus.parent.state

import android.net.Uri
import com.nexus.parent.data.DeviceStateCoordinator
import com.nexus.parent.data.monitor.CombinedPattern
import com.nexus.parent.data.monitor.FlashAction
import com.nexus.parent.data.monitor.VibratePattern
import com.nexus.parent.data.storage.StorageProtocol
import com.nexus.parent.webrtc.VideoFeedType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sin

/**
 * ParentStateHolder.kt - Central UI & session state controller for Nexus Parent Android.
 *
 * Hey friend! Works in two modes cleanly:
 * - Production mode (`coordinator != null`): delegates Connect, Disconnect, Reconnect, Stealth,
 *   Telemetry updates, and Settings persistence to `DeviceStateCoordinator` + `WebRtcManager`.
 * - Standalone UI/Unit-Test mode (`coordinator == null`): runs deterministic synchronous state
 *   transitions so all JVM unit tests and Compose instrumentation tests run with zero network setup.
 */
class ParentStateHolder(
    initialState: ParentUiState = ParentUiState(),
    private val coordinator: DeviceStateCoordinator? = null
) {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // SHELL & DRAWER NAVIGATION
    // -------------------------------------------------------------------------

    fun setDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = isOpen, isDevicePopoverOpen = false) }
    }

    fun toggleDrawer() {
        _uiState.update {
            it.copy(
                isDrawerOpen = !it.isDrawerOpen,
                isDevicePopoverOpen = false
            )
        }
    }

    fun setDrawerSearchQuery(query: String) {
        _uiState.update { it.copy(drawerSearchQuery = query) }
    }

    fun toggleSectionCollapsed(sectionId: SidebarSectionId) {
        _uiState.update { state ->
            val updated = if (sectionId in state.collapsedSections) {
                state.collapsedSections - sectionId
            } else {
                state.collapsedSections + sectionId
            }
            state.copy(collapsedSections = updated)
        }
    }

    fun toggleDevicePopover() {
        _uiState.update { it.copy(isDevicePopoverOpen = !it.isDevicePopoverOpen) }
    }

    fun closeDevicePopover() {
        _uiState.update { it.copy(isDevicePopoverOpen = false) }
    }

    // -------------------------------------------------------------------------
    // PAIRING & CONNECTION FLOW (UI-ONLY PLACEHOLDER TRANSITIONS)
    // -------------------------------------------------------------------------

    fun updatePairingDigits(rawInput: String) {
        val clean = FeatureRegistry.sanitizeChildIdDigits(rawInput)
        _uiState.update {
            it.copy(
                pairingInputDigits = clean,
                pairingErrorMessage = null,
                pairingStatusMessage = when {
                    clean.isEmpty() -> null
                    clean.length < 12 -> "${12 - clean.length} more digits needed"
                    else -> "Valid 12-digit ID. Ready to connect."
                }
            )
        }
    }

    fun clearPairingDigits() {
        _uiState.update {
            it.copy(
                pairingInputDigits = "",
                pairingErrorMessage = null,
                pairingStatusMessage = null
            )
        }
    }

    fun connectToDevice(digitsOverride: String? = null, fromHistoryId: String? = null) {
        if (coordinator != null) {
            val target = digitsOverride ?: _uiState.value.pairingInputDigits
            coordinator.connectFromUi(target, fromHistoryId = fromHistoryId)
        } else {
            submitPairingPreview(digitsOverride)
        }
    }

    fun submitPairingPreview(digitsOverride: String? = null) {
        val current = _uiState.value
        val digits = FeatureRegistry.sanitizeChildIdDigits(digitsOverride ?: current.pairingInputDigits)
        if (!FeatureRegistry.isValidChildId(digits)) {
            _uiState.update {
                it.copy(pairingErrorMessage = "Please enter a valid 12-digit Child ID.")
            }
            return
        }

        val formatted = FeatureRegistry.formatChildId(digits)
        val matchedTemplate = current.pairedDevicesHistory.firstOrNull { it.rawId == digits }
            ?: current.lastPairedDevice?.takeIf { it.rawId == digits }
        val connectedRecord = DeviceRecord(
            rawId = digits,
            formattedId = formatted,
            name = matchedTemplate?.name ?: "Child Android Device",
            model = matchedTemplate?.model ?: "Android Device",
            lastSyncedLabel = "Online • Just now",
            lastConnectedAt = System.currentTimeMillis(),
            telemetry = matchedTemplate?.telemetry ?: DeviceTelemetry()
        )
        val updatedHistory = listOf(connectedRecord) +
            current.pairedDevicesHistory.filterNot { it.rawId == digits }

        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.CONNECTED,
                pairingInputDigits = digits,
                pairingErrorMessage = null,
                pairingStatusMessage = null,
                connectingProgressMessage = null,
                connectingFromHistoryId = null,
                connectedDevice = connectedRecord,
                lastPairedDevice = connectedRecord,
                pairedDevicesHistory = updatedHistory,
                isDevicePopoverOpen = false,
                toastMessage = "Connected to $formatted"
            )
        }
    }

    fun disconnectDevice() {
        if (coordinator != null) {
            coordinator.disconnectManual()
        } else {
            applyDisconnectedState(toastMessage = "Device disconnected")
        }
    }

    fun retryReconnectNow() {
        coordinator?.retryReconnectNow()
    }

    fun cancelReconnection() {
        if (coordinator != null) {
            coordinator.cancelReconnect()
        } else {
            applyDisconnectedState(toastMessage = "Reconnection cancelled")
        }
    }

    // -------------------------------------------------------------------------
    // COORDINATOR LIFECYCLE CALLBACKS (LIVE WEBRTC, TELEMETRY & RECONNECT)
    // -------------------------------------------------------------------------

    fun onConnectingStarted(
        digits: String,
        progressMessage: String,
        fromHistoryId: String?,
        preserveReconnectingPhase: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                connectionPhase = if (preserveReconnectingPhase) ConnectionPhase.RECONNECTING else ConnectionPhase.CONNECTING,
                pairingInputDigits = if (fromHistoryId == null) digits else state.pairingInputDigits,
                pairingErrorMessage = null,
                connectingProgressMessage = progressMessage,
                connectingFromHistoryId = fromHistoryId
            )
        }
    }

    fun onConnectingProgress(progressMessage: String) {
        _uiState.update { it.copy(connectingProgressMessage = progressMessage) }
    }

    fun onConnectionEstablished(
        record: DeviceRecord,
        history: List<DeviceRecord>,
        toastMessage: String?
    ) {
        val isSwitchingDevice = _uiState.value.connectedDevice?.rawId?.let { it != record.rawId } ?: false
        val (usageLabel, usageFraction) = StorageProtocol.formatStorageCapacityStrip(
            record.telemetry.freeStorageBytes,
            record.telemetry.totalStorageBytes
        )
        _uiState.update { state ->
            state.copy(
                connectionPhase = ConnectionPhase.CONNECTED,
                pairingInputDigits = record.rawId,
                pairingErrorMessage = null,
                pairingStatusMessage = null,
                connectingProgressMessage = null,
                connectingFromHistoryId = null,
                connectedDevice = record,
                lastPairedDevice = record,
                pairedDevicesHistory = history,
                activeFeature = if (isSwitchingDevice) null else state.activeFeature,
                storage = if (isSwitchingDevice) {
                    StorageUiState(storageUsageText = usageLabel, storageUsedPercent = usageFraction)
                } else {
                    state.storage.copy(storageUsageText = usageLabel, storageUsedPercent = usageFraction)
                },
                isDevicePopoverOpen = false,
                toastMessage = toastMessage ?: state.toastMessage
            )
        }
    }

    fun onConnectingFailed(errorMessage: String?, isAutoReconnectAttempt: Boolean) {
        _uiState.update { state ->
            state.copy(
                connectionPhase = if (isAutoReconnectAttempt) ConnectionPhase.RECONNECTING else ConnectionPhase.DISCONNECTED,
                connectingProgressMessage = null,
                connectingFromHistoryId = null,
                pairingErrorMessage = errorMessage ?: state.pairingErrorMessage,
                pairingStatusMessage = if (errorMessage != null) null else state.pairingStatusMessage
            )
        }
    }

    fun onConnectionDroppedReconnecting(toastMessage: String?) {
        _uiState.update { state ->
            state.copy(
                connectionPhase = ConnectionPhase.RECONNECTING,
                connectingProgressMessage = null,
                connectingFromHistoryId = null,
                activeFeature = null,
                liveFeatures = emptySet(),
                camera = CameraFeedUiState(lens = state.settings.preferredCameraLens),
                screen = ScreenFeedUiState(quality = state.settings.screenQuality),
                mic = MicUiState(selectedVoice = state.settings.defaultVoicePreset),
                location = LocationUiState(),
                storage = StorageUiState(),
                activeCallAlert = null,
                toastMessage = toastMessage ?: state.toastMessage
            )
        }
    }

    fun onReconnectTick(attempt: Int, maxAttempts: Int, secondsRemaining: Int) {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.RECONNECTING,
                reconnectAttempt = attempt,
                reconnectMaxAttempts = maxAttempts,
                reconnectSecondsRemaining = secondsRemaining
            )
        }
    }

    fun onTelemetryUpdated(record: DeviceRecord, history: List<DeviceRecord>) {
        val (usageLabel, usageFraction) = StorageProtocol.formatStorageCapacityStrip(
            record.telemetry.freeStorageBytes,
            record.telemetry.totalStorageBytes
        )
        _uiState.update { state ->
            state.copy(
                connectedDevice = record,
                lastPairedDevice = record,
                pairedDevicesHistory = history,
                storage = state.storage.copy(
                    storageUsageText = usageLabel,
                    storageUsedPercent = usageFraction
                ),
                stealthStatus = when {
                    state.stealthStatus == StealthStatus.WAIT -> StealthStatus.WAIT
                    record.telemetry.isStealthMode -> StealthStatus.ACTIVE
                    else -> StealthStatus.ARMED
                }
            )
        }
    }

    fun onStealthCommandDispatched() {
        _uiState.update {
            it.copy(
                stealthStatus = StealthStatus.WAIT,
                showStealthConfirmDialog = false
            )
        }
    }

    fun onStealthStatusSynced(status: StealthStatus, toastMessage: String? = null) {
        _uiState.update {
            it.copy(
                stealthStatus = status,
                toastMessage = toastMessage ?: it.toastMessage
            )
        }
    }

    fun onHistoryCleared() {
        _uiState.update {
            it.copy(
                lastPairedDevice = if (it.connectionPhase == ConnectionPhase.CONNECTED) it.connectedDevice else null,
                pairedDevicesHistory = if (it.connectionPhase == ConnectionPhase.CONNECTED && it.connectedDevice != null) {
                    listOf(it.connectedDevice)
                } else {
                    emptyList()
                },
                selectedPairedDeviceId = null,
                toastMessage = "Paired device history cleared"
            )
        }
    }

    fun applyDisconnectedState(toastMessage: String?) {
        _uiState.update { state ->
            state.copy(
                connectionPhase = ConnectionPhase.DISCONNECTED,
                connectedDevice = null,
                connectingProgressMessage = null,
                connectingFromHistoryId = null,
                isDevicePopoverOpen = false,
                activeFeature = null,
                liveFeatures = emptySet(),
                camera = CameraFeedUiState(lens = state.settings.preferredCameraLens),
                screen = ScreenFeedUiState(quality = state.settings.screenQuality),
                mic = MicUiState(selectedVoice = state.settings.defaultVoicePreset),
                location = LocationUiState(),
                storage = StorageUiState(),
                monitor = MonitorPanelsUiState(),
                activeCallAlert = null,
                stealthStatus = StealthStatus.ARMED,
                reconnectAttempt = 0,
                reconnectSecondsRemaining = 0,
                toastMessage = toastMessage ?: state.toastMessage
            )
        }
    }

    fun updateMonitor(transform: (MonitorPanelsUiState) -> MonitorPanelsUiState) {
        _uiState.update { state ->
            state.copy(monitor = transform(state.monitor))
        }
    }

    fun setMonitorFeatureState(
        featureId: FeatureId,
        nextState: MonitoringState,
        isLiveBadge: Boolean
    ) {
        _uiState.update { state ->
            val m = state.monitor
            val nextMonitor = when (featureId) {
                FeatureId.SMS -> m.copy(smsState = nextState)
                FeatureId.CALLLOG -> m.copy(callLogState = nextState)
                FeatureId.NOTIFICATION -> m.copy(notificationState = nextState)
                FeatureId.KEYBOARD -> m.copy(keyboardState = nextState)
                FeatureId.WELLBEING -> m.copy(wellbeing = m.wellbeing.copy(monitoringState = nextState))
                FeatureId.APPLOCK -> m.copy(appLock = m.appLock.copy(monitoringState = nextState))
                FeatureId.WALLPAPER -> m.copy(wallpaper = m.wallpaper.copy(monitoringState = nextState))
                else -> m
            }
            val updatedLive = if (isLiveBadge) {
                state.liveFeatures + featureId
            } else {
                state.liveFeatures - featureId
            }
            state.copy(monitor = nextMonitor, liveFeatures = updatedLive)
        }
    }

    fun resetMonitorConnections() {
        val monitorIds = setOf(
            FeatureId.SMS,
            FeatureId.CALLLOG,
            FeatureId.NOTIFICATION,
            FeatureId.KEYBOARD,
            FeatureId.WELLBEING,
            FeatureId.APPLOCK,
            FeatureId.WALLPAPER
        )
        _uiState.update { state ->
            val m = state.monitor
            state.copy(
                monitor = m.copy(
                    smsState = MonitoringState.IDLE,
                    callLogState = MonitoringState.IDLE,
                    notificationState = MonitoringState.IDLE,
                    keyboardState = MonitoringState.IDLE,
                    wellbeing = m.wellbeing.copy(monitoringState = MonitoringState.IDLE),
                    appLock = m.appLock.copy(monitoringState = MonitoringState.IDLE),
                    wallpaper = m.wallpaper.copy(
                        monitoringState = MonitoringState.IDLE,
                        isFetching = false,
                        isPushing = false,
                        pushProgressPercent = 0
                    )
                ),
                liveFeatures = state.liveFeatures - monitorIds
            )
        }
    }

    // -------------------------------------------------------------------------
    // FEATURE ROUTING
    // -------------------------------------------------------------------------

    fun selectFeature(featureId: FeatureId) {
        if (featureId == FeatureId.MIC) {
            _uiState.update {
                it.copy(
                    mic = it.mic.copy(isOverlayOpen = !it.mic.isOverlayOpen, isVoiceMenuExpanded = false),
                    isDrawerOpen = false
                )
            }
            return
        }

        var switchedToStorage = false
        _uiState.update { state ->
            val nextActive = if (state.activeFeature == featureId) null else featureId
            switchedToStorage = nextActive == FeatureId.STORAGE && state.connectionPhase == ConnectionPhase.CONNECTED
            state.copy(
                activeFeature = nextActive,
                isDrawerOpen = false,
                isDevicePopoverOpen = false
            )
        }

        if (switchedToStorage) {
            coordinator?.storageCoordinator?.openStorageMonitor()
        }
    }

    fun closeActiveFeature() {
        _uiState.update { it.copy(activeFeature = null) }
    }

    // -------------------------------------------------------------------------
    // CAMERA & SCREEN LIVE SURVEILLANCE CONTROLS
    // -------------------------------------------------------------------------

    fun updateCameraState(transform: (CameraFeedUiState) -> CameraFeedUiState) {
        _uiState.update { it.copy(camera = transform(it.camera)) }
    }

    fun updateScreenState(transform: (ScreenFeedUiState) -> ScreenFeedUiState) {
        _uiState.update { it.copy(screen = transform(it.screen)) }
    }

    fun updateLocationState(transform: (LocationUiState) -> LocationUiState) {
        _uiState.update { it.copy(location = transform(it.location)) }
    }

    fun setFeatureLive(featureId: FeatureId, isLive: Boolean) {
        _uiState.update { state ->
            val updated = if (isLive) state.liveFeatures + featureId else state.liveFeatures - featureId
            state.copy(liveFeatures = updated)
        }
    }

    fun toggleCameraStream() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.toggleCameraStream()
            return
        }
        var nextState = MonitoringState.IDLE
        _uiState.update { state ->
            val next = if (state.camera.state == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            nextState = next
            val liveSet = if (next == MonitoringState.LIVE) {
                state.liveFeatures + FeatureId.CAMERA - FeatureId.SCREEN
            } else {
                state.liveFeatures - FeatureId.CAMERA
            }
            state.copy(
                camera = state.camera.copy(
                    state = next,
                    dvrProgress = 1f,
                    isAtLiveEdge = true,
                    elapsedLabel = if (next == MonitoringState.LIVE) "00:01" else "00:00"
                ),
                screen = if (next == MonitoringState.LIVE && state.screen.state == MonitoringState.LIVE) {
                    state.screen.copy(state = MonitoringState.IDLE, elapsedLabel = "00:00")
                } else {
                    state.screen
                },
                liveFeatures = liveSet
            )
        }
        @Suppress("UNUSED_VARIABLE")
        val unused = nextState
    }

    fun rotateCameraFeed() {
        _uiState.update { state ->
            val nextDeg = (state.camera.rotationDegrees + 90) % 360
            state.copy(camera = state.camera.copy(rotationDegrees = nextDeg))
        }
    }

    fun toggleCameraLens() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.toggleCameraLens()
            return
        }
        _uiState.update { state ->
            val nextLens = if (state.camera.lens == CameraLens.REAR) CameraLens.FRONT else CameraLens.REAR
            state.copy(
                camera = state.camera.copy(lens = nextLens),
                toastMessage = "Camera lens switched to ${nextLens.label}"
            )
        }
    }

    fun scrubCameraDvr(fraction: Float) {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.scrubDvr(VideoFeedType.CAMERA, fraction)
            return
        }
        val clamped = fraction.coerceIn(0f, 1f)
        val atLive = clamped >= 0.98f
        _uiState.update {
            it.copy(
                camera = it.camera.copy(
                    dvrProgress = if (atLive) 1f else clamped,
                    isAtLiveEdge = atLive
                )
            )
        }
    }

    fun jumpCameraToLive() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.jumpToLive(VideoFeedType.CAMERA)
            return
        }
        _uiState.update {
            it.copy(camera = it.camera.copy(dvrProgress = 1f, isAtLiveEdge = true))
        }
    }

    fun toggleCameraFullscreen() {
        _uiState.update {
            it.copy(camera = it.camera.copy(isFullscreen = !it.camera.isFullscreen))
        }
    }

    fun exportCameraDvrClip() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.exportActiveDvrClip(VideoFeedType.CAMERA)
        } else {
            showToast("Connect a child device and stream Camera to export DVR clip")
        }
    }

    fun toggleScreenStream() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.toggleScreenStream()
            return
        }
        _uiState.update { state ->
            val next = if (state.screen.state == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            val liveSet = if (next == MonitoringState.LIVE) {
                state.liveFeatures + FeatureId.SCREEN - FeatureId.CAMERA
            } else {
                state.liveFeatures - FeatureId.SCREEN
            }
            state.copy(
                screen = state.screen.copy(
                    state = next,
                    dvrProgress = 1f,
                    isAtLiveEdge = true,
                    elapsedLabel = if (next == MonitoringState.LIVE) "00:01" else "00:00"
                ),
                camera = if (next == MonitoringState.LIVE && state.camera.state == MonitoringState.LIVE) {
                    state.camera.copy(state = MonitoringState.IDLE, elapsedLabel = "00:00")
                } else {
                    state.camera
                },
                liveFeatures = liveSet
            )
        }
    }

    fun rotateScreenFeed() {
        _uiState.update { state ->
            val nextDeg = (state.screen.rotationDegrees + 90) % 360
            state.copy(screen = state.screen.copy(rotationDegrees = nextDeg))
        }
    }

    fun toggleSystemAudio() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.toggleSystemAudio()
            return
        }
        _uiState.update { state ->
            val next = !state.screen.isSystemAudioEnabled
            state.copy(
                screen = state.screen.copy(isSystemAudioEnabled = next),
                toastMessage = if (next) "System audio enabled" else "System audio muted"
            )
        }
    }

    fun scrubScreenDvr(fraction: Float) {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.scrubDvr(VideoFeedType.SCREEN, fraction)
            return
        }
        val clamped = fraction.coerceIn(0f, 1f)
        val atLive = clamped >= 0.98f
        _uiState.update {
            it.copy(
                screen = it.screen.copy(
                    dvrProgress = if (atLive) 1f else clamped,
                    isAtLiveEdge = atLive
                )
            )
        }
    }

    fun jumpScreenToLive() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.jumpToLive(VideoFeedType.SCREEN)
            return
        }
        _uiState.update {
            it.copy(screen = it.screen.copy(dvrProgress = 1f, isAtLiveEdge = true))
        }
    }

    fun toggleScreenFullscreen() {
        _uiState.update {
            it.copy(screen = it.screen.copy(isFullscreen = !it.screen.isFullscreen))
        }
    }

    fun exportScreenDvrClip() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.exportActiveDvrClip(VideoFeedType.SCREEN)
        } else {
            showToast("Connect a child device and stream Screen to export DVR clip")
        }
    }

    fun onDvrSegmentPosition(feedType: VideoFeedType, offsetMs: Int) {
        coordinator?.surveillanceCoordinator?.onDvrSegmentPlaybackPosition(feedType, offsetMs)
    }

    fun onDvrSegmentCompleted(feedType: VideoFeedType) {
        coordinator?.surveillanceCoordinator?.onDvrSegmentCompleted(feedType)
    }

    fun completeCreateDocumentExport(uri: Uri?) {
        coordinator?.surveillanceCoordinator?.completeCreateDocumentExport(uri)
    }

    // -------------------------------------------------------------------------
    // FLOATING MICROPHONE & VOICE INTERCOM HUD
    // -------------------------------------------------------------------------

    fun updateMicState(transform: (MicUiState) -> MicUiState) {
        _uiState.update { state ->
            state.copy(mic = transform(state.mic))
        }
    }

    fun setActiveCallAlert(alert: ActiveCallAlert?) {
        _uiState.update { it.copy(activeCallAlert = alert) }
    }

    fun applyPersistedSettingsSnapshot(settings: SettingsValues, toastMessage: String? = null) {
        _uiState.update { state ->
            state.copy(
                settings = settings,
                toastMessage = toastMessage ?: state.toastMessage
            )
        }
    }

    fun setMicOverlayOpen(open: Boolean) {
        if (coordinator != null) {
            coordinator.micCoordinator.setOverlayOpen(open)
            return
        }
        _uiState.update {
            it.copy(mic = it.mic.copy(isOverlayOpen = open, isVoiceMenuExpanded = false))
        }
    }

    fun toggleMicListen() {
        if (coordinator != null) {
            coordinator.micCoordinator.toggleListen()
            return
        }
        _uiState.update { state ->
            val next = if (state.mic.listenState == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            val bars = if (next == MonitoringState.LIVE) {
                List(36) { idx -> (0.25f + 0.65f * kotlin.math.abs(sin(idx * 0.45))).toFloat() }
            } else {
                List(36) { 0.12f }
            }
            val liveSet = if (next == MonitoringState.LIVE) {
                state.liveFeatures + FeatureId.MIC
            } else {
                state.liveFeatures - FeatureId.MIC
            }
            state.copy(
                mic = state.mic.copy(listenState = next, spectrumBars = bars),
                liveFeatures = liveSet
            )
        }
    }

    fun toggleMicSpeak(hasRecordAudioPermission: Boolean = true) {
        if (coordinator != null) {
            coordinator.micCoordinator.toggleSpeak(hasRecordAudioPermission)
            return
        }
        _uiState.update { state ->
            val next = if (state.mic.speakState == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            state.copy(mic = state.mic.copy(speakState = next, isVoiceMenuExpanded = false))
        }
    }

    fun onRecordAudioPermissionResult(granted: Boolean) {
        coordinator?.refreshAudioDevices()
        coordinator?.micCoordinator?.onRecordAudioPermissionResult(granted)
    }

    fun updateAudioDevices(inputs: List<AudioDeviceOption>, outputs: List<AudioDeviceOption>) {
        _uiState.update {
            it.copy(
                availableAudioInputs = inputs,
                availableAudioOutputs = outputs
            )
        }
    }

    fun refreshAudioDevices() {
        coordinator?.refreshAudioDevices()
    }

    fun toggleVoiceMenu() {
        _uiState.update {
            it.copy(mic = it.mic.copy(isVoiceMenuExpanded = !it.mic.isVoiceMenuExpanded))
        }
    }

    fun selectVoicePreset(preset: VoicePresetId) {
        if (coordinator != null) {
            coordinator.micCoordinator.selectVoicePreset(preset)
            return
        }
        _uiState.update {
            it.copy(
                mic = it.mic.copy(selectedVoice = preset, isVoiceMenuExpanded = false),
                settings = it.settings.copy(defaultVoicePreset = preset),
                toastMessage = "Voice preset: ${preset.displayName}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // LOCATION & STORAGE UI CONTROLS
    // -------------------------------------------------------------------------

    fun toggleLocationTracking() {
        if (coordinator != null) {
            coordinator.surveillanceCoordinator.toggleLocationTracking()
            return
        }
        _uiState.update { state ->
            val next = if (state.location.state == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            val liveSet = if (next == MonitoringState.LIVE) {
                state.liveFeatures + FeatureId.LOCATION
            } else {
                state.liveFeatures - FeatureId.LOCATION
            }
            state.copy(
                location = state.location.copy(
                    state = next,
                    address = if (next == MonitoringState.LIVE) "Awaiting child GPS telemetry fix..." else "Tracking paused",
                    coordinatesLabel = if (next == MonitoringState.LIVE) "-- , --" else "--",
                    lastPingLabel = if (next == MonitoringState.LIVE) "Live" else "--"
                ),
                liveFeatures = liveSet
            )
        }
    }

    fun centerLocationTarget() {
        _uiState.update { state ->
            state.copy(
                location = state.location.copy(
                    centerRequestToken = state.location.centerRequestToken + 1L
                )
            )
        }
    }

    fun zoomLocationIn() {
        _uiState.update { state ->
            state.copy(
                location = state.location.copy(
                    zoomDeltaRequestId = state.location.zoomDeltaRequestId + 1L,
                    zoomDeltaSteps = 1
                )
            )
        }
    }

    fun zoomLocationOut() {
        _uiState.update { state ->
            state.copy(
                location = state.location.copy(
                    zoomDeltaRequestId = state.location.zoomDeltaRequestId + 1L,
                    zoomDeltaSteps = -1
                )
            )
        }
    }

    fun toggleMapLayer() {
        _uiState.update { state ->
            val next = if (state.location.mapLayer == MapLayerMode.SATELLITE) {
                MapLayerMode.DARK_STREETS
            } else {
                MapLayerMode.SATELLITE
            }
            state.copy(
                location = state.location.copy(mapLayer = next),
                toastMessage = "Map layer: ${next.label}"
            )
        }
    }

    fun updateStorageState(transform: (StorageUiState) -> StorageUiState) {
        _uiState.update { state ->
            state.copy(storage = transform(state.storage))
        }
    }

    fun setStorageSearchQuery(query: String) {
        _uiState.update { it.copy(storage = it.storage.copy(searchQuery = query)) }
    }

    fun selectStorageCategory(category: StorageCategory) {
        _uiState.update { it.copy(storage = it.storage.copy(selectedCategory = category)) }
    }

    fun selectStorageItem(item: StorageFileItem?) {
        if (coordinator != null) {
            coordinator.storageCoordinator.selectItem(item)
            return
        }
        _uiState.update { it.copy(storage = it.storage.copy(selectedItem = item)) }
    }

    fun openStorageDirectory(item: StorageFileItem) {
        if (!item.isDirectory) return
        if (coordinator != null) {
            coordinator.storageCoordinator.openDirectory(item)
            return
        }
        _uiState.update { state ->
            state.copy(
                storage = state.storage.copy(
                    currentPath = item.path,
                    breadcrumbs = StorageProtocol.buildBreadcrumbs(item.path),
                    selectedItem = null
                )
            )
        }
    }

    fun refreshStorageDirectory() {
        if (coordinator != null) {
            coordinator.storageCoordinator.refreshCurrentDirectory()
        } else {
            showToast("Directory refresh queued")
        }
    }

    fun navigateStorageBreadcrumb(index: Int) {
        if (coordinator != null) {
            coordinator.storageCoordinator.navigateToBreadcrumb(index)
            return
        }
        _uiState.update { state ->
            val clamped = index.coerceIn(0, state.storage.breadcrumbs.lastIndex.coerceAtLeast(0))
            val targetPath = state.storage.breadcrumbs.getOrNull(clamped)?.fullPath
                ?: StorageProtocol.DEFAULT_STORAGE_ROOT
            state.copy(
                storage = state.storage.copy(
                    currentPath = targetPath,
                    breadcrumbs = state.storage.breadcrumbs.take(clamped + 1),
                    selectedItem = null
                )
            )
        }
    }

    fun navigateStorageUp() {
        if (coordinator != null) {
            coordinator.storageCoordinator.navigateUp()
            return
        }
        _uiState.update { state ->
            if (state.storage.breadcrumbs.size <= 1) return@update state
            val nextBreadcrumbs = state.storage.breadcrumbs.dropLast(1)
            state.copy(
                storage = state.storage.copy(
                    currentPath = nextBreadcrumbs.lastOrNull()?.fullPath
                        ?: StorageProtocol.DEFAULT_STORAGE_ROOT,
                    breadcrumbs = nextBreadcrumbs,
                    selectedItem = null
                )
            )
        }
    }

    fun loadMoreStorageFiles() {
        if (coordinator != null) {
            coordinator.storageCoordinator.loadMore()
        } else {
            showToast("Pagination requires active child connection")
        }
    }

    fun loadStorageImagePreview(item: StorageFileItem) {
        if (coordinator != null) {
            coordinator.storageCoordinator.loadFullImagePreview(item)
        } else {
            showToast("Full image preview requires active child connection")
        }
    }

    fun closeStorageImagePreview() {
        _uiState.update { state ->
            state.copy(
                storage = state.storage.copy(
                    imagePreviewItem = null,
                    imagePreviewLocalPath = null
                )
            )
        }
    }

    fun downloadPreviewedStorageImage() {
        if (coordinator != null) {
            coordinator.storageCoordinator.downloadPreviewedImage()
        } else {
            showToast("Image download requires active child connection")
        }
    }

    fun requestStorageDownload(item: StorageFileItem) {
        if (coordinator != null) {
            coordinator.storageCoordinator.requestDownload(item)
        } else {
            showToast("Download queued: ${item.name}")
        }
    }

    fun completeStorageDownloadPick(uri: Uri?) {
        coordinator?.storageCoordinator?.completeCreateDocumentSelection(uri)
    }

    fun cancelStorageTransfer() {
        coordinator?.storageCoordinator?.cancelActiveTransfer()
    }

    fun requestStorageDeleteConfirmation(item: StorageFileItem) {
        if (coordinator != null) {
            coordinator.storageCoordinator.requestDeleteConfirmation(item)
            return
        }
        _uiState.update { state ->
            state.copy(storage = state.storage.copy(deleteConfirmTarget = item))
        }
    }

    fun dismissStorageDeleteConfirmation() {
        if (coordinator != null) {
            coordinator.storageCoordinator.dismissDeleteConfirmation()
            return
        }
        _uiState.update { state ->
            state.copy(storage = state.storage.copy(deleteConfirmTarget = null))
        }
    }

    fun confirmStorageDelete() {
        if (coordinator != null) {
            coordinator.storageCoordinator.confirmDelete()
            return
        }
        val target = _uiState.value.storage.deleteConfirmTarget ?: return
        _uiState.update { state ->
            state.copy(
                storage = state.storage.copy(
                    items = state.storage.items.filterNot { it.path == target.path },
                    selectedItem = if (state.storage.selectedItem?.path == target.path) null else state.storage.selectedItem,
                    deleteConfirmTarget = null
                ),
                toastMessage = "Removed ${target.name} (preview mode)"
            )
        }
    }

    // -------------------------------------------------------------------------
    // MONITOR PANELS (SMS, CALL LOG, NOTIF, KEYBOARD, WELLBEING, APPLOCK, VF, WP)
    // -------------------------------------------------------------------------

    fun toggleMonitorPanelPlayPause(featureId: FeatureId) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.toggleFeatureMonitoring(featureId)
            return
        }
        _uiState.update { state ->
            val m = state.monitor
            val (nextMonitor, isNowLive) = when (featureId) {
                FeatureId.SMS -> {
                    val next = flipState(m.smsState)
                    m.copy(smsState = next) to (next == MonitoringState.LIVE)
                }
                FeatureId.CALLLOG -> {
                    val next = flipState(m.callLogState)
                    m.copy(callLogState = next) to (next == MonitoringState.LIVE)
                }
                FeatureId.NOTIFICATION -> {
                    val next = flipState(m.notificationState)
                    m.copy(notificationState = next) to (next == MonitoringState.LIVE)
                }
                FeatureId.KEYBOARD -> {
                    val next = flipState(m.keyboardState)
                    m.copy(keyboardState = next) to (next == MonitoringState.LIVE)
                }
                FeatureId.WELLBEING -> {
                    val next = flipState(m.wellbeing.monitoringState)
                    m.copy(wellbeing = m.wellbeing.copy(monitoringState = next)) to (next == MonitoringState.LIVE)
                }
                FeatureId.APPLOCK -> {
                    val next = flipState(m.appLock.monitoringState)
                    m.copy(appLock = m.appLock.copy(monitoringState = next)) to (next == MonitoringState.LIVE)
                }
                FeatureId.WALLPAPER -> {
                    val next = flipState(m.wallpaper.monitoringState)
                    m.copy(wallpaper = m.wallpaper.copy(monitoringState = next)) to (next == MonitoringState.LIVE)
                }
                else -> m to false
            }

            val updatedLive = if (isNowLive) state.liveFeatures + featureId else state.liveFeatures - featureId
            state.copy(monitor = nextMonitor, liveFeatures = updatedLive)
        }
    }

    private fun flipState(current: MonitoringState): MonitoringState {
        return if (current == MonitoringState.LIVE) MonitoringState.IDLE else MonitoringState.LIVE
    }

    fun setSmsSearchQuery(query: String) {
        _uiState.update { it.copy(monitor = it.monitor.copy(smsSearchQuery = query)) }
    }

    fun selectSmsItem(item: SmsUiItem?) {
        _uiState.update { it.copy(monitor = it.monitor.copy(selectedSms = item)) }
    }

    fun setCallFilter(filter: CallFilter) {
        _uiState.update { it.copy(monitor = it.monitor.copy(callFilter = filter)) }
    }

    fun toggleExpandedCall(callId: String) {
        _uiState.update { state ->
            val next = if (state.monitor.expandedCallId == callId) null else callId
            state.copy(monitor = state.monitor.copy(expandedCallId = next))
        }
    }

    fun setNotificationSearchQuery(query: String) {
        _uiState.update { it.copy(monitor = it.monitor.copy(notificationSearchQuery = query)) }
    }

    fun setNotificationFilter(filter: NotificationFilter) {
        _uiState.update { it.copy(monitor = it.monitor.copy(notificationFilter = filter)) }
    }

    fun selectNotificationItem(item: NotificationUiItem?) {
        _uiState.update { it.copy(monitor = it.monitor.copy(selectedNotification = item)) }
    }

    fun setKeyboardSearchQuery(query: String) {
        _uiState.update { it.copy(monitor = it.monitor.copy(keyboardSearchQuery = query)) }
    }

    fun clearKeyboardRecords() {
        _uiState.update {
            it.copy(
                monitor = it.monitor.copy(keyboardItems = emptyList()),
                toastMessage = "Keyboard log cleared"
            )
        }
    }

    fun setWellbeingRange(range: WellbeingRange) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.selectWellbeingRange(range)
        } else {
            _uiState.update {
                it.copy(monitor = it.monitor.copy(wellbeing = it.monitor.wellbeing.copy(range = range)))
            }
        }
    }

    fun refreshWellbeing() {
        if (coordinator != null) {
            coordinator.monitorCoordinator.refreshWellbeing()
        } else {
            showToast("Wellbeing refresh queued")
        }
    }

    fun setWellbeingSearchQuery(query: String) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(wellbeing = it.monitor.wellbeing.copy(searchQuery = query)))
        }
    }

    fun setWellbeingCategory(category: WellbeingCategoryFilter) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(wellbeing = it.monitor.wellbeing.copy(categoryFilter = category)))
        }
    }

    fun toggleRemoteAppLock() {
        if (coordinator != null) {
            coordinator.monitorCoordinator.toggleRemoteAppLock()
        } else {
            _uiState.update { state ->
                val next = !state.monitor.appLock.isLockEnabled
                state.copy(
                    monitor = state.monitor.copy(appLock = state.monitor.appLock.copy(isLockEnabled = next)),
                    toastMessage = if (next) "App Lock enabled (UI Preview)" else "App Lock disabled (UI Preview)"
                )
            }
        }
    }

    fun setAppLockSearchQuery(query: String) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(appLock = it.monitor.appLock.copy(searchQuery = query)))
        }
    }

    fun setChangePinDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(appLock = it.monitor.appLock.copy(showChangePinDialog = visible)))
        }
    }

    fun confirmNewAppLockPin(pin: String) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.setRemoteAppLockPin(pin)
        } else {
            setChangePinDialogVisible(false)
            showToast("6-digit PIN update queued")
        }
    }

    fun clearAppLockRecords() {
        if (coordinator != null) {
            coordinator.monitorCoordinator.clearAppLockRecords()
        } else {
            _uiState.update {
                it.copy(
                    monitor = it.monitor.copy(appLock = it.monitor.appLock.copy(attempts = emptyList())),
                    toastMessage = "App Lock attempt logs cleared"
                )
            }
        }
    }

    fun setSelfiePreviewAttempt(attempt: AppLockAttemptItem?) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(appLock = it.monitor.appLock.copy(previewingSelfieAttempt = attempt)))
        }
    }

    fun triggerVibrate(pattern: VibratePattern) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.triggerVibrate(pattern)
        } else {
            triggerVibrateFlashAction("Vibration: ${pattern.displayLabel}")
        }
    }

    fun triggerFlash(action: FlashAction) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.triggerFlash(action)
        } else {
            triggerVibrateFlashAction(action.displayLabel)
        }
    }

    fun triggerCombined(pattern: CombinedPattern) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.triggerCombined(pattern)
        } else {
            triggerVibrateFlashAction("Combined ${pattern.displayLabel}")
        }
    }

    fun triggerVibrateFlashAction(label: String) {
        _uiState.update {
            it.copy(
                monitor = it.monitor.copy(
                    vibrateFlash = VibrateFlashUiState(
                        lastSuccess = true,
                        statusMessage = "Queued command: $label (connect child to execute)"
                    )
                ),
                toastMessage = "$label triggered (UI Preview)"
            )
        }
    }

    fun setWallpaperTarget(target: WallpaperTarget) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(wallpaper = it.monitor.wallpaper.copy(target = target)))
        }
    }

    fun requestWallpaperPreview() {
        if (coordinator != null) {
            coordinator.monitorCoordinator.fetchActiveWallpaper()
        } else {
            _uiState.update {
                it.copy(
                    monitor = it.monitor.copy(
                        wallpaper = it.monitor.wallpaper.copy(hasPreviewLoaded = true, isFetching = false)
                    ),
                    toastMessage = "Wallpaper preview placeholder loaded"
                )
            }
        }
    }

    fun pushWallpaperFromUri(uri: Uri) {
        if (coordinator != null) {
            coordinator.monitorCoordinator.pushWallpaperFromUri(uri)
        } else {
            showToast("Connect a child device to push wallpaper")
        }
    }

    // -------------------------------------------------------------------------
    // STEALTH MODE & ALERTS
    // -------------------------------------------------------------------------

    fun onStealthButtonClicked() {
        val state = _uiState.value
        if (state.connectionPhase != ConnectionPhase.CONNECTED) {
            _uiState.update { it.copy(showStealthNotConnectedDialog = true) }
        } else {
            _uiState.update { it.copy(showStealthConfirmDialog = true) }
        }
    }

    fun dismissStealthDialogs() {
        _uiState.update {
            it.copy(showStealthConfirmDialog = false, showStealthNotConnectedDialog = false)
        }
    }

    fun confirmStealthToggle() {
        if (coordinator != null) {
            coordinator.sendStealthToggleCommand()
        } else {
            _uiState.update { state ->
                val next = if (state.stealthStatus == StealthStatus.ACTIVE) {
                    StealthStatus.ARMED
                } else {
                    StealthStatus.ACTIVE
                }
                state.copy(
                    stealthStatus = next,
                    showStealthConfirmDialog = false,
                    toastMessage = if (next == StealthStatus.ACTIVE) {
                        "Stealth Mode ACTIVE (UI Preview)"
                    } else {
                        "Stealth Mode deactivated (UI Preview)"
                    }
                )
            }
        }
    }

    fun dismissCallAlert() {
        _uiState.update { it.copy(activeCallAlert = null) }
    }

    fun acceptCallAlertAndListenMic() {
        if (coordinator != null) {
            coordinator.micCoordinator.acceptCallAlertAndListenMic()
            return
        }
        _uiState.update { state ->
            state.copy(
                activeCallAlert = null,
                mic = state.mic.copy(isOverlayOpen = true, listenState = MonitoringState.LIVE),
                liveFeatures = state.liveFeatures + FeatureId.MIC
            )
        }
    }

    // -------------------------------------------------------------------------
    // SETTINGS (5 TABS + AUTO-SAVE + FACTORY RESET)
    // -------------------------------------------------------------------------

    fun openSettings(tab: SettingsTabId = SettingsTabId.GENERAL) {
        coordinator?.refreshAudioDevices()
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                activeSettingsTab = tab,
                selectedPairedDeviceId = null,
                isDrawerOpen = false,
                isDevicePopoverOpen = false
            )
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false, selectedPairedDeviceId = null) }
    }

    fun selectSettingsTab(tab: SettingsTabId) {
        _uiState.update { it.copy(activeSettingsTab = tab, selectedPairedDeviceId = null) }
    }

    fun selectPairedDevice(rawId: String) {
        _uiState.update { it.copy(selectedPairedDeviceId = rawId) }
    }

    fun clearSelectedPairedDevice() {
        _uiState.update { it.copy(selectedPairedDeviceId = null) }
    }

    fun clearWorkspaceHistory() {
        if (coordinator != null) {
            coordinator.clearLocalWorkspaceHistory()
        } else {
            onHistoryCleared()
        }
    }

    fun updateSettings(transform: (SettingsValues) -> SettingsValues) {
        var latestSettings: SettingsValues? = null
        _uiState.update { state ->
            val updated = transform(state.settings)
            latestSettings = updated
            state.copy(
                settings = updated,
                camera = if (state.camera.state == MonitoringState.IDLE && !state.camera.isSwitchingLens) {
                    state.camera.copy(lens = updated.preferredCameraLens)
                } else {
                    state.camera
                },
                screen = state.screen.copy(quality = updated.screenQuality),
                mic = state.mic.copy(selectedVoice = updated.defaultVoicePreset)
            )
        }
        latestSettings?.let { coordinator?.saveSettings(it) }
    }

    fun resetSettingsToDefaults() {
        val defaults = SettingsValues()
        coordinator?.saveSettings(defaults)
        _uiState.update {
            it.copy(
                settings = defaults,
                camera = it.camera.copy(lens = defaults.preferredCameraLens),
                screen = it.screen.copy(quality = defaults.screenQuality),
                mic = it.mic.copy(selectedVoice = defaults.defaultVoicePreset),
                toastMessage = "All settings restored to factory defaults"
            )
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
