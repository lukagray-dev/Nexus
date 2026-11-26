package com.nexus.child.components.screen

/**
 * Target video resolution and framerate preset for Screen Mirror streaming.
 */
enum class ScreenQualityPreset(val id: String, val maxDimension: Int, val fps: Int) {
    FULL_HD_1080P("1080p", 1920, 30),
    HD_720P("720p", 1280, 30),
    SD_540P("540p", 960, 24);

    companion object {
        fun fromId(raw: String?): ScreenQualityPreset {
            return when (raw?.trim()?.lowercase()) {
                "720p", "hd" -> HD_720P
                "540p", "sd" -> SD_540P
                else -> FULL_HD_1080P
            }
        }
    }
}

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
     * Returns true if live screen video capture and streaming is underway.
     */
    fun isStreaming(): Boolean

    /**
     * Requests screen capture permission and begins streaming upon consent at default 1080p.
     */
    fun startScreenStreaming(
        onStarted: () -> Unit,
        onError: (errorMessage: String) -> Unit
    ): Boolean

    /**
     * Requests screen capture permission and begins streaming using the requested [quality] preset.
     */
    fun startScreenStreaming(
        quality: ScreenQualityPreset,
        onStarted: () -> Unit,
        onError: (errorMessage: String) -> Unit
    ): Boolean = startScreenStreaming(onStarted, onError)

    /**
     * Dynamically updates resolution and framerate of an active screen capture session.
     */
    fun updateScreenQuality(quality: ScreenQualityPreset): Boolean = false

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
