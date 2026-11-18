package com.nexus.child.components.camera

import android.util.Log

/**
 * CameraCommandHandler processes DataChannel control commands for camera surveillance:
 * 1. CAMERA_ON: Activates the child device's rear camera and enables the WebRTC video track.
 * 2. CAMERA_OFF: Halts hardware video capture and disables the WebRTC video track.
 * 3. CAMERA_SWITCH: Switches between rear and front hardware lenses.
 *
 * Hey friend! Here is how the zero-renegotiation camera command flow works:
 *
 * --- CAMERA ACTIVATION (CAMERA_ON) ---
 * 1. Parent clicks the Play button on their desktop console.
 * 2. Parent sends "CAMERA_ON" across the 'nexus-control' DataChannel.
 * 3. We verify that runtime CAMERA permission is granted on the phone.
 * 4. We start CameraController with the REAR camera lens by default.
 * 5. CameraController calls videoTrack.setEnabled(true), so video frames flow into WebRTC!
 * 6. We reply with "CAMERA_STARTED" over the DataChannel.
 * 7. Parent receives "CAMERA_STARTED" and flips the loading spinner to the Pause button!
 *
 * --- CAMERA DEACTIVATION (CAMERA_OFF) ---
 * 1. Parent clicks the Pause button on their desktop console.
 * 2. Parent sends "CAMERA_OFF" across the DataChannel.
 * 3. We stop the Camera2Capturer and call videoTrack.setEnabled(false).
 * 4. We reply with "CAMERA_STOPPED" over the DataChannel.
 * 5. Parent flips the loading spinner back to the Play button and shows paused overlay.
 *
 * --- CAMERA LENS SWITCH (CAMERA_SWITCH) ---
 * 1. Parent clicks "Change Lens" on their desktop console.
 * 2. Parent sends "CAMERA_SWITCH" across the DataChannel.
 * 3. We switch between REAR and FRONT lenses smoothly.
 * 4. We reply with "CAMERA_SWITCHED: FRONT" (or REAR) over the DataChannel.
 * 5. Parent updates its lens button label and restores the camera icon!
 */
class CameraCommandHandler(
    private val cameraController: ICameraController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "CameraCommandHandler"

        // Camera Control Commands
        const val CMD_CAMERA_ON = "CAMERA_ON"
        const val CMD_CAMERA_OFF = "CAMERA_OFF"
        const val CMD_CAMERA_SWITCH = "CAMERA_SWITCH"

        // Camera Responses & Confirmations
        const val RESP_CAMERA_STARTED = "CAMERA_STARTED"
        const val RESP_CAMERA_STOPPED = "CAMERA_STOPPED"
        const val RESP_CAMERA_SWITCHED_PREFIX = "CAMERA_SWITCHED"
        const val RESP_CAMERA_ERROR_PREFIX = "CAMERA_ERROR: "
    }

    /**
     * Inspects an incoming DataChannel message and executes the corresponding action
     * if it matches a camera command.
     *
     * @param command Raw message received over 'nexus-control'
     * @return true if command was recognized and handled; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        return when (command.trim()) {
            CMD_CAMERA_ON -> {
                handleCameraOn()
                true
            }
            CMD_CAMERA_OFF -> {
                handleCameraOff()
                true
            }
            CMD_CAMERA_SWITCH -> {
                handleCameraSwitch()
                true
            }
            else -> false
        }
    }

    /**
     * Handles starting the camera (rear lens by default).
     */
    private fun handleCameraOn() {
        Log.d(TAG, "Processing command: $CMD_CAMERA_ON")

        // Step 1: Verify runtime CAMERA permission
        if (!cameraController.hasCameraPermission()) {
            Log.e(TAG, "Camera permission missing on Child device!")
            dataSender("$RESP_CAMERA_ERROR_PREFIX Missing camera permission")
            return
        }

        // Step 2: If already active, immediately acknowledge with CAMERA_STARTED
        if (cameraController.isActive()) {
            Log.d(TAG, "Camera is already active. Acknowledging with $RESP_CAMERA_STARTED.")
            dataSender(RESP_CAMERA_STARTED)
            return
        }

        // Step 3: Start hardware camera capture (rear lens by default per user requirements)
        val started = cameraController.startCamera(CameraLens.REAR)
        if (started) {
            Log.d(TAG, "Camera started successfully. Transmitting confirmation: $RESP_CAMERA_STARTED...")
            dataSender(RESP_CAMERA_STARTED)
        } else {
            Log.e(TAG, "Failed to start camera hardware!")
            dataSender("$RESP_CAMERA_ERROR_PREFIX Failed to start camera hardware")
        }
    }

    /**
     * Handles stopping the camera and muting the video track.
     */
    private fun handleCameraOff() {
        Log.d(TAG, "Processing command: $CMD_CAMERA_OFF")

        // If already stopped, immediately acknowledge with CAMERA_STOPPED
        if (!cameraController.isActive()) {
            Log.d(TAG, "Camera is already idle. Acknowledging with $RESP_CAMERA_STOPPED.")
            dataSender(RESP_CAMERA_STOPPED)
            return
        }

        // Step 1: Stop camera capture and mute video frames
        val stopped = cameraController.stopCamera()
        if (!stopped) {
            Log.w(TAG, "stopCamera() returned false, but proceeding to confirm stop.")
        }

        Log.d(TAG, "Camera stopped. Transmitting confirmation: $RESP_CAMERA_STOPPED...")
        dataSender(RESP_CAMERA_STOPPED)
    }

    /**
     * Handles toggling between the rear and front camera lenses.
     */
    private fun handleCameraSwitch() {
        Log.d(TAG, "Processing command: $CMD_CAMERA_SWITCH")

        if (!cameraController.hasCameraPermission()) {
            Log.e(TAG, "Cannot switch camera: missing CAMERA permission!")
            dataSender("$RESP_CAMERA_ERROR_PREFIX Missing camera permission")
            return
        }

        val newLens = cameraController.switchCamera()
        val confirmation = "$RESP_CAMERA_SWITCHED_PREFIX: ${newLens.name}"
        Log.d(TAG, "Camera switched to $newLens. Transmitting confirmation: $confirmation...")
        dataSender(confirmation)
    }

    /**
     * Fail-safe disconnection callback invoked when WebRTC connection or DataChannel drops.
     * Automatically stops camera hardware so the physical camera sensor does not remain running.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Executing fail-safe camera teardown...")
        if (cameraController.isActive()) {
            Log.d(TAG, "Camera was actively capturing during disconnect. Stopping sensor...")
            cameraController.stopCamera()
        }
    }

    /**
     * Releases all camera resources when the activity or service is destroyed.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() invoked on CameraCommandHandler.")
        cameraController.cleanup()
    }
}
