package com.nexus.parent.data.mic

import com.nexus.parent.media.mic.AudioSpectrumAnalyzer
import com.nexus.parent.media.mic.VoiceChangerDspEngine
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.LoudspeakerVolume
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.SettingsValues
import com.nexus.parent.state.VoicePresetId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMicMediaBridge : MicMediaBridge {
    override val dspEngine = VoiceChangerDspEngine()
    override val spectrumAnalyzer = AudioSpectrumAnalyzer()

    var remoteAudioPlaying = false
    var outboundActive = false
    var voiceRouteEngaged = false
    var speechFgsActive = false
    var lastNoiseSuppression = true
    var failNextStartOutbound = false

    override fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteAudioPlaying = enabled
    }

    override fun startOutboundAudioTrack(noiseSuppression: Boolean): Boolean {
        if (failNextStartOutbound) return false
        outboundActive = true
        lastNoiseSuppression = noiseSuppression
        return true
    }

    override fun stopOutboundAudioTrack() {
        outboundActive = false
    }

    override fun reconfigureOutboundAudio(noiseSuppression: Boolean): Boolean {
        lastNoiseSuppression = noiseSuppression
        return true
    }

    override fun engageVoiceRoute() {
        voiceRouteEngaged = true
    }

    override fun releaseVoiceRoute() {
        voiceRouteEngaged = false
    }

    override fun onSpeechServiceActive(active: Boolean) {
        speechFgsActive = active
    }
}

