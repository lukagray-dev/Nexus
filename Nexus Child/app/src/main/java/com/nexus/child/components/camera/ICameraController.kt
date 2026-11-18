package com.nexus.child.components.camera

import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

/**
 * CameraLens represents the physical camera hardware orientation on an Android device.
 *
 * Hey friend! Most phones have at least two cameras:
 * - REAR: The main back-facing camera pointing away from the user screen.
 * - FRONT: The selfie camera pointing at the user screen.
 */
enum class CameraLens {
    REAR,
    FRONT
}

/**
 * Interface defining the contract for camera hardware capture controllers.
 *
 * Hey friend! Here is why we use an interface here:
 * 1. Decoupling: Components like CameraCommandHandler only need to know what actions
 *    can be performed, not the low-level Camera2 or WebRTC details.
 * 2. Testability: We can mock or create fake controllers in unit tests without
 *    needing real Android camera hardware or Robolectric drivers!
 */
interface ICameraController {
    /**
     * Checks if the Android app has been granted Manifest.permission.CAMERA.
     */
    fun hasCameraPermission(): Boolean

    /**
     * Starts hardware camera video capture using the specified lens (defaults to REAR).
     *
     * @param lens Target camera direction (REAR or FRONT).
     * @return true if hardware capture was successfully initiated; false otherwise.
     */
    fun startCamera(lens: CameraLens = CameraLens.REAR): Boolean

    /**
     * Stops hardware camera capture and mutes video frames without removing the track.
     *
     * @return true if stopped cleanly; false if already idle.
     */
    fun stopCamera(): Boolean

    /**
     * Switches between REAR and FRONT camera lenses smoothly while streaming.
     *
     * @return The newly active CameraLens.
     */
    fun switchCamera(): CameraLens

    /**
     * Returns the currently configured or active CameraLens.
     */
    fun getCurrentLens(): CameraLens

    /**
     * Returns true if camera hardware is actively capturing and streaming frames.
     */
    fun isActive(): Boolean

    /**
     * Attaches the underlying WebRTC VideoTrack to the active PeerConnection.
     * Must be called during initial connection setup before SDP answer generation.
     */
    fun attachToPeerConnection(pc: PeerConnection): Boolean

    /**
     * Returns the native WebRTC VideoTrack instance.
     */
    fun getVideoTrack(): VideoTrack?

    /**
     * Full teardown called when the activity or WebRTC session is terminated.
     * Releases camera device locks and native WebRTC buffers.
     */
    fun cleanup()
}
