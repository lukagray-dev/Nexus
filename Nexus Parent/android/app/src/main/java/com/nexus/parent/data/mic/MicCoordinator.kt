package com.nexus.parent.data.mic

import com.nexus.parent.data.alerts.GeneralAlertsController
import com.nexus.parent.media.mic.AudioSpectrumAnalyzer
import com.nexus.parent.media.mic.VoiceChangerDspEngine
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.MicUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import com.nexus.parent.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioCaptureApplyResult(
    val success: Boolean,
    val fellBackToDefault: Boolean = false
)

/**
 * MicMediaBridge - Abstraction decoupling [MicCoordinator] from WebRTC/AudioManager hardware
 * so full protocol & DSP workflows can be tested deterministically on the JVM.
 */
interface MicMediaBridge {
    val dspEngine: VoiceChangerDspEngine
    val spectrumAnalyzer: AudioSpectrumAnalyzer
    fun setRemoteAudioEnabled(enabled: Boolean)
    fun startOutboundAudioTrack(noiseSuppression: Boolean, inputDeviceId: String): AudioCaptureApplyResult
    fun stopOutboundAudioTrack()
    fun reconfigureOutboundAudio(noiseSuppression: Boolean, inputDeviceId: String): AudioCaptureApplyResult
    fun engageVoiceRoute(outputDeviceId: String): AudioRouteApplyResult
    fun applyOutputRoute(outputDeviceId: String): AudioRouteApplyResult
    fun releaseVoiceRoute()
    fun onSpeechServiceActive(active: Boolean)
}

class WebRtcMicMediaBridge(
    private val webRtcManager: WebRtcManager,
    private val routeManager: ParentAudioRouteManager,
    private val catalog: AudioDeviceCatalog,
    private val onSpeechFgsChanged: (Boolean) -> Unit
) : MicMediaBridge {
    override val dspEngine: VoiceChangerDspEngine get() = webRtcManager.voiceChangerDsp
    override val spectrumAnalyzer: AudioSpectrumAnalyzer get() = webRtcManager.audioSpectrumAnalyzer

    override fun setRemoteAudioEnabled(enabled: Boolean) = webRtcManager.setRemoteAudioEnabled(enabled)

    override fun startOutboundAudioTrack(
        noiseSuppression: Boolean,
        inputDeviceId: String
    ): AudioCaptureApplyResult {
        val normalized = inputDeviceId.trim().ifEmpty { AudioDeviceMapper.DEFAULT_DEVICE_ID }
        val resolved = catalog.resolveInputDevice(normalized)
        val fellBack = normalized != AudioDeviceMapper.DEFAULT_DEVICE_ID && resolved == null
        val ok = webRtcManager.startOutboundAudioTrack(noiseSuppression, resolved)
        return AudioCaptureApplyResult(success = ok, fellBackToDefault = fellBack)
    }

    override fun stopOutboundAudioTrack() = webRtcManager.stopOutboundAudioTrack()

    override fun reconfigureOutboundAudio(
        noiseSuppression: Boolean,
        inputDeviceId: String
    ): AudioCaptureApplyResult {
        val normalized = inputDeviceId.trim().ifEmpty { AudioDeviceMapper.DEFAULT_DEVICE_ID }
        val resolved = catalog.resolveInputDevice(normalized)
        val fellBack = normalized != AudioDeviceMapper.DEFAULT_DEVICE_ID && resolved == null
        val ok = webRtcManager.reconfigureOutboundAudio(noiseSuppression, resolved)
        return AudioCaptureApplyResult(success = ok, fellBackToDefault = fellBack)
    }

    override fun engageVoiceRoute(outputDeviceId: String): AudioRouteApplyResult =
        routeManager.engageVoiceRoute(outputDeviceId)

    override fun applyOutputRoute(outputDeviceId: String): AudioRouteApplyResult =
        routeManager.applyOutputRoute(outputDeviceId)

    override fun releaseVoiceRoute() = routeManager.releaseVoiceRoute()
    override fun onSpeechServiceActive(active: Boolean) = onSpeechFgsChanged(active)
}

