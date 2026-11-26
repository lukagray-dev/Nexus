package com.nexus.parent.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexus.parent.media.ClipExportManager
import com.nexus.parent.media.ClipExportOutcome
import com.nexus.parent.media.OpusAudioPlayer
import com.nexus.parent.media.RollingDvrEngine
import com.nexus.parent.state.CameraFeedUiState
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.LocationUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.ScreenFeedUiState
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.ui.surveillance.location.NominatimGeocoder
import com.nexus.parent.webrtc.VideoFeedType
import com.nexus.parent.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PendingDocumentExportRequest(
    val fileName: String,
    val mimeType: String
)

/**
 * SurveillanceCoordinator.kt - Coordinates Camera, Screen (+ Opus System Audio + Rolling DVR),
 * and Location Tracking (+ Nominatim reverse geocoding) 1:1 with Desktop `camera.ts`, `screen.ts`,
 * and `location.ts`.
 *
 * Hey friend! Keeping this sibling coordinator alongside `MonitorCoordinator` keeps every
 * streaming state machine (`IDLE -> STARTING -> LIVE -> STOPPING -> IDLE`), 20-second timeout
 * safety guard, and exclusive video transceiver handover crystal-clear and isolated.
 */
class SurveillanceCoordinator(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val webRtcManager: WebRtcManager
) {

    companion object {
        private const val TAG = "SurveillanceCoordinator"
    }

    private val opusAudioPlayer = OpusAudioPlayer()
    private val rollingDvrEngine = RollingDvrEngine(appContext)
    private val nominatimGeocoder = NominatimGeocoder()

    private val _pendingExportRequests = MutableSharedFlow<PendingDocumentExportRequest>(extraBufferCapacity = 2)
    val pendingExportRequests: SharedFlow<PendingDocumentExportRequest> = _pendingExportRequests.asSharedFlow()

    private var stateHolder: ParentStateHolder? = null
    private var cameraTimeoutJob: Job? = null
    private var screenTimeoutJob: Job? = null
    private var locationTimeoutJob: Job? = null
    private var dvrTickerJob: Job? = null
    private var locationPingTickerJob: Job? = null

    private var activePlaybackSegmentIndex: Int = -1
    private var pendingStagedFile: File? = null
    private var lastAppliedSettings: SettingsValues? = null

    init {
        scope.launch {
            webRtcManager.activeVideoTrack.collectLatest { track ->
                val holder = stateHolder ?: return@collectLatest
                val state = holder.uiState.value
                val liveType = when {
                    state.camera.state == MonitoringState.LIVE -> VideoFeedType.CAMERA
                    state.screen.state == MonitoringState.LIVE -> VideoFeedType.SCREEN
                    else -> null
                }
                if (track != null && liveType != null) {
                    startDvrForTrack(liveType, track)
                } else {
                    rollingDvrEngine.stop()
                }
            }
        }
    }

    fun attachStateHolder(holder: ParentStateHolder) {
        stateHolder = holder
        lastAppliedSettings = holder.uiState.value.settings
    }

    fun onSettingsUpdated(next: SettingsValues) {
        val prev = lastAppliedSettings
        lastAppliedSettings = next
        val holder = stateHolder ?: return

        if (prev?.dvrBufferDuration != next.dvrBufferDuration) {
            rollingDvrEngine.updateRetention(next.dvrBufferDuration)
        }

        if (prev?.screenQuality != next.screenQuality && holder.uiState.value.screen.state == MonitoringState.LIVE) {
            webRtcManager.sendCommand(SurveillanceProtocols.buildScreenQualityCommand(next.screenQuality))
        }
    }

    // -------------------------------------------------------------------------
    // CAMERA FEED CONTROLS & CONFIRMATION STATE MACHINE
    // -------------------------------------------------------------------------

    fun toggleCameraStream() {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.camera.state
        when (current) {
            MonitoringState.IDLE -> startCameraStream(holder)
            MonitoringState.LIVE -> stopCameraStream(holder)
            MonitoringState.STARTING, MonitoringState.STOPPING -> Unit
        }
    }

    private fun startCameraStream(holder: ParentStateHolder) {
        val currentUi = holder.uiState.value
        val activeScreenState = currentUi.screen.state
        if (activeScreenState == MonitoringState.LIVE || activeScreenState == MonitoringState.STARTING) {
            // Single shared video transceiver: yield Screen cleanly first (matches Desktop)
            webRtcManager.sendCommand(SurveillanceProtocols.CMD_SCREEN_OFF)
            resetScreenFeedToIdle(holder, showSwitchedToast = "Screen feed paused to switch to camera")
        }

        webRtcManager.setActiveVideoType(VideoFeedType.CAMERA)
        holder.updateCameraState {
            it.copy(
                state = MonitoringState.STARTING,
                dvrProgress = 1f,
                isAtLiveEdge = true,
                elapsedLabel = "00:00",
                hasDvrClips = false
            )
        }

        val cmd = SurveillanceProtocols.buildCameraOnCommand(holder.uiState.value.camera.lens)
        if (!webRtcManager.sendCommand(cmd)) {
            webRtcManager.setActiveVideoType(null)
            holder.updateCameraState { it.copy(state = MonitoringState.IDLE) }
            holder.showToast("Cannot start Camera: DataChannel not connected")
            return
        }

        cameraTimeoutJob?.cancel()
        cameraTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.camera.state == MonitoringState.STARTING) {
                Log.w(TAG, "Timeout waiting for CAMERA_STARTED; reverting to IDLE")
                webRtcManager.setActiveVideoType(null)
                holder.updateCameraState { it.copy(state = MonitoringState.IDLE) }
                holder.showToast("Camera start timed out")
            }
        }
    }

    private fun stopCameraStream(holder: ParentStateHolder) {
        holder.updateCameraState { it.copy(state = MonitoringState.STOPPING) }
        if (!webRtcManager.sendCommand(SurveillanceProtocols.CMD_CAMERA_OFF)) {
            holder.updateCameraState { it.copy(state = MonitoringState.LIVE) }
            holder.showToast("Cannot stop Camera: DataChannel closed")
            return
        }

        cameraTimeoutJob?.cancel()
        cameraTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.camera.state == MonitoringState.STOPPING) {
                Log.w(TAG, "Timeout waiting for CAMERA_STOPPED; reverting to LIVE")
                holder.updateCameraState { it.copy(state = MonitoringState.LIVE) }
                holder.showToast("Camera stop timed out")
            }
        }
    }

    fun toggleCameraLens() {
        val holder = stateHolder ?: return
        if (holder.uiState.value.camera.isSwitchingLens) return

        holder.updateCameraState { it.copy(isSwitchingLens = true) }
        if (!webRtcManager.sendCommand(SurveillanceProtocols.CMD_CAMERA_SWITCH)) {
            holder.updateCameraState { it.copy(isSwitchingLens = false) }
            holder.showToast("Cannot switch lens: DataChannel not connected")
            return
        }

        cameraTimeoutJob?.cancel()
        cameraTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.camera.isSwitchingLens) {
                holder.updateCameraState { it.copy(isSwitchingLens = false) }
                holder.showToast("Lens switch timed out")
            }
        }
    }

    // -------------------------------------------------------------------------
    // SCREEN MIRRORING + INTERNAL OPUS SYSTEM AUDIO CONTROLS
    // -------------------------------------------------------------------------

    fun toggleScreenStream() {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.screen.state
        when (current) {
            MonitoringState.IDLE -> startScreenStream(holder)
            MonitoringState.LIVE -> stopScreenStream(holder)
            MonitoringState.STARTING, MonitoringState.STOPPING -> Unit
        }
    }

    private fun startScreenStream(holder: ParentStateHolder) {
        val currentUi = holder.uiState.value
        val activeCameraState = currentUi.camera.state
        if (activeCameraState == MonitoringState.LIVE || activeCameraState == MonitoringState.STARTING) {
            webRtcManager.sendCommand(SurveillanceProtocols.CMD_CAMERA_OFF)
            resetCameraFeedToIdle(holder, showSwitchedToast = "Camera feed paused to switch to screen")
        }

        webRtcManager.setActiveVideoType(VideoFeedType.SCREEN)
        holder.updateScreenState {
            it.copy(
                state = MonitoringState.STARTING,
                dvrProgress = 1f,
                isAtLiveEdge = true,
                elapsedLabel = "00:00",
                hasDvrClips = false
            )
        }

        val cmd = SurveillanceProtocols.buildScreenOnCommand(currentUi.settings.screenQuality)
        if (!webRtcManager.sendCommand(cmd)) {
            webRtcManager.setActiveVideoType(null)
            holder.updateScreenState { it.copy(state = MonitoringState.IDLE) }
            holder.showToast("Cannot start Screen Mirror: DataChannel not connected")
            return
        }

        screenTimeoutJob?.cancel()
        screenTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.screen.state == MonitoringState.STARTING) {
                Log.w(TAG, "Timeout waiting for SCREEN_STARTED; reverting to IDLE")
                webRtcManager.setActiveVideoType(null)
                holder.updateScreenState { it.copy(state = MonitoringState.IDLE) }
                holder.showToast("Screen mirror start timed out")
            }
        }
    }

    private fun stopScreenStream(holder: ParentStateHolder) {
        holder.updateScreenState { it.copy(state = MonitoringState.STOPPING) }
        if (!webRtcManager.sendCommand(SurveillanceProtocols.CMD_SCREEN_OFF)) {
            holder.updateScreenState { it.copy(state = MonitoringState.LIVE) }
            holder.showToast("Cannot stop Screen Mirror: DataChannel closed")
            return
        }

        screenTimeoutJob?.cancel()
        screenTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.screen.state == MonitoringState.STOPPING) {
                Log.w(TAG, "Timeout waiting for SCREEN_STOPPED; reverting to LIVE")
                holder.updateScreenState { it.copy(state = MonitoringState.LIVE) }
                holder.showToast("Screen mirror stop timed out")
            }
        }
    }

    fun toggleSystemAudio() {
        val holder = stateHolder ?: return
        val nextEnabled = !holder.uiState.value.screen.isSystemAudioEnabled
        holder.updateScreenState { it.copy(isSystemAudioEnabled = nextEnabled) }
        opusAudioPlayer.setMuted(!nextEnabled)
        holder.showToast(if (nextEnabled) "System audio enabled" else "System audio muted")
    }

    // -------------------------------------------------------------------------
    // DVR SCRUBBING, PLAYBACK ADVANCEMENT & CLIP EXPORT
    // -------------------------------------------------------------------------

    fun scrubDvr(feedType: VideoFeedType, fraction: Float) {
        val holder = stateHolder ?: return
        val resolution = rollingDvrEngine.resolveSeek(fraction)
        if (resolution.isLiveEdge || resolution.segmentFile == null) {
            jumpToLive(feedType)
            return
        }

        activePlaybackSegmentIndex = resolution.segmentIndex
        val totalSec = rollingDvrEngine.getProgressSnapshot().totalSeconds.coerceAtLeast(1f)
        val curLabel = SurveillanceProtocols.formatElapsedSeconds(resolution.playbackSeconds.toInt())
        val totLabel = SurveillanceProtocols.formatElapsedSeconds(totalSec.toInt())
        val triggerId = System.nanoTime()

        when (feedType) {
            VideoFeedType.CAMERA -> holder.updateCameraState {
                it.copy(
                    isAtLiveEdge = false,
                    dvrProgress = (resolution.playbackSeconds / totalSec).coerceIn(0f, 0.99f),
                    dvrCurrentSeconds = resolution.playbackSeconds,
                    dvrTotalSeconds = totalSec,
                    elapsedLabel = "$curLabel / $totLabel",
                    dvrActiveSegmentPath = resolution.segmentFile.absolutePath,
                    dvrSegmentOffsetMs = resolution.offsetWithinSegmentMs,
                    dvrSeekTriggerId = triggerId
                )
            }
            VideoFeedType.SCREEN -> holder.updateScreenState {
                it.copy(
                    isAtLiveEdge = false,
                    dvrProgress = (resolution.playbackSeconds / totalSec).coerceIn(0f, 0.99f),
                    dvrCurrentSeconds = resolution.playbackSeconds,
                    dvrTotalSeconds = totalSec,
                    elapsedLabel = "$curLabel / $totLabel",
                    dvrActiveSegmentPath = resolution.segmentFile.absolutePath,
                    dvrSegmentOffsetMs = resolution.offsetWithinSegmentMs,
                    dvrSeekTriggerId = triggerId
                )
            }
        }
    }

    fun jumpToLive(feedType: VideoFeedType) {
        val holder = stateHolder ?: return
        activePlaybackSegmentIndex = -1
        val totalSec = rollingDvrEngine.getProgressSnapshot().totalSeconds
        val liveLabel = SurveillanceProtocols.formatElapsedSeconds(totalSec.toInt())

        when (feedType) {
            VideoFeedType.CAMERA -> holder.updateCameraState {
                it.copy(
                    isAtLiveEdge = true,
                    dvrProgress = 1f,
                    dvrActiveSegmentPath = null,
                    dvrSegmentOffsetMs = 0,
                    elapsedLabel = liveLabel
                )
            }
            VideoFeedType.SCREEN -> holder.updateScreenState {
                it.copy(
                    isAtLiveEdge = true,
                    dvrProgress = 1f,
                    dvrActiveSegmentPath = null,
                    dvrSegmentOffsetMs = 0,
                    elapsedLabel = liveLabel
                )
            }
        }
    }

    fun onDvrSegmentPlaybackPosition(feedType: VideoFeedType, positionInSegmentMs: Int) {
        val holder = stateHolder ?: return
        val seg = rollingDvrEngine.getSegmentAt(activePlaybackSegmentIndex) ?: return
        val totalSec = rollingDvrEngine.getProgressSnapshot().totalSeconds.coerceAtLeast(1f)
        val globalSec = ((seg.startElapsedMs + positionInSegmentMs) / 1000f).coerceIn(0f, totalSec)
        val curLabel = SurveillanceProtocols.formatElapsedSeconds(globalSec.toInt())
        val totLabel = SurveillanceProtocols.formatElapsedSeconds(totalSec.toInt())
        val progress = (globalSec / totalSec).coerceIn(0f, 0.99f)

        when (feedType) {
            VideoFeedType.CAMERA -> if (!holder.uiState.value.camera.isAtLiveEdge) {
                holder.updateCameraState {
                    it.copy(dvrProgress = progress, dvrCurrentSeconds = globalSec, elapsedLabel = "$curLabel / $totLabel")
                }
            }
            VideoFeedType.SCREEN -> if (!holder.uiState.value.screen.isAtLiveEdge) {
                holder.updateScreenState {
                    it.copy(dvrProgress = progress, dvrCurrentSeconds = globalSec, elapsedLabel = "$curLabel / $totLabel")
                }
            }
        }
    }

    fun onDvrSegmentCompleted(feedType: VideoFeedType) {
        val holder = stateHolder ?: return
        val nextIdx = activePlaybackSegmentIndex + 1
        val nextSeg = rollingDvrEngine.getSegmentAt(nextIdx)
        if (nextSeg == null) {
            jumpToLive(feedType)
            return
        }
        activePlaybackSegmentIndex = nextIdx
        val triggerId = System.nanoTime()
        when (feedType) {
            VideoFeedType.CAMERA -> holder.updateCameraState {
                it.copy(
                    dvrActiveSegmentPath = nextSeg.file.absolutePath,
                    dvrSegmentOffsetMs = 0,
                    dvrSeekTriggerId = triggerId
                )
            }
            VideoFeedType.SCREEN -> holder.updateScreenState {
                it.copy(
                    dvrActiveSegmentPath = nextSeg.file.absolutePath,
                    dvrSegmentOffsetMs = 0,
                    dvrSeekTriggerId = triggerId
                )
            }
        }
    }

    fun exportActiveDvrClip(feedType: VideoFeedType) {
        val holder = stateHolder ?: return
        val isAlreadyExporting = when (feedType) {
            VideoFeedType.CAMERA -> holder.uiState.value.camera.isExportingClip
            VideoFeedType.SCREEN -> holder.uiState.value.screen.isExportingClip
        }
        if (isAlreadyExporting) return

        val (segments, container) = rollingDvrEngine.snapshotExportSegments()
        if (segments.isEmpty()) {
            holder.showToast("No recorded DVR footage available yet")
            return
        }

        setExportingFlag(feedType, exporting = true)
        val askBeforeDownload = holder.uiState.value.settings.askBeforeDownload

        scope.launch {
            val outcome = ClipExportManager.exportClip(
                context = appContext,
                feedType = feedType,
                segmentFiles = segments,
                container = container,
                askBeforeDownload = askBeforeDownload
            )
            setExportingFlag(feedType, exporting = false)

            when (outcome) {
                is ClipExportOutcome.SavedToDownloads -> {
                    holder.showToast("Saved ${outcome.fileName} to Downloads/NexusParent")
                }
                is ClipExportOutcome.NeedsUserDestination -> {
                    pendingStagedFile?.delete()
                    pendingStagedFile = outcome.stagedFile
                    _pendingExportRequests.tryEmit(
                        PendingDocumentExportRequest(outcome.fileName, outcome.mimeType)
                    )
                }
                is ClipExportOutcome.Failed -> {
                    holder.showToast(outcome.reason)
                }
            }
        }
    }

    fun completeCreateDocumentExport(destinationUri: Uri?) {
        val staged = pendingStagedFile ?: return
        pendingStagedFile = null
        val holder = stateHolder ?: return
        if (destinationUri == null) {
            staged.delete()
            holder.showToast("Clip export cancelled")
            return
        }
        scope.launch {
            val saved = ClipExportManager.copyStagedFileToUri(appContext, staged, destinationUri)
            holder.showToast(if (saved) "DVR clip saved cleanly" else "Failed saving DVR clip to chosen location")
        }
    }

    // -------------------------------------------------------------------------
    // LOCATION TRACKING CONTROLS & NOMINATIM GEOCODING
    // -------------------------------------------------------------------------

    fun toggleLocationTracking() {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.location.state
        when (current) {
            MonitoringState.IDLE -> startLocationTracking(holder)
            MonitoringState.LIVE -> stopLocationTracking(holder)
            MonitoringState.STARTING, MonitoringState.STOPPING -> Unit
        }
    }

    private fun startLocationTracking(holder: ParentStateHolder) {
        holder.updateLocationState {
            it.copy(state = MonitoringState.STARTING, errorMessage = null)
        }
        if (!webRtcManager.sendCommand(SurveillanceProtocols.CMD_LOCATION_ON)) {
            holder.updateLocationState {
                it.copy(state = MonitoringState.IDLE, errorMessage = "DataChannel is not open")
            }
            holder.showToast("Cannot start Location: DataChannel closed")
            return
        }

        locationTimeoutJob?.cancel()
        locationTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.location.state == MonitoringState.STARTING) {
                Log.w(TAG, "Timeout waiting for LOCATION_STARTED; reverting to IDLE")
                holder.updateLocationState {
                    it.copy(state = MonitoringState.IDLE, errorMessage = "Location request timed out")
                }
                holder.showToast("Location tracking timed out")
            }
        }
    }

    private fun stopLocationTracking(holder: ParentStateHolder) {
        holder.updateLocationState { it.copy(state = MonitoringState.STOPPING) }
        if (!webRtcManager.sendCommand(SurveillanceProtocols.CMD_LOCATION_OFF)) {
            resetLocationToIdle(holder)
            return
        }

        locationTimeoutJob?.cancel()
        locationTimeoutJob = scope.launch {
            delay(SurveillanceProtocols.COMMAND_CONFIRMATION_TIMEOUT_MS)
            if (holder.uiState.value.location.state == MonitoringState.STOPPING) {
                resetLocationToIdle(holder)
            }
        }
    }

    // -------------------------------------------------------------------------
    // INCOMING DATACHANNEL MESSAGE ROUTING & PEER DISCONNECT FAIL-SAFE
    // -------------------------------------------------------------------------

    fun handleIncomingMessage(rawMessage: String): Boolean {
        // Fast path for 50fps Opus system audio frames avoiding string trims & log noise
        if (rawMessage.startsWith(SurveillanceProtocols.HEADER_OPUS_AUDIO)) {
            val holder = stateHolder ?: return true
            if (holder.uiState.value.screen.state == MonitoringState.LIVE) {
                opusAudioPlayer.feedBase64Packet(
                    rawMessage.substring(SurveillanceProtocols.HEADER_OPUS_AUDIO.length)
                )
            }
            return true
        }

        val holder = stateHolder ?: return false
        val msg = rawMessage.trim()

        return when {
            msg == SurveillanceProtocols.RESP_CAMERA_STARTED -> {
                handleCameraStarted(holder)
                true
            }
            msg == SurveillanceProtocols.RESP_CAMERA_STOPPED -> {
                cameraTimeoutJob?.cancel()
                resetCameraFeedToIdle(holder)
                true
            }
            msg.startsWith(SurveillanceProtocols.RESP_CAMERA_SWITCHED_PREFIX) -> {
                cameraTimeoutJob?.cancel()
                val switchedLens = SurveillanceProtocols.parseCameraSwitchedLens(
                    msg,
                    holder.uiState.value.camera.lens
                )
                holder.updateCameraState {
                    it.copy(isSwitchingLens = false, lens = switchedLens)
                }
                holder.showToast("Camera lens switched to ${switchedLens.label}")
                true
            }
            msg.startsWith(SurveillanceProtocols.RESP_CAMERA_ERROR_PREFIX) -> {
                cameraTimeoutJob?.cancel()
                val err = msg.removePrefix(SurveillanceProtocols.RESP_CAMERA_ERROR_PREFIX).trim()
                when (holder.uiState.value.camera.state) {
                    MonitoringState.STARTING -> resetCameraFeedToIdle(holder)
                    MonitoringState.STOPPING -> holder.updateCameraState { it.copy(state = MonitoringState.LIVE) }
                    else -> Unit
                }
                holder.updateCameraState { it.copy(isSwitchingLens = false) }
                holder.showToast("Camera error: $err")
                true
            }

            msg == SurveillanceProtocols.RESP_SCREEN_STARTED ||
                msg == SurveillanceProtocols.RESP_SCREEN_RECORDING_STARTED -> {
                handleScreenStarted(holder)
                true
            }
            msg == SurveillanceProtocols.RESP_SCREEN_STOPPED ||
                msg == SurveillanceProtocols.RESP_SCREEN_RECORDING_STOPPED -> {
                screenTimeoutJob?.cancel()
                resetScreenFeedToIdle(holder)
                true
            }
            msg.startsWith(SurveillanceProtocols.RESP_SCREEN_QUALITY_SET_PREFIX) -> {
                val quality = msg.substringAfter(':').trim()
                holder.showToast("Screen mirror quality updated to $quality")
                true
            }
            msg.startsWith(SurveillanceProtocols.RESP_SCREEN_ERROR_PREFIX) -> {
                screenTimeoutJob?.cancel()
                val err = msg.removePrefix(SurveillanceProtocols.RESP_SCREEN_ERROR_PREFIX).trim()
                when (holder.uiState.value.screen.state) {
                    MonitoringState.STARTING -> resetScreenFeedToIdle(holder)
                    MonitoringState.STOPPING -> holder.updateScreenState { it.copy(state = MonitoringState.LIVE) }
                    else -> Unit
                }
                holder.showToast("Screen error: $err")
                true
            }

            msg == SurveillanceProtocols.RESP_LOCATION_STARTED -> {
                locationTimeoutJob?.cancel()
                holder.updateLocationState {
                    it.copy(state = MonitoringState.LIVE, errorMessage = null)
                }
                holder.setFeatureLive(FeatureId.LOCATION, isLive = true)
                startLocationPingTicker(holder)
                true
            }
            msg == SurveillanceProtocols.RESP_LOCATION_STOPPED -> {
                locationTimeoutJob?.cancel()
                resetLocationToIdle(holder)
                true
            }
            msg.startsWith(SurveillanceProtocols.RESP_LOCATION_ERROR_PREFIX) -> {
                locationTimeoutJob?.cancel()
                val detail = msg.removePrefix(SurveillanceProtocols.RESP_LOCATION_ERROR_PREFIX).trim()
                resetLocationToIdle(holder, errorText = detail)
                holder.showToast("Location error: $detail")
                true
            }
            else -> {
                val locPacket = SurveillanceProtocols.parseLocationPacket(msg) ?: return false
                handleLocationUpdatePacket(holder, locPacket)
                true
            }
        }
    }

    fun onPeerDisconnected() {
        val holder = stateHolder ?: return
        cameraTimeoutJob?.cancel()
        screenTimeoutJob?.cancel()
        locationTimeoutJob?.cancel()
        resetCameraFeedToIdle(holder)
        resetScreenFeedToIdle(holder)
        resetLocationToIdle(holder)
        pendingStagedFile?.delete()
        pendingStagedFile = null
    }

    private fun handleCameraStarted(holder: ParentStateHolder) {
        cameraTimeoutJob?.cancel()
        val currentState = holder.uiState.value.camera.state
        if (currentState == MonitoringState.STARTING) {
            webRtcManager.setActiveVideoType(VideoFeedType.CAMERA)
            holder.updateCameraState {
                it.copy(
                    state = MonitoringState.LIVE,
                    dvrProgress = 1f,
                    isAtLiveEdge = true
                )
            }
            holder.setFeatureLive(FeatureId.CAMERA, isLive = true)
            holder.setFeatureLive(FeatureId.SCREEN, isLive = false)
            if (holder.uiState.value.screen.state != MonitoringState.IDLE) {
                resetScreenFeedToIdle(holder)
            }
            webRtcManager.activeVideoTrack.value?.let { track ->
                startDvrForTrack(VideoFeedType.CAMERA, track)
            }
            startDvrTicker(holder, VideoFeedType.CAMERA)
        } else if (webRtcManager.getActiveVideoType() != VideoFeedType.CAMERA) {
            webRtcManager.sendCommand(SurveillanceProtocols.CMD_CAMERA_OFF)
        }
    }

    private fun handleScreenStarted(holder: ParentStateHolder) {
        screenTimeoutJob?.cancel()
        val currentState = holder.uiState.value.screen.state
        if (currentState == MonitoringState.STARTING) {
            webRtcManager.setActiveVideoType(VideoFeedType.SCREEN)
            holder.updateScreenState {
                it.copy(
                    state = MonitoringState.LIVE,
                    dvrProgress = 1f,
                    isAtLiveEdge = true
                )
            }
            holder.setFeatureLive(FeatureId.SCREEN, isLive = true)
            holder.setFeatureLive(FeatureId.CAMERA, isLive = false)
            if (holder.uiState.value.camera.state != MonitoringState.IDLE) {
                resetCameraFeedToIdle(holder)
            }
            opusAudioPlayer.start()
            opusAudioPlayer.setMuted(!holder.uiState.value.screen.isSystemAudioEnabled)
            webRtcManager.activeVideoTrack.value?.let { track ->
                startDvrForTrack(VideoFeedType.SCREEN, track)
            }
            startDvrTicker(holder, VideoFeedType.SCREEN)
        } else if (webRtcManager.getActiveVideoType() != VideoFeedType.SCREEN) {
            webRtcManager.sendCommand(SurveillanceProtocols.CMD_SCREEN_OFF)
        }
    }

    private fun startDvrForTrack(feedType: VideoFeedType, track: org.webrtc.VideoTrack) {
        val settings = stateHolder?.uiState?.value?.settings ?: SettingsValues()
        rollingDvrEngine.start(
            feedType = feedType,
            track = track,
            eglContext = webRtcManager.getEglBaseContext(),
            bufferDuration = settings.dvrBufferDuration,
            clipFormat = settings.dvrClipFormat
        )
    }

    private fun startDvrTicker(holder: ParentStateHolder, feedType: VideoFeedType) {
        dvrTickerJob?.cancel()
        dvrTickerJob = scope.launch {
            while (isActive) {
                val snapshot = rollingDvrEngine.getProgressSnapshot()
                when (feedType) {
                    VideoFeedType.CAMERA -> {
                        val cam = holder.uiState.value.camera
                        if (cam.state != MonitoringState.LIVE) break
                        holder.updateCameraState {
                            it.copy(
                                hasDvrClips = snapshot.hasRecordedClips,
                                dvrBufferStartSeconds = snapshot.bufferStartSeconds,
                                dvrTotalSeconds = snapshot.totalSeconds,
                                elapsedLabel = if (it.isAtLiveEdge) {
                                    SurveillanceProtocols.formatElapsedSeconds(snapshot.totalSeconds.toInt())
                                } else {
                                    it.elapsedLabel
                                }
                            )
                        }
                    }
                    VideoFeedType.SCREEN -> {
                        val scr = holder.uiState.value.screen
                        if (scr.state != MonitoringState.LIVE) break
                        holder.updateScreenState {
                            it.copy(
                                hasDvrClips = snapshot.hasRecordedClips,
                                dvrBufferStartSeconds = snapshot.bufferStartSeconds,
                                dvrTotalSeconds = snapshot.totalSeconds,
                                elapsedLabel = if (it.isAtLiveEdge) {
                                    SurveillanceProtocols.formatElapsedSeconds(snapshot.totalSeconds.toInt())
                                } else {
                                    it.elapsedLabel
                                }
                            )
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun handleLocationUpdatePacket(holder: ParentStateHolder, packet: LocationUpdatePacket) {
        val coordsStr = SurveillanceProtocols.formatCoordinates(packet.latitude, packet.longitude)
        val accStr = SurveillanceProtocols.formatAccuracy(packet.accuracyMeters)
        val speedStr = SurveillanceProtocols.formatSpeed(packet.speedMps)
        val pingStr = SurveillanceProtocols.formatRelativeTime(packet.timestampMs)

        holder.updateLocationState {
            it.copy(
                latitude = packet.latitude,
                longitude = packet.longitude,
                accuracyMeters = packet.accuracyMeters,
                speedMps = packet.speedMps,
                altitudeMeters = packet.altitudeMeters,
                bearingDegrees = packet.bearingDegrees,
                lastTimestampMs = packet.timestampMs,
                coordinatesLabel = coordsStr,
                accuracyLabel = accStr,
                velocityLabel = speedStr,
                lastPingLabel = pingStr,
                errorMessage = null
            )
        }

        scope.launch {
            val resolved = nominatimGeocoder.resolveAddress(packet.latitude, packet.longitude)
            if (!resolved.isNullOrBlank()) {
                holder.updateLocationState { it.copy(address = resolved) }
            }
        }
    }

    private fun startLocationPingTicker(holder: ParentStateHolder) {
        locationPingTickerJob?.cancel()
        locationPingTickerJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                val loc = holder.uiState.value.location
                if (loc.state != MonitoringState.LIVE) break
                val ts = loc.lastTimestampMs ?: continue
                holder.updateLocationState {
                    it.copy(lastPingLabel = SurveillanceProtocols.formatRelativeTime(ts))
                }
            }
        }
    }

    private fun resetCameraFeedToIdle(holder: ParentStateHolder, showSwitchedToast: String? = null) {
        dvrTickerJob?.cancel()
        dvrTickerJob = null
        activePlaybackSegmentIndex = -1
        rollingDvrEngine.stop()
        if (webRtcManager.getActiveVideoType() == VideoFeedType.CAMERA) {
            webRtcManager.setActiveVideoType(null)
        }
        val currentCam = holder.uiState.value.camera
        holder.updateCameraState {
            CameraFeedUiState(
                state = MonitoringState.IDLE,
                lens = currentCam.lens,
                rotationDegrees = currentCam.rotationDegrees,
                isFullscreen = currentCam.isFullscreen
            )
        }
        holder.setFeatureLive(FeatureId.CAMERA, isLive = false)
        showSwitchedToast?.let { holder.showToast(it) }
    }

    private fun resetScreenFeedToIdle(holder: ParentStateHolder, showSwitchedToast: String? = null) {
        dvrTickerJob?.cancel()
        dvrTickerJob = null
        activePlaybackSegmentIndex = -1
        rollingDvrEngine.stop()
        opusAudioPlayer.reset()
        if (webRtcManager.getActiveVideoType() == VideoFeedType.SCREEN) {
            webRtcManager.setActiveVideoType(null)
        }
        val currentScreen = holder.uiState.value.screen
        holder.updateScreenState {
            ScreenFeedUiState(
                state = MonitoringState.IDLE,
                quality = holder.uiState.value.settings.screenQuality,
                rotationDegrees = currentScreen.rotationDegrees,
                isSystemAudioEnabled = currentScreen.isSystemAudioEnabled,
                isFullscreen = currentScreen.isFullscreen
            )
        }
        holder.setFeatureLive(FeatureId.SCREEN, isLive = false)
        showSwitchedToast?.let { holder.showToast(it) }
    }

    private fun resetLocationToIdle(holder: ParentStateHolder, errorText: String? = null) {
        locationPingTickerJob?.cancel()
        locationPingTickerJob = null
        holder.updateLocationState {
            it.copy(
                state = MonitoringState.IDLE,
                errorMessage = errorText
            )
        }
        holder.setFeatureLive(FeatureId.LOCATION, isLive = false)
    }

    private fun setExportingFlag(feedType: VideoFeedType, exporting: Boolean) {
        val holder = stateHolder ?: return
        when (feedType) {
            VideoFeedType.CAMERA -> holder.updateCameraState { it.copy(isExportingClip = exporting) }
            VideoFeedType.SCREEN -> holder.updateScreenState { it.copy(isExportingClip = exporting) }
        }
    }
}
