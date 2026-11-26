package com.nexus.parent.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexus.parent.data.alerts.GeneralAlertsController
import com.nexus.parent.data.alerts.SafetyAlertDispatcher
import com.nexus.parent.data.mic.AudioDeviceCatalog
import com.nexus.parent.data.mic.MicCoordinator
import com.nexus.parent.data.mic.ParentAudioRouteManager
import com.nexus.parent.data.mic.WebRtcMicMediaBridge
import com.nexus.parent.data.monitor.MonitorCoordinator
import com.nexus.parent.data.storage.StorageCoordinator
import com.nexus.parent.data.update.AppUpdateManager
import com.nexus.parent.service.ParentConnectionService
import com.nexus.parent.service.ParentNotificationManager
import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.ConnectionPhase
import com.nexus.parent.state.DeviceRecord
import com.nexus.parent.state.DeviceTelemetry
import com.nexus.parent.state.FeatureRegistry
import com.nexus.parent.state.LocalStorageMetrics
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.ParentUiState
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.StealthStatus
import com.nexus.parent.webrtc.AuthManager
import com.nexus.parent.webrtc.VideoFeedType
import com.nexus.parent.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DeviceStateCoordinator.kt - Live WebRTC, Signaling, Telemetry, Reconnection, Settings Persistence,
 * and Foreground Service coordinator matching Desktop `device-state.ts`.
 *
 * Hey friend! Think of this class as the "engine room" sitting right below `ParentStateHolder`:
 * - On app boot: restores saved `SettingsValues`, `lastConnectedDevice`, and `pairedDevicesHistory`.
 * - If `autoConnectLastDevice` is enabled, attempts one automatic reconnect right after launch.
 * - During active sessions: pushes 27-field `DEVICE_TELEMETRY` updates, handles `STEALTH_STATUS` acks,
 *   detects unexpected drops to trigger `ReconnectManager`'s 9-attempt backoff schedule, and keeps
 *   `ParentConnectionService` running so screen-off doesn't kill the socket.
 */
