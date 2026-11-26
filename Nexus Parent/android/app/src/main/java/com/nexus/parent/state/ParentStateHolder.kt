package com.nexus.parent.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sin

/**
 * ParentStateHolder.kt - Deterministic UI-only state controller for Nexus Parent Android.
 *
 * Hey friend! In this UI-first milestone, all interactions (pairing validation, drawer search,
 * feature switching, DVR scrubbing, Mic HUD toggles, Monitor Start/Pause transitions, Stealth
 * confirmation, and Settings auto-save) run locally on `StateFlow<ParentUiState>` with zero
 * network/WebRTC dependencies. Every public method here becomes a clean hook point when
 * WebRTC and Firebase signaling are wired next.
 */
class ParentStateHolder(initialState: ParentUiState = ParentUiState()) {

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
        val matchedTemplate = current.lastPairedDevice?.takeIf { it.rawId == digits }
        val connectedRecord = DeviceRecord(
            rawId = digits,
            formattedId = formatted,
            name = matchedTemplate?.name ?: "Child Android Device",
            model = matchedTemplate?.model ?: "Android Device",
            lastSyncedLabel = "Online • Just now",
            telemetry = matchedTemplate?.telemetry ?: DeviceTelemetry()
        )

        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.CONNECTED,
                pairingInputDigits = digits,
                pairingErrorMessage = null,
                pairingStatusMessage = null,
                connectedDevice = connectedRecord,
                lastPairedDevice = connectedRecord,
                isDevicePopoverOpen = false,
                toastMessage = "UI Preview: Connected to $formatted"
            )
        }
    }

    fun disconnectDevice() {
        _uiState.update { state ->
            state.copy(
                connectionPhase = ConnectionPhase.DISCONNECTED,
                connectedDevice = null,
                isDevicePopoverOpen = false,
                activeFeature = null,
                liveFeatures = emptySet(),
                camera = CameraFeedUiState(lens = state.settings.preferredCameraLens),
                screen = ScreenFeedUiState(quality = state.settings.screenQuality),
                mic = MicUiState(selectedVoice = state.settings.defaultVoicePreset),
                location = LocationUiState(),
                activeCallAlert = null,
                stealthStatus = StealthStatus.ARMED,
                toastMessage = "Device disconnected"
            )
        }
    }

    fun cancelReconnection() {
        disconnectDevice()
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

        _uiState.update { state ->
            val nextActive = if (state.activeFeature == featureId) null else featureId
            state.copy(
                activeFeature = nextActive,
                isDrawerOpen = false,
                isDevicePopoverOpen = false
            )
        }
    }

    fun closeActiveFeature() {
        _uiState.update { it.copy(activeFeature = null) }
    }

    // -------------------------------------------------------------------------
    // CAMERA & SCREEN LIVE SURVEILLANCE UI CONTROLS
    // -------------------------------------------------------------------------

    fun toggleCameraStream() {
        _uiState.update { state ->
            val next = if (state.camera.state == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
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
    }

    fun rotateCameraFeed() {
        _uiState.update { state ->
            val nextDeg = (state.camera.rotationDegrees + 90) % 360
            state.copy(camera = state.camera.copy(rotationDegrees = nextDeg))
        }
    }

    fun toggleCameraLens() {
        _uiState.update { state ->
            val nextLens = if (state.camera.lens == CameraLens.REAR) CameraLens.FRONT else CameraLens.REAR
            state.copy(
                camera = state.camera.copy(lens = nextLens),
                toastMessage = "Camera lens switched to ${nextLens.label}"
            )
        }
    }

    fun scrubCameraDvr(fraction: Float) {
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
        _uiState.update {
            it.copy(camera = it.camera.copy(dvrProgress = 1f, isAtLiveEdge = true))
        }
    }

    fun toggleCameraFullscreen() {
        _uiState.update {
            it.copy(camera = it.camera.copy(isFullscreen = !it.camera.isFullscreen))
        }
    }

    fun toggleScreenStream() {
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

    fun scrubScreenDvr(fraction: Float) {
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
        _uiState.update {
            it.copy(screen = it.screen.copy(dvrProgress = 1f, isAtLiveEdge = true))
        }
    }

    fun toggleScreenFullscreen() {
        _uiState.update {
            it.copy(screen = it.screen.copy(isFullscreen = !it.screen.isFullscreen))
        }
    }

    // -------------------------------------------------------------------------
    // FLOATING MICROPHONE & VOICE INTERCOM HUD
    // -------------------------------------------------------------------------

    fun setMicOverlayOpen(open: Boolean) {
        _uiState.update {
            it.copy(mic = it.mic.copy(isOverlayOpen = open, isVoiceMenuExpanded = false))
        }
    }

    fun toggleMicListen() {
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

    fun toggleMicSpeak() {
        _uiState.update { state ->
            val next = if (state.mic.speakState == MonitoringState.LIVE) {
                MonitoringState.IDLE
            } else {
                MonitoringState.LIVE
            }
            state.copy(mic = state.mic.copy(speakState = next, isVoiceMenuExpanded = false))
        }
    }

    fun toggleVoiceMenu() {
        _uiState.update {
            it.copy(mic = it.mic.copy(isVoiceMenuExpanded = !it.mic.isVoiceMenuExpanded))
        }
    }

    fun selectVoicePreset(preset: VoicePresetId) {
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

    fun setStorageSearchQuery(query: String) {
        _uiState.update { it.copy(storage = it.storage.copy(searchQuery = query)) }
    }

    fun selectStorageCategory(category: StorageCategory) {
        _uiState.update { it.copy(storage = it.storage.copy(selectedCategory = category)) }
    }

    fun selectStorageItem(item: StorageFileItem?) {
        _uiState.update { it.copy(storage = it.storage.copy(selectedItem = item)) }
    }

    fun navigateStorageBreadcrumb(index: Int) {
        _uiState.update { state ->
            val clamped = index.coerceIn(0, state.storage.breadcrumbs.lastIndex.coerceAtLeast(0))
            state.copy(
                storage = state.storage.copy(
                    breadcrumbs = state.storage.breadcrumbs.take(clamped + 1),
                    selectedItem = null
                )
            )
        }
    }

    fun navigateStorageUp() {
        _uiState.update { state ->
            if (state.storage.breadcrumbs.size <= 1) return@update state
            state.copy(
                storage = state.storage.copy(
                    breadcrumbs = state.storage.breadcrumbs.dropLast(1),
                    selectedItem = null
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // MONITOR PANELS (SMS, CALL LOG, NOTIF, KEYBOARD, WELLBEING, APPLOCK, VF, WP)
    // -------------------------------------------------------------------------

    fun toggleMonitorPanelPlayPause(featureId: FeatureId) {
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
        _uiState.update {
            it.copy(monitor = it.monitor.copy(wellbeing = it.monitor.wellbeing.copy(range = range)))
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
        _uiState.update { state ->
            val next = !state.monitor.appLock.isLockEnabled
            state.copy(
                monitor = state.monitor.copy(appLock = state.monitor.appLock.copy(isLockEnabled = next)),
                toastMessage = if (next) "App Lock enabled (UI Preview)" else "App Lock disabled (UI Preview)"
            )
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

    fun setSelfiePreviewAttempt(attempt: AppLockAttemptItem?) {
        _uiState.update {
            it.copy(monitor = it.monitor.copy(appLock = it.monitor.appLock.copy(previewingSelfieAttempt = attempt)))
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
        _uiState.update {
            it.copy(
                monitor = it.monitor.copy(
                    wallpaper = it.monitor.wallpaper.copy(hasPreviewLoaded = true, isFetching = false)
                ),
                toastMessage = "Wallpaper preview placeholder loaded"
            )
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

    fun dismissCallAlert() {
        _uiState.update { it.copy(activeCallAlert = null) }
    }

    fun acceptCallAlertAndListenMic() {
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
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                activeSettingsTab = tab,
                isDrawerOpen = false,
                isDevicePopoverOpen = false
            )
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun selectSettingsTab(tab: SettingsTabId) {
        _uiState.update { it.copy(activeSettingsTab = tab) }
    }

    fun updateSettings(transform: (SettingsValues) -> SettingsValues) {
        _uiState.update { state ->
            val updated = transform(state.settings)
            state.copy(
                settings = updated,
                camera = state.camera.copy(lens = updated.preferredCameraLens),
                screen = state.screen.copy(quality = updated.screenQuality),
                mic = state.mic.copy(selectedVoice = updated.defaultVoicePreset)
            )
        }
    }

    fun resetSettingsToDefaults() {
        val defaults = SettingsValues()
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
