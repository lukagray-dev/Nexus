package com.nexus.child.components.screen

/**
 * IScreenController specifies the interface contract for managing, querying,
 * and streaming full-screen video and system audio on the Android Child device.
 *
 * Hey friend! This interface decouples screen capture and audio pipeline logic
 * from DataChannel transport, allowing full mockability in unit tests.
 */
interface IScreenController {

    /**
     * Checks whether the user has enabled Nexus Child in Android's Accessibility Services.
     */
    fun hasAccessibilityPermission(): Boolean

    /**
     * Returns true if live Full HD screen video capture and streaming is underway.
     */
    fun isStreaming(): Boolean

    /**
     * Requests screen capture permission and begins streaming upon consent.
     *
     * @param onStarted Callback invoked when screen streaming and audio capture successfully initialize.
     * @param onError Callback invoked if permission is missing, denied, or initialization fails.
     * @return true if screen capture initiation was dispatched; false otherwise.
     */
    fun startScreenStreaming(
        onStarted: () -> Unit,
        onError: (errorMessage: String) -> Unit
    ): Boolean

    /**
     * Halts live screen video capture, system audio capture, and encoder threads.
     *
     * @return true if active capture was halted; false if already idle.
     */
    fun stopScreenStreaming(): Boolean

    /**
     * Completely cleans up all capture resources, MediaProjection sessions, and threads.
     */
    fun cleanup()
}