class DeviceStateCoordinator(
    context: Context,
    private val storage: DeviceStorage,
    val webRtcManager: WebRtcManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {

    companion object {
        private const val TAG = "DeviceCoordinator"
        private const val DISCONNECTED_GRACE_MS = 3500L
    }

    private val appContext = context.applicationContext
    private val reconnectManager = ReconnectManager(scope)
    val audioDeviceCatalog = AudioDeviceCatalog(appContext)
    private val audioRouteManager = ParentAudioRouteManager(appContext, audioDeviceCatalog)
    private val startupAttempted = AtomicBoolean(false)

    val alertsController: GeneralAlertsController = GeneralAlertsController(
        dispatcher = object : SafetyAlertDispatcher {
            override fun showToast(message: String) {
                stateHolder?.showToast(message)
            }

            override fun postNotification(
                notificationId: Int,
                title: String,
                body: String,
                onlyAlertOnce: Boolean
            ) {
                ParentNotificationManager.postSafetyAlert(
                    context = appContext,
                    notificationId = notificationId,
                    title = title,
                    body = body,
                    onlyAlertOnce = onlyAlertOnce
                )
            }

            override fun cancelNotification(notificationId: Int) {
                ParentNotificationManager.cancelNotification(appContext, notificationId)
            }
        }
    )

    val monitorCoordinator: MonitorCoordinator = MonitorCoordinator(
        context = appContext,
        scope = scope,
        sendCommand = webRtcManager::sendCommand,
        onIntruderAttempt = { attempt ->
            val holder = stateHolder
            if (holder != null) {
                val devName = activeSessionDevice?.name ?: holder.uiState.value.connectedDevice?.name
                alertsController.evaluateIntruderSelfie(
                    attempt = attempt,
                    settings = holder.uiState.value.settings,
                    deviceName = devName
                )
            } else {
                false
            }
        }
    )

    val appUpdateManager: AppUpdateManager = AppUpdateManager(
        context = appContext,
        scope = scope
    )

    private val _pendingInstallIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    val pendingInstallIntents: SharedFlow<Intent> = _pendingInstallIntents.asSharedFlow()

    val storageCoordinator: StorageCoordinator = StorageCoordinator(
        context = appContext,
        scope = scope,
        sendCommand = webRtcManager::sendCommand
    )

    val surveillanceCoordinator: SurveillanceCoordinator = SurveillanceCoordinator(
        appContext = appContext,
        scope = scope,
        webRtcManager = webRtcManager,
        resolvePreferredMediaOutput = { deviceId ->
            audioDeviceCatalog.resolveOutputDevice(deviceId, preferVoiceProfile = false)
        }
    )

    val micCoordinator: MicCoordinator = MicCoordinator(
        scope = scope,
        mediaBridge = WebRtcMicMediaBridge(
            webRtcManager = webRtcManager,
            routeManager = audioRouteManager,
            catalog = audioDeviceCatalog,
            onSpeechFgsChanged = { active ->
                includeMicFgsType = active
                val activeDev = activeSessionDevice
                if (activeDev != null) {
                    updateServiceNotification(
                        title = "Connected to ${activeDev.name}",
                        body = if (active) {
                            "Broadcast Intercom active (${activeDev.formattedId})"
                        } else {
                            "Encrypted P2P session active (${activeDev.formattedId})"
                        }
                    )
                }
            }
        ),
        sendCommand = webRtcManager::sendCommand,
        onPersistSettings = { updated -> storage.saveSettings(updated) },
        alertsController = alertsController
    )

    private var stateHolder: ParentStateHolder? = null
    private var activeSessionDevice: DeviceRecord? = null
    private var lastStealthBeforeWait: StealthStatus = StealthStatus.ARMED
    private var wasManuallyDisconnected = false
    private var isAttemptingConnection = false
    private var includeMicFgsType = false
    private var disconnectGraceJob: Job? = null
    private var lastServiceText: String = ""

    init {
        configureReconnectManager()
        observeWebRtcStreams()
        observeReconnectState()
        observeAudioDevices()
        observeAppUpdates()
    }

    fun attachStateHolder(holder: ParentStateHolder) {
        stateHolder = holder
        monitorCoordinator.attachStateHolder(holder)
        storageCoordinator.attachStateHolder(holder)
        surveillanceCoordinator.attachStateHolder(holder)
        micCoordinator.attachStateHolder(holder)
    }

    fun refreshAudioDevices() {
        val snap = audioDeviceCatalog.refresh()
        stateHolder?.updateAudioDevices(snap.inputs, snap.outputs)
        audioRouteManager.refreshActiveRouteIfNeeded()
        surveillanceCoordinator.refreshAudioOutputDevice()
    }

    fun buildInitialUiState(): ParentUiState {
        val settings = storage.loadSettings()
        val last = storage.loadLastConnectedDevice()
        val history = storage.loadPairedDevicesHistory()
        val audioSnap = audioDeviceCatalog.snapshot.value
        return ParentUiState(
            settings = settings,
            availableAudioInputs = audioSnap.inputs,
            availableAudioOutputs = audioSnap.outputs,
            lastPairedDevice = last,
            pairedDevicesHistory = history,
            appUpdate = appUpdateManager.state.value,
            localStorageMetrics = computeStorageFootprint(),
            isFirebaseSignedIn = AuthManager.isSignedIn(appContext),
            camera = com.nexus.parent.state.CameraFeedUiState(lens = settings.preferredCameraLens),
            screen = com.nexus.parent.state.ScreenFeedUiState(quality = settings.screenQuality),
            mic = com.nexus.parent.state.MicUiState(selectedVoice = settings.defaultVoicePreset)
        )
    }

    private fun observeAppUpdates() {
        scope.launch {
            appUpdateManager.state.collect { updateState ->
                stateHolder?.onAppUpdateStateChanged(updateState)
                refreshAboutTelemetry()
            }
        }
    }

    private fun computeStorageFootprint(): LocalStorageMetrics {
        return storage.measureStorageFootprint(appUpdateManager.computeUpdatesCacheBytes())
    }

    fun refreshAboutTelemetry() {
        stateHolder?.updateAboutTelemetry(
            metrics = computeStorageFootprint(),
            isFirebaseSignedIn = AuthManager.isSignedIn(appContext)
        )
    }

    private fun observeAudioDevices() {
        scope.launch {
            audioDeviceCatalog.snapshot.collect { snap ->
                stateHolder?.updateAudioDevices(snap.inputs, snap.outputs)
                audioRouteManager.refreshActiveRouteIfNeeded()
                surveillanceCoordinator.refreshAudioOutputDevice()
            }
        }
    }

    fun getEglBaseContext(): EglBase.Context = webRtcManager.getEglBaseContext()

    fun runStartupAutoConnect() {
        if (!startupAttempted.compareAndSet(false, true)) return
        val holder = stateHolder ?: return
        val current = holder.uiState.value
        appUpdateManager.syncAutoCheckPreference(
            enabled = current.settings.autoCheckUpdates,
            triggerImmediateIfStartup = true
        )
        val target = current.lastPairedDevice ?: return
        if (!current.settings.autoConnectLastDevice) return
        if (current.connectionPhase != ConnectionPhase.DISCONNECTED) return

        holder.showToast("Auto-connecting to ${target.name}...")
        scope.launch {
            val ok = connectLive(
                rawId = target.rawId,
                fromHistoryId = null,
                isAutoReconnectAttempt = true,
                suppressFailureError = true
            )
            if (ok) {
                holder.showToast("Auto-connected to ${target.name}")
            } else {
                holder.showToast("${target.name} is offline — ready for manual connect")
            }
        }
    }

    fun connectFromUi(rawInput: String, fromHistoryId: String? = null) {
        scope.launch {
            connectLive(
                rawId = rawInput,
                fromHistoryId = fromHistoryId,
                isAutoReconnectAttempt = false,
                suppressFailureError = false
            )
        }
    }

    suspend fun connectLive(
        rawId: String,
        fromHistoryId: String? = null,
        isAutoReconnectAttempt: Boolean = false,
        suppressFailureError: Boolean = false
    ): Boolean {
        val holder = stateHolder ?: return false
        val cleanId = FeatureRegistry.sanitizeChildIdDigits(rawId)
        if (!FeatureRegistry.isValidChildId(cleanId)) {
            if (!isAutoReconnectAttempt) {
                holder.onConnectingFailed("Please enter a valid 12-digit Child ID.", isAutoReconnectAttempt = false)
            }
            return false
        }

        if (isAttemptingConnection) return false
        isAttemptingConnection = true

        // Clear any stray grace timer or earlier auto-reconnect loop when connecting manually
        disconnectGraceJob?.cancel()
        disconnectGraceJob = null
        wasManuallyDisconnected = false
        if (!isAutoReconnectAttempt && reconnectManager.isReconnecting()) {
            reconnectManager.cancel(silent = true)
        }

        val formattedId = FeatureRegistry.formatChildId(cleanId)
        holder.onConnectingStarted(
            digits = cleanId,
            progressMessage = "Authenticating session...",
            fromHistoryId = fromHistoryId,
            preserveReconnectingPhase = isAutoReconnectAttempt && reconnectManager.isReconnecting()
        )
        updateServiceNotification(
            title = "Nexus Parent Connecting",
            body = "Signaling $formattedId..."
        )

        return try {
            val hwAccel = holder.uiState.value.settings.hardwareAcceleration
            val metadata = webRtcManager.connect(
                childId = cleanId,
                hardwareAcceleration = hwAccel,
                onProgress = { progress ->
                    holder.onConnectingProgress(progress)
                    updateServiceNotification("Nexus Parent Connecting", progress)
                }
            )

            // Preserve any richer cached telemetry already known for this child until fresh packet arrives
            val cached = holder.uiState.value.pairedDevicesHistory.firstOrNull { it.rawId == cleanId }
                ?: holder.uiState.value.lastPairedDevice?.takeIf { it.rawId == cleanId }

            val now = System.currentTimeMillis()
            val mergedModel = metadata.model?.takeIf { it.isNotBlank() } ?: cached?.model ?: "Android Device"
            val mergedRecord = DeviceRecord(
                rawId = cleanId,
                formattedId = formattedId,
                name = cached?.name?.takeIf { it.isNotBlank() } ?: mergedModel,
                model = mergedModel,
                lastSyncedLabel = "Online • Just now",
                lastConnectedAt = now,
                telemetry = (cached?.telemetry ?: DeviceTelemetry()).copy(
                    androidVersion = metadata.androidVersion?.takeIf { it.isNotBlank() }
                        ?: cached?.telemetry?.androidVersion ?: "14",
                    batteryPercent = metadata.batteryPercent ?: cached?.telemetry?.batteryPercent ?: 0,
                    brand = metadata.manufacturer?.takeIf { it.isNotBlank() }
                        ?: cached?.telemetry?.brand ?: "Android",
                    deviceUptimeLabel = cached?.telemetry?.deviceUptimeLabel ?: "Active"
                )
            )

            activeSessionDevice = mergedRecord
            val updatedHistory = storage.savePairedDevice(mergedRecord)
            reconnectManager.handleSuccess()

            holder.onConnectionEstablished(
                record = mergedRecord,
                history = updatedHistory,
                toastMessage = if (isAutoReconnectAttempt) null else "Connected to ${mergedRecord.name}",
                sessionHardwareAcceleration = hwAccel
            )
            updateServiceNotification(
                title = "Connected to ${mergedRecord.name}",
                body = "Encrypted P2P session active ($formattedId)"
            )
            true
        } catch (err: Exception) {
            Log.w(TAG, "Connection failed for $cleanId: ${err.message}")
            val errMsg = err.message?.takeIf { it.isNotBlank() } ?: "Unable to connect to child device."
            val stillReconnecting = isAutoReconnectAttempt && reconnectManager.isReconnecting()
            if (!stillReconnecting) {
                activeSessionDevice = null
                ParentConnectionService.stop(appContext)
                lastServiceText = ""
            }
            holder.onConnectingFailed(
                errorMessage = if (suppressFailureError || stillReconnecting) null else errMsg,
                isAutoReconnectAttempt = stillReconnecting
            )
            false
        } finally {
            isAttemptingConnection = false
        }
    }

    fun disconnectManual() {
        val holder = stateHolder ?: return
        wasManuallyDisconnected = true
        disconnectGraceJob?.cancel()
        disconnectGraceJob = null
        activeSessionDevice = null

        monitorCoordinator.onPeerDisconnected()
        storageCoordinator.onPeerDisconnected()
        surveillanceCoordinator.onPeerDisconnected()
        micCoordinator.onPeerDisconnected()
        alertsController.resetSession()
        includeMicFgsType = false
        reconnectManager.cancel(silent = true)
        webRtcManager.disconnect(clearRemoteSignaling = true)
        ParentConnectionService.stop(appContext)
        lastServiceText = ""

        holder.applyDisconnectedState(toastMessage = "Device disconnected")
    }

    fun reconnectCurrentDevice() {
        val targetId = activeSessionDevice?.rawId
            ?: stateHolder?.uiState?.value?.connectedDevice?.rawId
            ?: return
        if (isAttemptingConnection) return
        disconnectManual()
        connectFromUi(targetId)
    }

    fun retryReconnectNow() {
        reconnectManager.retryNow()
    }

    fun cancelReconnect() {
        wasManuallyDisconnected = true
        disconnectGraceJob?.cancel()
        disconnectGraceJob = null
        activeSessionDevice = null
        monitorCoordinator.onPeerDisconnected()
        storageCoordinator.onPeerDisconnected()
        surveillanceCoordinator.onPeerDisconnected()
        micCoordinator.onPeerDisconnected()
        alertsController.resetSession()
        includeMicFgsType = false
        reconnectManager.cancel(silent = false)
        webRtcManager.disconnect(clearRemoteSignaling = false)
        ParentConnectionService.stop(appContext)
        lastServiceText = ""
    }

    fun sendStealthToggleCommand() {
        val holder = stateHolder ?: return
        val currentStatus = holder.uiState.value.stealthStatus
        if (currentStatus == StealthStatus.WAIT) return

        val command = if (currentStatus == StealthStatus.ACTIVE) "STEALTH_OFF" else "STEALTH_ON"
        lastStealthBeforeWait = currentStatus
        holder.onStealthCommandDispatched()

        val sent = webRtcManager.sendCommand(command)
        if (!sent) {
            holder.onStealthStatusSynced(lastStealthBeforeWait, toastMessage = "DataChannel unavailable; stealth unchanged")
        }
    }

    fun saveSettings(settings: SettingsValues) {
        storage.saveSettings(settings)
        surveillanceCoordinator.onSettingsUpdated(settings)
        micCoordinator.onSettingsUpdated(settings)
        appUpdateManager.syncAutoCheckPreference(settings.autoCheckUpdates, triggerImmediateIfStartup = false)
        if (!settings.alertChildActiveCall) {
            stateHolder?.dismissCallAlert()
        }
        if (activeSessionDevice != null) {
            alertsController.syncCallAlertSettingToChild(
                enabled = settings.alertChildActiveCall,
                sendCommand = webRtcManager::sendCommand,
                force = false
            )
        }
        refreshAboutTelemetry()
    }

    fun checkForUpdatesManual() {
        val holder = stateHolder ?: return
        holder.showToast("Checking GitHub for stable Nexus releases...")
        appUpdateManager.launchCheckForUpdates(manual = true) { result ->
            when (result.status) {
                AppUpdateStatusKind.READY -> {
                    val v = result.availableVersion ?: "latest"
                    holder.showToast("Update v$v downloaded — tap \"Restart to update\"")
                }
                AppUpdateStatusKind.UP_TO_DATE -> {
                    holder.showToast("You are running the latest stable version (v${result.currentVersion})")
                }
                AppUpdateStatusKind.ERROR -> {
                    holder.showToast("Update check failed: ${result.errorMessage ?: "Network error"}")
                }
                else -> Unit
            }
        }
    }

    fun restartToUpdate() {
        val holder = stateHolder ?: return
        if (!appUpdateManager.canRequestPackageInstalls()) {
            holder.showToast("Allow \"Install unknown apps\" for Nexus Parent to install update")
            _pendingInstallIntents.tryEmit(appUpdateManager.createUnknownSourcesPermissionIntent())
            return
        }
        val installIntent = appUpdateManager.createInstallApkIntent()
        if (installIntent == null) {
            holder.showToast("No downloaded update APK is staged")
            return
        }
        if (activeSessionDevice != null || holder.uiState.value.connectionPhase != ConnectionPhase.DISCONNECTED) {
            disconnectManual()
        }
        _pendingInstallIntents.tryEmit(installIntent)
    }

    fun executeWorkspaceReset() {
        disconnectManual()
        storage.clearPairedDevicesHistory()
        val defaults = SettingsValues()
        storage.saveSettings(defaults)
        surveillanceCoordinator.onSettingsUpdated(defaults)
        micCoordinator.onSettingsUpdated(defaults)
        appUpdateManager.syncAutoCheckPreference(defaults.autoCheckUpdates, triggerImmediateIfStartup = false)
        stateHolder?.applyFullWorkspaceReset(
            defaults = defaults,
            metrics = computeStorageFootprint(),
            isFirebaseSignedIn = AuthManager.isSignedIn(appContext)
        )
    }

    fun clearLocalWorkspaceHistory() {
        executeWorkspaceReset()
    }

    private fun configureReconnectManager() {
        reconnectManager.setConnectHandler { targetId ->
            connectLive(
                rawId = targetId,
                isAutoReconnectAttempt = true,
                suppressFailureError = true
            )
        }
        reconnectManager.setCallbacks(
            onCancelled = {
                stateHolder?.applyDisconnectedState(toastMessage = "Reconnection cancelled")
            },
            onExhausted = { target ->
                activeSessionDevice = null
                alertsController.resetSession()
                ParentConnectionService.stop(appContext)
                lastServiceText = ""
                val label = target?.name ?: "child device"
                stateHolder?.applyDisconnectedState(
                    toastMessage = "Could not reconnect to $label after ${ReconnectManager.MAX_RECONNECT_ATTEMPTS} attempts"
                )
            },
            onSucceeded = { target ->
                target?.let {
                    stateHolder?.showToast("Reconnected to ${it.name}")
                }
            }
        )
    }

    private fun observeWebRtcStreams() {
        scope.launch {
            webRtcManager.connectionState.collect { pcState ->
                handlePeerConnectionStateChange(pcState)
            }
        }

        scope.launch {
            webRtcManager.dataChannelOpened.collect {
                Log.i(TAG, "DataChannel opened cleanly for active session")
                val enabled = stateHolder?.uiState?.value?.settings?.alertChildActiveCall ?: true
                alertsController.syncCallAlertSettingToChild(
                    enabled = enabled,
                    sendCommand = webRtcManager::sendCommand,
                    force = true
                )
            }
        }

        scope.launch {
            webRtcManager.dataChannelMessages.collect { message ->
                handleDataChannelMessage(message)
            }
        }
    }

    private fun observeReconnectState() {
        scope.launch {
            reconnectManager.state.collect { rec ->
                if (!rec.isActive) return@collect
                stateHolder?.onReconnectTick(
                    attempt = rec.currentAttempt,
                    maxAttempts = rec.maxAttempts,
                    secondsRemaining = rec.secondsRemaining
                )
                val targetName = rec.targetDevice?.name ?: "Child"
                val detail = when (rec.status) {
                    ReconnectStatus.WAITING -> "Retry ${rec.currentAttempt}/${rec.maxAttempts} in ${rec.secondsRemaining}s"
                    ReconnectStatus.CONNECTING -> "Attempting reconnect ${rec.currentAttempt}/${rec.maxAttempts}..."
                    ReconnectStatus.IDLE -> "Reconnecting..."
                }
                updateServiceNotification("Reconnecting to $targetName", detail)
            }
        }
    }

    private fun handlePeerConnectionStateChange(pcState: PeerConnection.PeerConnectionState) {
        if (stateHolder == null) return
        when (pcState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                disconnectGraceJob?.cancel()
                disconnectGraceJob = null
                activeSessionDevice?.let { device ->
                    updateServiceNotification(
                        title = "Connected to ${device.name}",
                        body = "Encrypted P2P session active (${device.formattedId})"
                    )
                }
            }

            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                // Give brief grace window in case Wi-Fi <-> 5G handoff ICE-restarts cleanly
                if (wasManuallyDisconnected || isAttemptingConnection || activeSessionDevice == null) return
                if (disconnectGraceJob?.isActive == true) return
                disconnectGraceJob = scope.launch {
                    delay(DISCONNECTED_GRACE_MS)
                    if (!wasManuallyDisconnected && !isAttemptingConnection && activeSessionDevice != null) {
                        triggerUnexpectedDropReconnect()
                    }
                }
            }

            PeerConnection.PeerConnectionState.FAILED,
            PeerConnection.PeerConnectionState.CLOSED -> {
                disconnectGraceJob?.cancel()
                disconnectGraceJob = null
                if (!wasManuallyDisconnected && !isAttemptingConnection && activeSessionDevice != null) {
                    triggerUnexpectedDropReconnect()
                }
            }

            else -> Unit
        }
    }

    private fun triggerUnexpectedDropReconnect() {
        val holder = stateHolder ?: return
        val active = activeSessionDevice ?: return
        if (reconnectManager.isReconnecting()) return

        Log.w(TAG, "Unexpected WebRTC drop for ${active.rawId}; starting 9-attempt backoff")
        monitorCoordinator.onPeerDisconnected()
        storageCoordinator.onPeerDisconnected()
        surveillanceCoordinator.onPeerDisconnected()
        micCoordinator.onPeerDisconnected()
        alertsController.resetSession()
        includeMicFgsType = false
        webRtcManager.setActiveVideoType(null)
        webRtcManager.disconnect(clearRemoteSignaling = false)

        alertsController.evaluateUnexpectedDisconnect(active, holder.uiState.value.settings)
        holder.onConnectionDroppedReconnecting(toastMessage = null)

        reconnectManager.start(
            ReconnectTargetDevice(
                rawId = active.rawId,
                formattedId = active.formattedId,
                name = active.name,
                model = active.model
            )
        )
    }

    private fun handleDataChannelMessage(rawMessage: String) {
        if (surveillanceCoordinator.handleIncomingMessage(rawMessage)) return
        if (micCoordinator.handleIncomingMessage(rawMessage)) return
        if (storageCoordinator.handleIncomingMessage(rawMessage)) return

        val holder = stateHolder ?: return
        val trimmed = rawMessage.trim()

        if (trimmed == "DISCONNECT") {
            wasManuallyDisconnected = true
            disconnectGraceJob?.cancel()
            disconnectGraceJob = null
            activeSessionDevice = null
            monitorCoordinator.onPeerDisconnected()
            storageCoordinator.onPeerDisconnected()
            surveillanceCoordinator.onPeerDisconnected()
            micCoordinator.onPeerDisconnected()
            alertsController.resetSession()
            includeMicFgsType = false
            reconnectManager.cancel(silent = true)
            webRtcManager.disconnect(clearRemoteSignaling = false)
            ParentConnectionService.stop(appContext)
            lastServiceText = ""
            holder.applyDisconnectedState(toastMessage = "Child device ended the session")
            return
        }

        if (trimmed == "STEALTH_ON_ACK" || trimmed == "STEALTH_STATUS:ON") {
            updateCachedStealthFlag(true)
            holder.onStealthStatusSynced(
                status = StealthStatus.ACTIVE,
                toastMessage = if (trimmed == "STEALTH_ON_ACK") "Child app icon hidden (Stealth Active)" else null
            )
            return
        }

        if (trimmed == "STEALTH_OFF_ACK" || trimmed == "STEALTH_STATUS:OFF") {
            updateCachedStealthFlag(false)
            holder.onStealthStatusSynced(
                status = StealthStatus.ARMED,
                toastMessage = if (trimmed == "STEALTH_OFF_ACK") "Child app icon visible (Stealth Off)" else null
            )
            return
        }

        val telemetryUpdate = TelemetryParser.parsePacket(trimmed)
        if (telemetryUpdate != null) {
            val baseRecord = activeSessionDevice ?: holder.uiState.value.connectedDevice ?: return
            val updatedRecord = TelemetryParser.applyUpdate(baseRecord, telemetryUpdate)
            activeSessionDevice = updatedRecord
            val updatedHistory = storage.savePairedDevice(updatedRecord)
            holder.onTelemetryUpdated(
                record = updatedRecord,
                history = updatedHistory
            )
            alertsController.evaluateLowBattery(updatedRecord, holder.uiState.value.settings)
            return
        }

        monitorCoordinator.handleIncomingMessage(trimmed)
    }

    private fun updateCachedStealthFlag(isStealth: Boolean) {
        val current = activeSessionDevice ?: return
        val updated = current.copy(
            telemetry = current.telemetry.copy(
                isStealthMode = isStealth,
                securityGuardsText = TelemetryParser.formatSecurityGuards(
                    isAppLockEnabled = current.telemetry.isAppLockEnabled,
                    isStealthMode = isStealth
                )
            )
        )
        activeSessionDevice = updated
        storage.savePairedDevice(updated)
    }

    private fun updateServiceNotification(title: String, body: String) {
        val composite = "$title|$body|$includeMicFgsType"
        if (composite == lastServiceText) return
        lastServiceText = composite
        ParentConnectionService.startOrUpdate(
            context = appContext,
            title = title,
            body = body,
            includeMicrophoneFgsType = includeMicFgsType
        )
    }
}