/**
 * MicCoordinatorTest.kt - Verifies end-to-end Child Mic listen, Broadcast Intercom speak,
 * loading spinner states, 20s timeouts, voice hot-switching, settings sync, and Call Alert flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MicCoordinatorTest {

    @Test
    fun `listen flow shows intermediate STARTING and STOPPING spinners until Child confirms`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bridge = FakeMicMediaBridge()
        val sentCommands = mutableListOf<String>()

        val coordinator = MicCoordinator(
            scope = scope,
            mediaBridge = bridge,
            sendCommand = { cmd -> sentCommands.add(cmd); true },
            onPersistSettings = {}
        )
        val holder = ParentStateHolder()
        coordinator.attachStateHolder(holder)
        scope.runCurrent()

        // 1. Tap Play -> transitions IDLE -> STARTING spinner, ignores duplicate taps
        coordinator.toggleListen()
        assertEquals(MonitoringState.STARTING, holder.uiState.value.mic.listenState)
        assertEquals(listOf("MIC_ON"), sentCommands)
        coordinator.toggleListen() // Ignored while STARTING
        assertEquals(1, sentCommands.size)

        // 2. Child replies MIC_STARTED -> LIVE, remote track enabled, FeatureId.MIC badge lit
        assertTrue(coordinator.handleIncomingMessage("MIC_STARTED"))
        assertEquals(MonitoringState.LIVE, holder.uiState.value.mic.listenState)
        assertTrue(FeatureId.MIC in holder.uiState.value.liveFeatures)
        assertTrue(bridge.remoteAudioPlaying)
        assertTrue(bridge.voiceRouteEngaged)

        // 3. Minimizing HUD preserves active audio stream and LIVE indicator (matching Desktop)
        coordinator.setOverlayOpen(false)
        assertFalse(holder.uiState.value.mic.isOverlayOpen)
        assertEquals(MonitoringState.LIVE, holder.uiState.value.mic.listenState)
        assertTrue(FeatureId.MIC in holder.uiState.value.liveFeatures)

        // 4. Tap Pause -> STOPPING spinner until MIC_STOPPED arrives
        coordinator.toggleListen()
        assertEquals(MonitoringState.STOPPING, holder.uiState.value.mic.listenState)
        assertEquals(listOf("MIC_ON", "MIC_OFF"), sentCommands)

        assertTrue(coordinator.handleIncomingMessage("MIC_STOPPED"))
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.listenState)
        assertFalse(FeatureId.MIC in holder.uiState.value.liveFeatures)
        assertFalse(bridge.remoteAudioPlaying)
        assertFalse(bridge.voiceRouteEngaged)
    }

    @Test
    fun `listen timeout reverts STARTING spinner after 20s and MIC_ERROR recovers cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bridge = FakeMicMediaBridge()

        val coordinator = MicCoordinator(
            scope = scope,
            mediaBridge = bridge,
            sendCommand = { true },
            onPersistSettings = {}
        )
        val holder = ParentStateHolder()
        coordinator.attachStateHolder(holder)
        scope.runCurrent()

        coordinator.toggleListen()
        assertEquals(MonitoringState.STARTING, holder.uiState.value.mic.listenState)
        scope.advanceTimeBy(MicProtocol.COMMAND_CONFIRMATION_TIMEOUT_MS + 100L)
        scope.runCurrent()
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.listenState)

        // Error packet test
        coordinator.toggleListen()
        assertEquals(MonitoringState.STARTING, holder.uiState.value.mic.listenState)
        coordinator.handleIncomingMessage("MIC_ERROR: Permission denied")
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.listenState)
        assertEquals("Mic error: Permission denied", holder.uiState.value.toastMessage)
    }

    @Test
    fun `broadcast intercom gates on permission, hot-switches DSP presets, syncs loudspeaker settings, and stops cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bridge = FakeMicMediaBridge()
        val sentCommands = mutableListOf<String>()
        var persistedSettings: SettingsValues? = null

        val coordinator = MicCoordinator(
            scope = scope,
            mediaBridge = bridge,
            sendCommand = { cmd -> sentCommands.add(cmd); true },
            onPersistSettings = { persistedSettings = it }
        )
        val holder = ParentStateHolder()
        coordinator.attachStateHolder(holder)
        scope.runCurrent()

        // 1. Missing RECORD_AUDIO permission triggers prompt and resumes on grant
        var permRequested = false
        val job = scope.launch {
            coordinator.recordAudioPermissionRequests.first()
            permRequested = true
        }
        scope.runCurrent()
        coordinator.toggleSpeak(hasRecordAudioPermission = false)
        scope.runCurrent()
        assertTrue(permRequested)
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.speakState)
        job.cancel()

        // 2. Grant permission -> starts outbound track + sends PARENT_AUDIO_UNMUTE:max
        coordinator.onRecordAudioPermissionResult(granted = true)
        assertEquals(MonitoringState.STARTING, holder.uiState.value.mic.speakState)
        assertTrue(bridge.outboundActive)
        assertTrue(bridge.speechFgsActive)
        assertEquals(listOf("PARENT_AUDIO_UNMUTE:max"), sentCommands)

        // 3. Child replies PARENT_AUDIO_UNMUTED -> LIVE
        coordinator.handleIncomingMessage("PARENT_AUDIO_UNMUTED")
        assertEquals(MonitoringState.LIVE, holder.uiState.value.mic.speakState)

        // 4. Live voice preset hot-switch persists without renegotiation
        coordinator.selectVoicePreset(VoicePresetId.ROBOT_FEMALE)
        assertEquals(VoicePresetId.ROBOT_FEMALE, holder.uiState.value.mic.selectedVoice)
        assertEquals(VoicePresetId.ROBOT_FEMALE, bridge.dspEngine.getCurrentPreset())
        assertEquals(VoicePresetId.ROBOT_FEMALE, persistedSettings?.defaultVoicePreset)

        // 5. Updating loudspeakerVolume & noiseSuppression in Settings while LIVE updates Child & hardware
        val updatedSettings = holder.uiState.value.settings.copy(
            loudspeakerVolume = LoudspeakerVolume.HIGH,
            noiseSuppression = false
        )
        holder.applyPersistedSettingsSnapshot(updatedSettings)
        coordinator.onSettingsUpdated(updatedSettings)
        assertTrue(sentCommands.contains("PARENT_AUDIO_VOLUME:high"))
        assertFalse(bridge.lastNoiseSuppression)

        // 6. Stop Speak -> releases hardware mic & FGS flag + sends PARENT_AUDIO_MUTE -> IDLE on ack
        coordinator.toggleSpeak(hasRecordAudioPermission = true)
        assertEquals(MonitoringState.STOPPING, holder.uiState.value.mic.speakState)
        assertFalse(bridge.outboundActive)
        assertFalse(bridge.speechFgsActive)
        assertEquals("PARENT_AUDIO_MUTE", sentCommands.last())

        coordinator.handleIncomingMessage("PARENT_AUDIO_MUTED")
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.speakState)
    }

    @Test
    fun `CALL_STATE triggers ActiveCallAlert and 1-click accept opens HUD and sends MIC_ON`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val bridge = FakeMicMediaBridge()
        val sentCommands = mutableListOf<String>()

        val coordinator = MicCoordinator(
            scope = scope,
            mediaBridge = bridge,
            sendCommand = { cmd -> sentCommands.add(cmd); true },
            onPersistSettings = {}
        )
        val holder = ParentStateHolder()
        coordinator.attachStateHolder(holder)
        scope.runCurrent()

        coordinator.handleIncomingMessage(
            """{"type":"CALL_STATE","state":"active","direction":"incoming","contactName":"Alex","number":"5550199"}"""
        )
        assertNotNull(holder.uiState.value.activeCallAlert)

        coordinator.acceptCallAlertAndListenMic()
        assertNull(holder.uiState.value.activeCallAlert)
        assertTrue(holder.uiState.value.mic.isOverlayOpen)
        assertEquals(MonitoringState.STARTING, holder.uiState.value.mic.listenState)
        assertEquals(listOf("MIC_ON"), sentCommands)

        // Disconnect resets all audio pipelines cleanly
        coordinator.onPeerDisconnected()
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.listenState)
        assertEquals(MonitoringState.IDLE, holder.uiState.value.mic.speakState)
        assertFalse(bridge.outboundActive)
        assertFalse(bridge.remoteAudioPlaying)
    }
}
