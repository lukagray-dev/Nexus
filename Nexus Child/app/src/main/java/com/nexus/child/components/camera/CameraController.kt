package com.nexus.child.components.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraController manages the native hardware camera capture pipeline on Android.
 *
 * Hey friend! Here is how WebRTC video streaming works on Android:
 * 1. An EglBase context is created to provide OpenGL hardware acceleration for video textures.
 * 2. SurfaceTextureHelper creates a dedicated background thread ("NexusCamThread") where camera
 *    frames are read directly from Android's Camera2 API.
 * 3. VideoSource wraps those hardware frames and feeds them into WebRTC's encoder.
 * 4. VideoTrack wraps the VideoSource so it can be streamed across the network.
 * 5. The VideoTrack is added to PeerConnection ONCE during initial handshake, but kept disabled
 *    (track.setEnabled(false)). This means NO SDP renegotiation is ever needed when the parent
 *    turns the camera on/off or switches lenses!
 * 6. When the parent sends CAMERA_ON, we simply start the Camera2Capturer and call track.setEnabled(true).
 * 7. When the parent sends CAMERA_OFF, we stop capture and call track.setEnabled(false).
 * 8. When switching lenses, we toggle between the rear and front sensors seamlessly!
 */
class CameraController(
    private val context: Context,
    private val factoryProvider: () -> PeerConnectionFactory?,
    private val peerConnectionProvider: () -> PeerConnection?,
    private val eglBaseContextProvider: () -> EglBase.Context? = { null }
) : ICameraController {

    companion object {
        private const val TAG = "CameraController"
        private const val TRACK_ID = "NEXUS_CAM_TRACK"
        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720
        private const val CAPTURE_FPS = 30
    }

    private var eglBase: EglBase? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var rtpSender: RtpSender? = null
    private var videoCapturer: VideoCapturer? = null

    // Track active lens direction (defaults to REAR camera per project specification)
    private var currentLens: CameraLens = CameraLens.REAR

    // Thread-safe flag tracking if hardware camera is actively capturing
    private val isCapturing = AtomicBoolean(false)

    init {
        initializePipeline()
    }

    /**
     * Initializes the WebRTC video pipeline: EGL context, texture helper, source, and track.
     */
    @Synchronized
    private fun initializePipeline() {
        val factory = factoryProvider()
        if (factory == null) {
            Log.w(TAG, "PeerConnectionFactory not available yet during CameraController init.")
            return
        }

        try {
            Log.d(TAG, "Initializing hardware video capture pipeline...")
            val sharedContext = eglBaseContextProvider()
            val egl = if (sharedContext != null) {
                Log.d(TAG, "Creating EglBase shared with WebRtcManager encoder factory...")
                EglBase.create(sharedContext)
            } else {
                Log.d(TAG, "Creating standalone EglBase context...")
                EglBase.create()
            }
            eglBase = egl

            val helper = SurfaceTextureHelper.create("NexusCamThread", egl.eglBaseContext)
            surfaceTextureHelper = helper

            val source = factory.createVideoSource(false)
            videoSource = source

            val track = factory.createVideoTrack(TRACK_ID, source)
            track.setEnabled(false) // Muted until parent explicitly sends CAMERA_ON
            videoTrack = track

            Log.d(TAG, "Video pipeline initialized successfully. VideoTrack ready and muted.")

            // Automatically attach to active PeerConnection if already available
            val pc = peerConnectionProvider()
            if (pc != null) {
                attachToPeerConnection(pc)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video pipeline: ${e.message}", e)
        }
    }

    /**
     * Attaches the muted VideoTrack to the active PeerConnection.
     * This ensures the track is included in the initial SDP answer.
     */
    @Synchronized
    override fun attachToPeerConnection(pc: PeerConnection): Boolean {
        val track = videoTrack ?: run {
            // Lazy initialization if factory wasn't ready during init
            initializePipeline()
            videoTrack ?: return false
        }

        if (rtpSender != null) {
            Log.d(TAG, "VideoTrack is already attached to PeerConnection.")
            return true
        }

        return try {
            Log.d(TAG, "Adding VideoTrack ($TRACK_ID) to PeerConnection with stream ID 'nexus-camera-stream'...")
            val sender = pc.addTrack(track, listOf("nexus-camera-stream"))
            rtpSender = sender
            Log.d(TAG, "VideoTrack attached to PeerConnection. RtpSender created: ${sender != null}")
            sender != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach VideoTrack to PeerConnection: ${e.message}", e)
            false
        }
    }

    /**
     * Checks whether the app currently holds the runtime CAMERA permission.
     */
    override fun hasCameraPermission(): Boolean {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts hardware camera video capture using the specified lens.
     */
    @Synchronized
    override fun startCamera(lens: CameraLens): Boolean {
        Log.d(TAG, "startCamera(lens=$lens) invoked. Currently active: ${isCapturing.get()}")

        if (isCapturing.get()) {
            if (currentLens == lens) {
                Log.d(TAG, "Camera is already capturing on target lens ($lens). No-op.")
                return true
            }
            // If changing lens while running, switch to new lens
            return switchCamera() == lens
        }

        if (!hasCameraPermission()) {
            Log.e(TAG, "Cannot start camera: Manifest.permission.CAMERA not granted!")
            return false
        }

        // Ensure pipeline is initialized and track is attached
        if (videoTrack == null) {
            initializePipeline()
        }
        val pc = peerConnectionProvider()
        if (pc != null && rtpSender == null) {
            attachToPeerConnection(pc)
        }

        val helper = surfaceTextureHelper
        val source = videoSource
        val track = videoTrack

        if (helper == null || source == null || track == null) {
            Log.e(TAG, "Video pipeline components missing. Cannot start capture.")
            return false
        }

        try {
            val enumerator = Camera2Enumerator(context)
            val targetDeviceName = findCameraDevice(enumerator, lens)
            if (targetDeviceName == null) {
                Log.e(TAG, "No matching camera device found for lens: $lens")
                return false
            }

            Log.d(TAG, "Creating CameraVideoCapturer for device: $targetDeviceName (Lens: $lens)...")
            val capturer = enumerator.createCapturer(targetDeviceName, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(errorDescription: String?) {
                    Log.e(TAG, "Hardware Camera Error: $errorDescription")
                }
                override fun onCameraDisconnected() {
                    Log.w(TAG, "Hardware Camera Disconnected.")
                }
                override fun onCameraFreezed(errorDescription: String?) {
                    Log.w(TAG, "Hardware Camera Freezed: $errorDescription")
                }
                override fun onCameraOpening(cameraName: String?) {
                    Log.d(TAG, "Hardware Camera opening: $cameraName")
                }
                override fun onFirstFrameAvailable() {
                    Log.d(TAG, "First camera video frame available from sensor!")
                }
                override fun onCameraClosed() {
                    Log.d(TAG, "Hardware Camera closed.")
                }
            })

            capturer.initialize(helper, context.applicationContext, source.capturerObserver)
            Log.d(TAG, "Starting capture at ${CAPTURE_WIDTH}x${CAPTURE_HEIGHT} @ ${CAPTURE_FPS}fps...")
            capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)

            videoCapturer = capturer
            currentLens = lens

            // Enable track so WebRTC encoder begins sending video packets
            track.setEnabled(true)
            isCapturing.set(true)

            Log.d(TAG, "Camera hardware capture started successfully on $lens lens.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting camera: ${e.message}", e)
            stopCamera()
            return false
        }
    }

    /**
     * Stops hardware camera capture and mutes video frames.
     */
    @Synchronized
    override fun stopCamera(): Boolean {
        Log.d(TAG, "stopCamera() invoked. Currently active: ${isCapturing.get()}")

        if (!isCapturing.get() && videoCapturer == null) {
            Log.d(TAG, "Camera is not currently capturing. Nothing to stop.")
            return false
        }

        try {
            videoCapturer?.let { capturer ->
                Log.d(TAG, "Stopping and disposing CameraVideoCapturer...")
                try {
                    capturer.stopCapture()
                } catch (e: Exception) {
                    Log.w(TAG, "Warning stopping capturer: ${e.message}")
                }
                capturer.dispose()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing capturer: ${e.message}")
        } finally {
            videoCapturer = null
            videoTrack?.setEnabled(false)
            isCapturing.set(false)
            Log.d(TAG, "Camera stopped and video track muted.")
        }

        return true
    }

    /**
     * Switches between REAR and FRONT camera lenses.
     */
    @Synchronized
    override fun switchCamera(): CameraLens {
        val nextLens = if (currentLens == CameraLens.REAR) CameraLens.FRONT else CameraLens.REAR
        Log.d(TAG, "switchCamera() invoked. Transitioning from $currentLens to $nextLens...")

        if (isCapturing.get()) {
            // Stop current sensor and start new sensor cleanly
            stopCamera()
            val started = startCamera(nextLens)
            if (!started) {
                Log.e(TAG, "Failed to start camera on $nextLens during switch. Attempting fallback to $currentLens.")
                startCamera(currentLens)
                return currentLens
            }
        } else {
            // If camera wasn't running, just update the target lens for next activation
            currentLens = nextLens
        }

        return currentLens
    }

    /**
     * Helper to discover the hardware camera device name corresponding to target lens.
     */
    private fun findCameraDevice(enumerator: Camera2Enumerator, lens: CameraLens): String? {
        val deviceNames = enumerator.deviceNames
        if (deviceNames.isEmpty()) {
            return null
        }

        return when (lens) {
            CameraLens.REAR -> {
                deviceNames.firstOrNull { enumerator.isBackFacing(it) } ?: deviceNames.firstOrNull()
            }
            CameraLens.FRONT -> {
                deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()
            }
        }
    }

    override fun getCurrentLens(): CameraLens = currentLens

    override fun isActive(): Boolean = isCapturing.get()

    override fun getVideoTrack(): VideoTrack? = videoTrack

    /**
     * Complete teardown invoked when WebRTC session or activity is destroyed.
     */
    @Synchronized
    override fun cleanup() {
        Log.d(TAG, "cleanup() invoked on CameraController. Releasing all hardware resources...")
        stopCamera()

        val pc = peerConnectionProvider()
        rtpSender?.let { sender ->
            try {
                pc?.removeTrack(sender)
            } catch (e: Exception) {
                Log.w(TAG, "Warning removing VideoTrack from PeerConnection: ${e.message}")
            }
        }
        rtpSender = null

        videoTrack?.dispose()
        videoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        eglBase?.release()
        eglBase = null

        Log.d(TAG, "CameraController cleanup complete.")
    }
}
