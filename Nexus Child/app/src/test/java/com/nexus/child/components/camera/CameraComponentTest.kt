package com.nexus.child.components.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

/**
 * Unit tests verifying the Camera command-and-control protocol and state machine.
 *
 * Hey friend! These tests verify:
 * 1. Permission security enforcement: CAMERA_ON fails immediately if Manifest.permission.CAMERA is missing.
 * 2. Successful CAMERA_ON flow: starts the REAR camera by default and acknowledges with CAMERA_STARTED.
 * 3. Idempotency: repeated CAMERA_ON commands return fast acknowledgments without restarting the hardware.
 * 4. Clean CAMERA_OFF flow: halts video capture, mutes video track, and confirms CAMERA_STOPPED.
 * 5. Dynamic Camera Switching: toggles between REAR and FRONT lenses smoothly with CAMERA_SWITCHED confirmation.
 * 6. Fail-Safe Disconnection: peer disconnect automatically stops active camera capture.
 */
class CameraComponentTest {

    /**
     * Fake implementation of ICameraController for unit testing without hardware drivers.
     */
    private class FakeCameraController(
        var hasPermission: Boolean = true,
        var activeState: Boolean = false,
        var currentLensState: CameraLens = CameraLens.REAR,
        var startResult: Boolean = true,
        var stopResult: Boolean = true
    ) : ICameraController {

        var startCalled = false
        var stopCalled = false
        var switchCalled = false
        var cleanupCalled = false
        var attachCalled = false

        override fun hasCameraPermission(): Boolean = hasPermission
        override fun isActive(): Boolean = activeState
        override fun getCurrentLens(): CameraLens = currentLensState

        override fun startCamera(lens: CameraLens): Boolean {
            startCalled = true
            currentLensState = lens
            if (startResult) activeState = true
            return startResult
        }

        override fun stopCamera(): Boolean {
            stopCalled = true
            if (stopResult) activeState = false
            return stopResult
        }

        override fun switchCamera(): CameraLens {
            switchCalled = true
            currentLensState = if (currentLensState == CameraLens.REAR) CameraLens.FRONT else CameraLens.REAR
            return currentLensState
        }

        override fun attachToPeerConnection(pc: PeerConnection): Boolean {
            attachCalled = true
            return true
        }

        override fun getVideoTrack(): VideoTrack? = null

        override fun cleanup() {
            cleanupCalled = true
            activeState = false
        }
    }

