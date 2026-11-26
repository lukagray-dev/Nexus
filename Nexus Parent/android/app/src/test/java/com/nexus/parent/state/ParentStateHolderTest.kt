package com.nexus.parent.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ParentStateHolderTest.kt - Covers end-to-end UI state transitions across pairing,
 * push-drawer navigation, surveillance feeds, floating mic HUD, stealth, and settings.
 */
class ParentStateHolderTest {

    @Test
    fun `pairing validation rejects short id and connects on 12 digits`() {
        val holder = ParentStateHolder()

        holder.updatePairingDigits("12345")
        assertEquals("7 more digits needed", holder.uiState.value.pairingStatusMessage)
        holder.submitPairingPreview()
        assertEquals(ConnectionPhase.DISCONNECTED, holder.uiState.value.connectionPhase)
        assertNotNull(holder.uiState.value.pairingErrorMessage)

        holder.updatePairingDigits("4821-9402-1104")
        assertEquals("482194021104", holder.uiState.value.pairingInputDigits)
        assertEquals("Valid 12-digit ID. Ready to connect.", holder.uiState.value.pairingStatusMessage)

        holder.submitPairingPreview()
        assertEquals(ConnectionPhase.CONNECTED, holder.uiState.value.connectionPhase)
        assertNotNull(holder.uiState.value.connectedDevice)
        assertEquals("4821 9402 1104", holder.uiState.value.connectedDevice?.formattedId)
    }

    @Test
    fun `camera and screen streams share exclusive video transceiver and wrap 90 degree rotation`() {
        val holder = ParentStateHolder()
        holder.submitPairingPreview("482194021104")

        holder.selectFeature(FeatureId.CAMERA)
        assertEquals(FeatureId.CAMERA, holder.uiState.value.activeFeature)
        assertFalse(holder.uiState.value.isDrawerOpen)

        holder.toggleCameraStream()
        assertEquals(MonitoringState.LIVE, holder.uiState.value.camera.state)
        assertTrue(FeatureId.CAMERA in holder.uiState.value.liveFeatures)

        repeat(4) { holder.rotateCameraFeed() }
        assertEquals(0, holder.uiState.value.camera.rotationDegrees)

        holder.scrubCameraDvr(0.45f)
        assertFalse(holder.uiState.value.camera.isAtLiveEdge)
        holder.jumpCameraToLive()
        assertTrue(holder.uiState.value.camera.isAtLiveEdge)
        assertEquals(1f, holder.uiState.value.camera.dvrProgress, 0.001f)

        // Starting Screen stream yields Camera stream cleanly
        holder.selectFeature(FeatureId.SCREEN)
        holder.toggleScreenStream()
        assertEquals(MonitoringState.LIVE, holder.uiState.value.screen.state)
        assertEquals(MonitoringState.IDLE, holder.uiState.value.camera.state)
        assertFalse(FeatureId.CAMERA in holder.uiState.value.liveFeatures)
        assertTrue(FeatureId.SCREEN in holder.uiState.value.liveFeatures)
    }

    @Test
    fun `floating mic overlay persists over active view and syncs voice preset with settings`() {
        val holder = ParentStateHolder()
        holder.submitPairingPreview("482194021104")
        holder.selectFeature(FeatureId.LOCATION)

        holder.selectFeature(FeatureId.MIC)
        assertTrue(holder.uiState.value.mic.isOverlayOpen)
        assertEquals(FeatureId.LOCATION, holder.uiState.value.activeFeature)

        holder.toggleMicListen()
        assertEquals(MonitoringState.LIVE, holder.uiState.value.mic.listenState)
        assertTrue(FeatureId.MIC in holder.uiState.value.liveFeatures)

        holder.selectVoicePreset(VoicePresetId.ROBOT_FEMALE)
        assertEquals(VoicePresetId.ROBOT_FEMALE, holder.uiState.value.mic.selectedVoice)
        assertEquals(VoicePresetId.ROBOT_FEMALE, holder.uiState.value.settings.defaultVoicePreset)
    }