/**
 * MicCoordinator.kt - Live Child Mic Listener, Parent-to-Child Broadcast Intercom, Voice Changer DSP,
 * 36-Bar Spectrum Analyzer & Call Alert Mic Auto-Start (`mic.ts` + `parent-audio-controller.ts`).
 *
 * Hey friend! Think of this class as the conductor of all bidirectional audio workflows:
 * - **Listen (Child -> Parent)**: Tapping Play sends `MIC_ON` and switches the button into an
 *   intermediate loading spinner (`STARTING`) until Child confirms with `MIC_STARTED`, then plays
 *   Child's mic audio and animates the 36-bar spectrum. Closing the floating HUD keeps listening alive!
 * - **Speak (Parent -> Child)**: Checks `RECORD_AUDIO` permission, configures the 10-preset Kotlin
 *   DSP engine, attaches Parent's outbound WebRTC track cleanly without renegotiation, sends
 *   `PARENT_AUDIO_UNMUTE:<max|high|normal>`, shows a loading spinner until `PARENT_AUDIO_UNMUTED`,
 *   and allows zero-drop live hot-switching across all 10 voice presets.
 */
class MicCoordinator(
    private val scope: CoroutineScope,
    private val mediaBridge: MicMediaBridge,
    private val sendCommand: (String) -> Boolean,
    private val onPersistSettings: (SettingsValues) -> Unit,
    private val alertsController: GeneralAlertsController? = null
) {

    private val _recordAudioPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val recordAudioPermissionRequests: SharedFlow<Unit> = _recordAudioPermissionRequests.asSharedFlow()

    private var stateHolder: ParentStateHolder? = null
    private var listenTimeoutJob: Job? = null
    private var speakTimeoutJob: Job? = null
    private var spectrumCollectorJob: Job? = null
    private var speakPulseJob: Job? = null

    private var pendingSpeakAfterPermission = false
    private var lastAppliedSettings: SettingsValues? = null

    fun attachStateHolder(holder: ParentStateHolder) {
        stateHolder = holder
        lastAppliedSettings = holder.uiState.value.settings
        mediaBridge.dspEngine.setPreset(holder.uiState.value.settings.defaultVoicePreset)

        spectrumCollectorJob?.cancel()
        spectrumCollectorJob = scope.launch {
            mediaBridge.spectrumAnalyzer.bars.collect { fftBars ->
                val current = stateHolder?.uiState?.value?.mic ?: return@collect
                // Speaking animation takes visual precedence in green while parent talks;
                // otherwise show live FFT spectrum from Child's remote mic PCM.
                if (current.speakState != MonitoringState.LIVE) {
                    stateHolder?.updateMicState { it.copy(spectrumBars = fftBars) }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // FLOATING OVERLAY & LISTEN CONTROLS
    // -------------------------------------------------------------------------

    fun setOverlayOpen(open: Boolean) {
        val holder = stateHolder ?: return
        // Just like Desktop: minimizing closes only the panel & dropdown, preserving live audio stream!
        holder.updateMicState {
            it.copy(isOverlayOpen = open, isVoiceMenuExpanded = false)
        }
    }

    fun startChildMicMonitoring(): Boolean {
        val holder = stateHolder ?: return false
        holder.updateMicState { it.copy(isOverlayOpen = true, isVoiceMenuExpanded = false) }
        val state = holder.uiState.value.mic.listenState
        if (state == MonitoringState.STARTING || state == MonitoringState.LIVE) {
            return true
        }
        return startListening(holder)
    }

    fun toggleListen() {
        val holder = stateHolder ?: return
        holder.updateMicState { it.copy(isVoiceMenuExpanded = false) }
        when (holder.uiState.value.mic.listenState) {
            MonitoringState.IDLE -> startListening(holder)
            MonitoringState.LIVE -> stopListening(holder)
            MonitoringState.STARTING, MonitoringState.STOPPING -> Unit
        }
    }

    private fun startListening(holder: ParentStateHolder): Boolean {
        holder.updateMicState { it.copy(listenState = MonitoringState.STARTING) }
        if (!sendCommand(MicProtocol.CMD_MIC_ON)) {
            holder.updateMicState { it.copy(listenState = MonitoringState.IDLE) }
            holder.showToast("Cannot start microphone: DataChannel not connected")
            return false
        }

        listenTimeoutJob?.cancel()
        listenTimeoutJob = scope.launch {
            delay(MicProtocol.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.mic.listenState == MonitoringState.STARTING) {
                holder.updateMicState { it.copy(listenState = MonitoringState.IDLE) }
                holder.showToast("Child microphone confirmation timed out")
            }
        }
        return true
    }

    private fun stopListening(holder: ParentStateHolder) {
        holder.updateMicState { it.copy(listenState = MonitoringState.STOPPING) }
        if (!sendCommand(MicProtocol.CMD_MIC_OFF)) {
            holder.updateMicState { it.copy(listenState = MonitoringState.LIVE) }
            holder.showToast("Cannot stop microphone: DataChannel not connected")
            return
        }

        listenTimeoutJob?.cancel()
        listenTimeoutJob = scope.launch {
            delay(MicProtocol.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.mic.listenState == MonitoringState.STOPPING) {
                // Force local playback off if link dropped silently while waiting
                disableListenPipeline(holder)
                holder.showToast("Timed out waiting for child mic stop confirmation")
            }
        }
    }

    // -------------------------------------------------------------------------
    // PARENT-TO-CHILD BROADCAST INTERCOM CONTROLS
    // -------------------------------------------------------------------------

    fun toggleSpeak(hasRecordAudioPermission: Boolean) {
        val holder = stateHolder ?: return
        holder.updateMicState { it.copy(isVoiceMenuExpanded = false) }

        when (holder.uiState.value.mic.speakState) {
            MonitoringState.IDLE -> {
                if (!hasRecordAudioPermission) {
                    pendingSpeakAfterPermission = true
                    _recordAudioPermissionRequests.tryEmit(Unit)
                    return
                }
                startSpeaking(holder)
            }
            MonitoringState.LIVE -> stopSpeaking(holder)
            MonitoringState.STARTING, MonitoringState.STOPPING -> Unit
        }
    }

    fun onRecordAudioPermissionResult(granted: Boolean) {
        val shouldStart = pendingSpeakAfterPermission
        pendingSpeakAfterPermission = false
        val holder = stateHolder ?: return

        if (granted && shouldStart) {
            startSpeaking(holder)
        } else if (!granted && shouldStart) {
            holder.showToast("Microphone permission is required to broadcast voice")
        }
    }

    private fun startSpeaking(holder: ParentStateHolder) {
        val settings = holder.uiState.value.settings
        holder.updateMicState { it.copy(speakState = MonitoringState.STARTING, isVoiceMenuExpanded = false) }

        mediaBridge.dspEngine.setPreset(holder.uiState.value.mic.selectedVoice)
        mediaBridge.dspEngine.setBypass(false)
        val routeResult = mediaBridge.engageVoiceRoute(settings.outputDeviceId)
        mediaBridge.onSpeechServiceActive(true)

        val captureResult = mediaBridge.startOutboundAudioTrack(
            noiseSuppression = settings.noiseSuppression,
            inputDeviceId = settings.inputDeviceId
        )
        if (!captureResult.success) {
            cleanupOutboundSpeakHardware(holder)
            holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
            holder.showToast("Unable to capture microphone for broadcast")
            return
        }

        if (captureResult.fellBackToDefault) {
            holder.showToast("Saved microphone disconnected — using System Default")
        } else if (routeResult.fellBackToDefault) {
            holder.showToast("Saved speaker disconnected — using System Default")
        }

        val cmd = MicProtocol.buildParentAudioUnmuteCommand(settings.loudspeakerVolume)
        if (!sendCommand(cmd)) {
            cleanupOutboundSpeakHardware(holder)
            holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
            holder.showToast("Cannot broadcast: DataChannel not connected")
            return
        }

        speakTimeoutJob?.cancel()
        speakTimeoutJob = scope.launch {
            delay(MicProtocol.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.mic.speakState == MonitoringState.STARTING) {
                sendCommand(MicProtocol.CMD_PARENT_AUDIO_MUTE)
                cleanupOutboundSpeakHardware(holder)
                holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
                holder.showToast("Child loudspeaker confirmation timed out")
            }
        }
    }

    private fun stopSpeaking(holder: ParentStateHolder) {
        holder.updateMicState { it.copy(speakState = MonitoringState.STOPPING, isVoiceMenuExpanded = false) }
        cleanupOutboundSpeakHardware(holder)

        if (!sendCommand(MicProtocol.CMD_PARENT_AUDIO_MUTE)) {
            holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
            return
        }

        speakTimeoutJob?.cancel()
        speakTimeoutJob = scope.launch {
            delay(MicProtocol.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.mic.speakState == MonitoringState.STOPPING) {
                holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // VOICE CHANGER DSP & SETTINGS SYNCHRONIZATION
    // -------------------------------------------------------------------------

    fun selectVoicePreset(preset: VoicePresetId) {
        val holder = stateHolder ?: return
        mediaBridge.dspEngine.setPreset(preset)
        val updatedSettings = holder.uiState.value.settings.copy(defaultVoicePreset = preset)
        lastAppliedSettings = updatedSettings

        holder.updateMicState {
            it.copy(selectedVoice = preset, isVoiceMenuExpanded = false)
        }
        holder.applyPersistedSettingsSnapshot(updatedSettings, toastMessage = "Voice preset: ${preset.displayName}")
        onPersistSettings(updatedSettings)
    }

    fun onSettingsUpdated(next: SettingsValues) {
        val prev = lastAppliedSettings
        lastAppliedSettings = next
        val holder = stateHolder ?: return

        if (prev?.defaultVoicePreset != next.defaultVoicePreset) {
            mediaBridge.dspEngine.setPreset(next.defaultVoicePreset)
            holder.updateMicState { it.copy(selectedVoice = next.defaultVoicePreset) }
        }

        val speakState = holder.uiState.value.mic.speakState
        val listenState = holder.uiState.value.mic.listenState
        val isSpeaking = speakState == MonitoringState.LIVE || speakState == MonitoringState.STARTING
        val isListening = listenState == MonitoringState.LIVE || listenState == MonitoringState.STARTING

        if (isSpeaking && prev?.loudspeakerVolume != next.loudspeakerVolume) {
            sendCommand(MicProtocol.buildParentAudioVolumeCommand(next.loudspeakerVolume))
        }

        if (isSpeaking &&
            (prev?.noiseSuppression != next.noiseSuppression || prev?.inputDeviceId != next.inputDeviceId)
        ) {
            val captureResult = mediaBridge.reconfigureOutboundAudio(next.noiseSuppression, next.inputDeviceId)
            if (!captureResult.success) {
                holder.showToast("Unable to update microphone input settings mid-speech")
            } else if (captureResult.fellBackToDefault) {
                holder.showToast("Saved microphone disconnected — using System Default")
            }
        }

        if ((isSpeaking || isListening) && prev?.outputDeviceId != next.outputDeviceId) {
            val routeResult = mediaBridge.applyOutputRoute(next.outputDeviceId)
            if (routeResult.fellBackToDefault) {
                holder.showToast("Saved speaker disconnected — using System Default")
            }
        }
    }

    // -------------------------------------------------------------------------
    // CALL ALERT ACTION & DATACHANNEL EVENT HANDLING
    // -------------------------------------------------------------------------

    fun acceptCallAlertAndListenMic() {
        val holder = stateHolder ?: return
        holder.dismissCallAlert()
        startChildMicMonitoring()
    }

    fun handleIncomingMessage(rawMessage: String): Boolean {
        val holder = stateHolder ?: return false
        val trimmed = rawMessage.trim()

        when {
            trimmed == MicProtocol.RESP_MIC_STARTED -> {
                listenTimeoutJob?.cancel()
                val routeResult = mediaBridge.engageVoiceRoute(holder.uiState.value.settings.outputDeviceId)
                if (routeResult.fellBackToDefault) {
                    holder.showToast("Saved speaker disconnected — using System Default")
                }
                mediaBridge.setRemoteAudioEnabled(true)
                mediaBridge.spectrumAnalyzer.setListeningActive(true)
                alertsController?.onMicListeningActivated()
                holder.setActiveCallAlert(null)
                holder.updateMicState { it.copy(listenState = MonitoringState.LIVE) }
                holder.setFeatureLive(FeatureId.MIC, isLive = true)
                return true
            }

            trimmed == MicProtocol.RESP_MIC_STOPPED -> {
                listenTimeoutJob?.cancel()
                disableListenPipeline(holder)
                return true
            }

            trimmed.startsWith(MicProtocol.RESP_MIC_ERROR_PREFIX) -> {
                listenTimeoutJob?.cancel()
                val err = trimmed.removePrefix(MicProtocol.RESP_MIC_ERROR_PREFIX).trim()
                val current = holder.uiState.value.mic.listenState
                val reverted = when (current) {
                    MonitoringState.STARTING -> MonitoringState.IDLE
                    MonitoringState.STOPPING -> MonitoringState.LIVE
                    else -> current
                }
                if (reverted == MonitoringState.IDLE) {
                    disableListenPipeline(holder)
                } else {
                    holder.updateMicState { it.copy(listenState = reverted) }
                }
                if (err.isNotBlank()) holder.showToast("Mic error: $err")
                return true
            }

            trimmed == MicProtocol.RESP_PARENT_AUDIO_UNMUTED -> {
                speakTimeoutJob?.cancel()
                holder.updateMicState { it.copy(speakState = MonitoringState.LIVE) }
                startSpeakingPulseTicker(holder)
                return true
            }

            trimmed == MicProtocol.RESP_PARENT_AUDIO_MUTED -> {
                speakTimeoutJob?.cancel()
                cleanupOutboundSpeakHardware(holder)
                holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
                return true
            }

            trimmed.startsWith(MicProtocol.RESP_PARENT_AUDIO_ERROR_PREFIX) -> {
                speakTimeoutJob?.cancel()
                val err = trimmed.removePrefix(MicProtocol.RESP_PARENT_AUDIO_ERROR_PREFIX).trim()
                cleanupOutboundSpeakHardware(holder)
                holder.updateMicState { it.copy(speakState = MonitoringState.IDLE) }
                if (err.isNotBlank()) holder.showToast("Intercom error: $err")
                return true
            }

            trimmed.startsWith(MicProtocol.RESP_PARENT_AUDIO_VOLUME_SET_PREFIX) -> {
                return true
            }
        }

        val callPacket = MicProtocol.parseCallStatePacket(trimmed)
        if (callPacket != null) {
            val current = holder.uiState.value
            val isMicActive = current.mic.listenState == MonitoringState.LIVE ||
                current.mic.listenState == MonitoringState.STARTING
            val nextAlert = if (alertsController != null) {
                alertsController.evaluateCallState(
                    packet = callPacket,
                    settings = current.settings,
                    isMicListeningOrStarting = isMicActive,
                    deviceName = current.connectedDevice?.name
                )
            } else if (!current.settings.alertChildActiveCall || callPacket.state == "idle" || isMicActive) {
                null
            } else {
                MicProtocol.toActiveCallAlert(callPacket)
            }
            holder.setActiveCallAlert(nextAlert)
            return true
        }

        return false
    }

    fun onPeerDisconnected() {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        speakTimeoutJob?.cancel()
        speakTimeoutJob = null
        pendingSpeakAfterPermission = false

        val holder = stateHolder ?: return
        disableListenPipeline(holder)
        cleanupOutboundSpeakHardware(holder)
        mediaBridge.dspEngine.reset()
        mediaBridge.releaseVoiceRoute()
        holder.setActiveCallAlert(null)
        holder.updateMicState {
            MicUiState(
                isOverlayOpen = it.isOverlayOpen,
                listenState = MonitoringState.IDLE,
                speakState = MonitoringState.IDLE,
                selectedVoice = holder.uiState.value.settings.defaultVoicePreset,
                isVoiceMenuExpanded = false,
                spectrumBars = AudioSpectrumAnalyzer.IDLE_BARS
            )
        }
    }

    // -------------------------------------------------------------------------
    // INTERNAL PIPELINE HELPERS
    // -------------------------------------------------------------------------

    private fun disableListenPipeline(holder: ParentStateHolder) {
        mediaBridge.setRemoteAudioEnabled(false)
        mediaBridge.spectrumAnalyzer.setListeningActive(false)
        holder.updateMicState {
            it.copy(
                listenState = MonitoringState.IDLE,
                spectrumBars = if (it.speakState == MonitoringState.LIVE) it.spectrumBars else AudioSpectrumAnalyzer.IDLE_BARS
            )
        }
        holder.setFeatureLive(FeatureId.MIC, isLive = false)
        if (holder.uiState.value.mic.speakState == MonitoringState.IDLE) {
            mediaBridge.releaseVoiceRoute()
        }
    }

    private fun cleanupOutboundSpeakHardware(holder: ParentStateHolder) {
        speakPulseJob?.cancel()
        speakPulseJob = null
        mediaBridge.dspEngine.setBypass(true)
        mediaBridge.stopOutboundAudioTrack()
        mediaBridge.onSpeechServiceActive(false)

        val micState = holder.uiState.value.mic
        if (micState.listenState == MonitoringState.IDLE) {
            mediaBridge.releaseVoiceRoute()
            holder.updateMicState { it.copy(spectrumBars = AudioSpectrumAnalyzer.IDLE_BARS) }
        }
    }

    private fun startSpeakingPulseTicker(holder: ParentStateHolder) {
        speakPulseJob?.cancel()
        speakPulseJob = scope.launch {
            var phaseRad = 0.0
            while (isActive && holder.uiState.value.mic.speakState == MonitoringState.LIVE) {
                val level = mediaBridge.dspEngine.getOutboundRmsLevel()
                val bars = mediaBridge.spectrumAnalyzer.buildSpeakingPulseBars(level, phaseRad)
                holder.updateMicState { it.copy(spectrumBars = bars) }
                phaseRad += 0.35
                delay(33L)
            }
        }
    }
}