    @Test
    fun testCameraOnWithoutPermissionSendsError() {
        val fakeController = FakeCameraController(hasPermission = false)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_ON)
        assertTrue("CAMERA_ON command must be recognized", handled)
        assertFalse("startCamera must not be invoked without permission", fakeController.startCalled)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report permission error",
            sentMessages[0].startsWith(CameraCommandHandler.RESP_CAMERA_ERROR_PREFIX)
        )
    }

    @Test
    fun testCameraOnWithPermissionStartsRearCameraAndConfirmsStarted() {
        val fakeController = FakeCameraController(hasPermission = true, activeState = false)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_ON)
        assertTrue("CAMERA_ON command must be recognized", handled)
        assertTrue("startCamera must be invoked", fakeController.startCalled)
        assertEquals("Must default to REAR camera lens", CameraLens.REAR, fakeController.currentLensState)
        assertTrue("Camera state must be active", fakeController.activeState)
        assertEquals(1, sentMessages.size)
        assertEquals(CameraCommandHandler.RESP_CAMERA_STARTED, sentMessages[0])
    }

    @Test
    fun testCameraOnWhenAlreadyActiveAcknowledgesImmediatelyWithoutRestart() {
        val fakeController = FakeCameraController(hasPermission = true, activeState = true)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_ON)
        assertTrue(handled)
        assertFalse("startCamera must not be called again if already active", fakeController.startCalled)
        assertEquals(1, sentMessages.size)
        assertEquals(CameraCommandHandler.RESP_CAMERA_STARTED, sentMessages[0])
    }

    @Test
    fun testCameraOffWhenActiveStopsAndConfirmsStopped() {
        val fakeController = FakeCameraController(activeState = true)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_OFF)
        assertTrue("CAMERA_OFF command must be recognized", handled)
        assertTrue("stopCamera must be invoked", fakeController.stopCalled)
        assertFalse("Camera state must be inactive", fakeController.activeState)
        assertEquals(1, sentMessages.size)
        assertEquals(CameraCommandHandler.RESP_CAMERA_STOPPED, sentMessages[0])
    }

    @Test
    fun testCameraOffWhenAlreadyIdleAcknowledgesImmediately() {
        val fakeController = FakeCameraController(activeState = false)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_OFF)
        assertTrue(handled)
        assertFalse("stopCamera must not be called if already idle", fakeController.stopCalled)
        assertEquals(1, sentMessages.size)
        assertEquals(CameraCommandHandler.RESP_CAMERA_STOPPED, sentMessages[0])
    }

    @Test
    fun testCameraSwitchTogglesFromRearToFrontAndConfirmsSwitched() {
        val fakeController = FakeCameraController(hasPermission = true, activeState = true, currentLensState = CameraLens.REAR)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        // First switch: REAR -> FRONT
        val handled1 = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_SWITCH)
        assertTrue(handled1)
        assertTrue(fakeController.switchCalled)
        assertEquals(CameraLens.FRONT, fakeController.currentLensState)
        assertEquals(1, sentMessages.size)
        assertEquals("CAMERA_SWITCHED: FRONT", sentMessages[0])

        // Second switch: FRONT -> REAR
        val handled2 = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_SWITCH)
        assertTrue(handled2)
        assertEquals(CameraLens.REAR, fakeController.currentLensState)
        assertEquals(2, sentMessages.size)
        assertEquals("CAMERA_SWITCHED: REAR", sentMessages[1])
    }

    @Test
    fun testCameraSwitchWithoutPermissionSendsError() {
        val fakeController = FakeCameraController(hasPermission = false)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_SWITCH)
        assertTrue(handled)
        assertFalse("switchCamera must not be invoked without permission", fakeController.switchCalled)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report permission error",
            sentMessages[0].startsWith(CameraCommandHandler.RESP_CAMERA_ERROR_PREFIX)
        )
    }

    @Test
    fun testCameraOnFailsGracefullyWhenHardwareUnavailable() {
        val fakeController = FakeCameraController(hasPermission = true, activeState = false, startResult = false)
        val sentMessages = mutableListOf<String>()

        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { msg -> sentMessages.add(msg); true }
        )

        val handled = handler.handleCommand(CameraCommandHandler.CMD_CAMERA_ON)
        assertTrue(handled)
        assertTrue(fakeController.startCalled)
        assertFalse(fakeController.activeState)
        assertEquals(1, sentMessages.size)
        assertTrue(
            "Must report hardware error",
            sentMessages[0].startsWith(CameraCommandHandler.RESP_CAMERA_ERROR_PREFIX)
        )
    }

    @Test
    fun testPeerDisconnectedStopsActiveCamera() {
        val fakeController = FakeCameraController(activeState = true)
        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { true }
        )

        handler.onPeerDisconnected()
        assertTrue("stopCamera must be called when peer disconnects", fakeController.stopCalled)
        assertFalse("Camera must be inactive after peer disconnect", fakeController.activeState)
    }

    @Test
    fun testPeerDisconnectedStopsEvenWhenIdleOrOpening() {
        val fakeController = FakeCameraController(activeState = false)
        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { true }
        )

        handler.onPeerDisconnected()
        assertTrue("stopCamera must be called unconditionally on peer disconnect", fakeController.stopCalled)
        assertFalse("Camera must remain inactive after peer disconnect", fakeController.isActive())
    }

    @Test
    fun testUnrelatedCommandReturnsFalse() {
        val fakeController = FakeCameraController()
        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { true }
        )

        val handled = handler.handleCommand("MIC_ON")
        assertFalse("Unrelated command must return false", handled)
    }

    @Test
    fun testCleanupTearsDownController() {
        val fakeController = FakeCameraController(activeState = true)
        val handler = CameraCommandHandler(
            cameraController = fakeController,
            dataSender = { true }
        )

        handler.cleanup()
        assertTrue("Controller cleanup must be called", fakeController.cleanupCalled)
        assertFalse("Controller must not be active after cleanup", fakeController.isActive())
    }
}
