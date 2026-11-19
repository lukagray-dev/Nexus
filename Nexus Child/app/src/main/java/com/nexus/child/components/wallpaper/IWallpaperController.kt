package com.nexus.child.components.wallpaper

/**
 * Encapsulates encoded wallpaper image data for Home and Lock screens.
 *
 * @property homeBase64 JPEG/WebP compressed Base64 string of the Home screen wallpaper.
 * @property lockBase64 JPEG/WebP compressed Base64 string of the Lock screen wallpaper (or null if same).
 * @property isSame true if Home and Lock screens use the identical wallpaper image.
 * @property timestamp Unix epoch time when the wallpaper snapshot was retrieved.
 */
data class WallpaperDataBundle(
    val homeBase64: String?,
    val lockBase64: String?,
    val isSame: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Interface defining wallpaper retrieval, live change observation, and remote push application.
 *
 * Hey friend! This interface isolates our WallpaperManager operations from the command handler,
 * providing clean mockability and safe handling of Android 7.0+ FLAG_SYSTEM and FLAG_LOCK APIs.
 */
interface IWallpaperController {

    /**
     * Verifies that the app has system permission to read and set wallpapers.
     */
    fun hasWallpaperPermission(): Boolean

    /**
     * Reads active Home and Lock screen wallpapers, determines if they are identical,
     * downscales and compresses them into high-efficiency JPEG Base64 payloads.
     */
    fun getActiveWallpapers(): WallpaperDataBundle

    /**
     * Registers active wallpaper listeners (OnColorsChangedListener and ACTION_WALLPAPER_CHANGED).
     *
     * @param onWallpaperChanged Callback invoked with fresh wallpaper data whenever child changes wallpaper.
     * @return true if monitoring started successfully; false otherwise.
     */
    fun startWallpaperMonitoring(onWallpaperChanged: (WallpaperDataBundle) -> Unit): Boolean

    /**
     * Halts wallpaper monitoring and unregisters system listeners.
     */
    fun stopWallpaperMonitoring(): Boolean

    /**
     * Checks if live wallpaper change observation is currently active.
     */
    fun isMonitoring(): Boolean

    /**
     * Applies a raw image byte array as the child device's wallpaper.
     *
     * @param imageData Raw JPEG or PNG byte array
     * @param target Target screen: "both", "home", or "lock"
     * @return true if successfully set via WallpaperManager; false otherwise.
     */
    fun applyWallpaper(imageData: ByteArray, target: String): Boolean

    /**
     * Releases active observers and cleans up memory buffers.
     */
    fun cleanup()
}