    @Test
    fun `stealth requires active connection and toggles between ARMED and ACTIVE`() {
        val holder = ParentStateHolder()

        // Disconnected guard
        holder.onStealthButtonClicked()
        assertTrue(holder.uiState.value.showStealthNotConnectedDialog)
        assertFalse(holder.uiState.value.showStealthConfirmDialog)
        holder.dismissStealthDialogs()

        // Connected flow
        holder.submitPairingPreview("482194021104")
        holder.onStealthButtonClicked()
        assertTrue(holder.uiState.value.showStealthConfirmDialog)

        holder.confirmStealthToggle()
        assertEquals(StealthStatus.ACTIVE, holder.uiState.value.stealthStatus)
        assertFalse(holder.uiState.value.showStealthConfirmDialog)

        holder.confirmStealthToggle()
        assertEquals(StealthStatus.ARMED, holder.uiState.value.stealthStatus)
    }

    @Test
    fun `monitor panels toggle live badges and disconnect resets session state`() {
        val holder = ParentStateHolder()
        holder.submitPairingPreview("482194021104")

        listOf(
            FeatureId.SMS,
            FeatureId.CALLLOG,
            FeatureId.NOTIFICATION,
            FeatureId.KEYBOARD,
            FeatureId.WELLBEING,
            FeatureId.APPLOCK,
            FeatureId.WALLPAPER
        ).forEach { feature ->
            holder.toggleMonitorPanelPlayPause(feature)
            assertTrue(feature in holder.uiState.value.liveFeatures)
        }

        holder.updateSettings {
            it.copy(
                screenQuality = ScreenQuality.P540,
                preferredCameraLens = CameraLens.FRONT,
                keepScreenAwake = false
            )
        }
        assertEquals(ScreenQuality.P540, holder.uiState.value.settings.screenQuality)

        holder.resetSettingsToDefaults()
        assertEquals(ScreenQuality.P1080, holder.uiState.value.settings.screenQuality)
        assertEquals(CameraLens.REAR, holder.uiState.value.settings.preferredCameraLens)
        assertTrue(holder.uiState.value.settings.keepScreenAwake)

        holder.disconnectDevice()
        assertEquals(ConnectionPhase.DISCONNECTED, holder.uiState.value.connectionPhase)
        assertNull(holder.uiState.value.activeFeature)
        assertTrue(holder.uiState.value.liveFeatures.isEmpty())
    }

    @Test
    fun `telemetry syncs stealth status and paired device detail navigation works cleanly`() {
        val holder = ParentStateHolder()
        val sample = DeviceRecord(
            rawId = "482194021104",
            formattedId = "4821 9402 1104",
            name = "Child Phone",
            model = "Pixel 8 Pro",
            lastSyncedLabel = "Online • Just now",
            telemetry = DeviceTelemetry(batteryPercent = 82, isStealthMode = true)
        )

        holder.onConnectionEstablished(
            record = sample.copy(telemetry = sample.telemetry.copy(isStealthMode = false)),
            history = listOf(sample),
            toastMessage = null
        )
        assertEquals(ConnectionPhase.CONNECTED, holder.uiState.value.connectionPhase)
        assertEquals(StealthStatus.ARMED, holder.uiState.value.stealthStatus)

        holder.onTelemetryUpdated(record = sample, history = listOf(sample))
        assertEquals(StealthStatus.ACTIVE, holder.uiState.value.stealthStatus)
        assertEquals(82, holder.uiState.value.connectedDevice?.telemetry?.batteryPercent)

        holder.openSettings(SettingsTabId.DEVICES)
        holder.selectPairedDevice("482194021104")
        assertEquals("482194021104", holder.uiState.value.selectedPairedDeviceId)
        holder.clearSelectedPairedDevice()
        assertNull(holder.uiState.value.selectedPairedDeviceId)

        holder.onConnectionDroppedReconnecting("Connection lost")
        assertEquals(ConnectionPhase.RECONNECTING, holder.uiState.value.connectionPhase)
        holder.onReconnectTick(attempt = 2, maxAttempts = 9, secondsRemaining = 30)
        assertEquals(2, holder.uiState.value.reconnectAttempt)
        assertEquals(30, holder.uiState.value.reconnectSecondsRemaining)
    }
}
