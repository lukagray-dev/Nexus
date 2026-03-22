package nexus.android.parent.features.screen

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import nexus.android.parent.webrtc.ParentPeerManager
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * ScreenFeature - Remote screen monitoring feature
 * Allows parents to view live screen feed from child device
 * Uses WebRTC for real-time video streaming
 * Follows exact same pattern as CameraFeature
 */
class ScreenFeature(context: Context) : BaseFeature(context) {

    private var statusIndicator: View? = null
    private var playPauseBtn: ImageView? = null
    private var fullscreenBtn: ImageView? = null
    private var placeholder: LinearLayout? = null
    private var placeholderText: TextView? = null
    private var streamContainer: FrameLayout? = null
    private var surfaceView: SurfaceViewRenderer? = null
    
    private var isStreaming = false
    private var isLoading = false
    private var currentVideoTrack: VideoTrack? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null
    private var videoTrackJob: Job? = null
    private var confirmationTimeout: Job? = null
    
    companion object {
        private const val TAG = "ScreenFeature"
    }

    override fun createView(container: ViewGroup): View {
        android.util.Log.d(TAG, "🖥️ createView() called")
        
        // Find views
        statusIndicator = container.findViewById(R.id.screen_status_indicator)
        playPauseBtn = container.findViewById(R.id.screen_play_pause_btn)
        fullscreenBtn = container.findViewById(R.id.screen_fullscreen_btn)
        placeholder = container.findViewById(R.id.screen_placeholder)
        placeholderText = container.findViewById(R.id.screen_placeholder_text)
        streamContainer = container.findViewById(R.id.screen_stream_container)
        
        android.util.Log.d(TAG, "Views found - statusIndicator: $statusIndicator, playPauseBtn: $playPauseBtn, streamContainer: $streamContainer")
        
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            android.util.Log.d(TAG, "Starting data channel listener")
            listenForConfirmations()
        }
        
        if (videoTrackJob == null || videoTrackJob?.isActive == false) {
            android.util.Log.d(TAG, "Starting video track listener")
            listenForVideoTrack()
        }
        
        return container
    }

    private fun setupListeners() {
        playPauseBtn?.setOnClickListener {
            if (!isLoading) {
                toggleScreen()
            }
        }
        
        fullscreenBtn?.setOnClickListener {
            if (isStreaming && currentVideoTrack != null) {
                toggleFullscreen()
            } else {
                Toast.makeText(context, "Start screen recording first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForConfirmations() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                android.util.Log.d(TAG, "Received message: $message")
                
                try {
                    // Try parsing as JSON first
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "SCREEN_RECORDING_STARTED" -> {
                            android.util.Log.d(TAG, "Screen recording started confirmed (JSON)")
                            handleScreenStarted()
                        }
                        "SCREEN_RECORDING_STOPPED" -> {
                            android.util.Log.d(TAG, "Screen recording stopped confirmed (JSON)")
                            handleScreenStopped()
                        }
                        "SCREEN_RECORDING_PERMISSION_DENIED" -> {
                            android.util.Log.w(TAG, "Screen recording permission denied (JSON)")
                            handlePermissionDenied()
                        }
                        "SCREEN_RECORDING_ERROR" -> {
                            android.util.Log.e(TAG, "Screen recording error (JSON)")
                            handleError("Screen recording error")
                        }
                    }
                } catch (e: Exception) {
                    // Not JSON, check if it's a plain string message
                    when (message) {
                        "SCREEN_RECORDING_STARTED" -> {
                            android.util.Log.d(TAG, "Screen recording started confirmed (plain)")
                            handleScreenStarted()
                        }
                        "SCREEN_RECORDING_STOPPED" -> {
                            android.util.Log.d(TAG, "Screen recording stopped confirmed (plain)")
                            handleScreenStopped()
                        }
                        "SCREEN_RECORDING_PERMISSION_DENIED" -> {
                            android.util.Log.w(TAG, "Screen recording permission denied (plain)")
                            handlePermissionDenied()
                        }
                    }
                    
                    // Check for error messages
                    if (message.startsWith("SCREEN_RECORDING_ERROR")) {
                        android.util.Log.e(TAG, "Screen recording error (plain): $message")
                        handleError("Screen recording error")
                    }
                }
            }
        }
    }

    private fun handleScreenStarted() {
        clearConfirmationTimeout()
        isStreaming = true
        isLoading = false
        updateUI(true)
        hideLoading()
        
        // Re-attach video track if we have one (for subsequent starts)
        currentVideoTrack?.let { track ->
            android.util.Log.d(TAG, "Re-attaching existing video track")
            attachVideoTrack(track)
        }
        
        Toast.makeText(context, "Screen recording started", Toast.LENGTH_SHORT).show()
    }

    private fun handleScreenStopped() {
        clearConfirmationTimeout()
        isStreaming = false
        isLoading = false
        updateUI(false)
        hideLoading()
        removeSurfaceView()
        Toast.makeText(context, "Screen recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun handlePermissionDenied() {
        clearConfirmationTimeout()
        isStreaming = false
        isLoading = false
        hideLoading()
        showError("Screen recording permission denied")
        Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    private fun handleError(message: String) {
        clearConfirmationTimeout()
        isStreaming = false
        isLoading = false
        hideLoading()
        showError(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun listenForVideoTrack() {
        android.util.Log.d(TAG, "🎧 Setting up video track listener")
        
        // Check if stream already exists (panel reopened while streaming)
        // This is critical - tracks may have arrived before panel opened
        scope.launch {
            try {
                // Try to get the last emitted value from the flow
                // Since we use replay=1, the last value should be available
                android.util.Log.d(TAG, "🔍 Checking for existing screen video track...")
                
                // We can't directly get replay value, so we'll collect with timeout
                withTimeout(100) {
                    ConnectionManager.screenVideoTrack.collect { videoTrack ->
                        android.util.Log.d(TAG, "🖥️ Found existing screen video track!")
                        currentVideoTrack = videoTrack
                        attachVideoTrack(videoTrack)
                        // Cancel after first emission
                        throw CancellationException("Got existing track")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                android.util.Log.d(TAG, "No existing track found, will wait for new one")
            } catch (e: CancellationException) {
                // Expected - we got the track
            }
        }
        
        // Listen for video track from WebRTC
        videoTrackJob = scope.launch {
            android.util.Log.d(TAG, "🎧 Started collecting from screenVideoTrack flow")
            ConnectionManager.screenVideoTrack.collect { videoTrack ->
                android.util.Log.d(TAG, "🖥️ Screen video track received!")
                currentVideoTrack = videoTrack
                attachVideoTrack(videoTrack)
            }
        }
    }

    fun attachVideoTrack(videoTrack: VideoTrack) {
        android.util.Log.d(TAG, "Attaching video track - enabled: ${videoTrack.enabled()}, state: ${videoTrack.state()}")
        
        scope.launch(Dispatchers.Main) {
            try {
                // Store current video track
                currentVideoTrack = videoTrack
                
                // Create surface view if not exists
                if (surfaceView == null && streamContainer != null) {
                    android.util.Log.d(TAG, "Creating new SurfaceViewRenderer")
                    
                    val newSurface = SurfaceViewRenderer(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setZOrderMediaOverlay(false)
                        setEnableHardwareScaler(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    }
                    
                    // Add to container first
                    streamContainer?.addView(newSurface, 0)
                    surfaceView = newSurface
                    android.util.Log.d(TAG, "✅ SurfaceViewRenderer added to container")
                }
                
                // Initialize surface if not already initialized (using tag)
                surfaceView?.let { surface ->
                    val eglBase = ParentPeerManager.getSharedEglBase()
                    val tag = surface.tag
                    
                    if (tag != "initialized") {
                        android.util.Log.d(TAG, "Initializing surface with EGL context")
                        try {
                            surface.init(eglBase.eglBaseContext, null)
                            surface.setMirror(false)
                            surface.setEnableHardwareScaler(true)
                            surface.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            surface.tag = "initialized"
                            android.util.Log.d(TAG, "✅ SurfaceViewRenderer initialized with EGL")
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "❌ Failed to initialize surface", e)
                        }
                    } else {
                        android.util.Log.d(TAG, "Surface already initialized, skipping init")
                    }
                    
                    // Remove and re-add sink to ensure proper attachment
                    try {
                        videoTrack.removeSink(surface)
                        android.util.Log.d(TAG, "Removed existing sink")
                    } catch (e: Exception) {
                        // Ignore if sink wasn't attached
                        android.util.Log.d(TAG, "No existing sink to remove")
                    }
                    
                    videoTrack.addSink(surface)
                    android.util.Log.d(TAG, "✅ Video track attached to surface view")
                    
                    // Force enable the track
                    if (!videoTrack.enabled()) {
                        android.util.Log.w(TAG, "⚠️ Video track was disabled, enabling it")
                        videoTrack.setEnabled(true)
                    }
                }
                
                // Hide placeholder
                placeholder?.visibility = View.GONE
                android.util.Log.d(TAG, "✅ Video should now be visible")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error attaching video track", e)
                showError("Failed to display video: ${e.message}")
            }
        }
    }

    private fun toggleScreen() {
        if (isStreaming) {
            stopScreen()
        } else {
            startScreen()
        }
    }

    private fun startScreen() {
        android.util.Log.d(TAG, "Starting screen recording")
        
        // Check if already streaming (prevent duplicate commands)
        if (isStreaming) {
            android.util.Log.w(TAG, "Already streaming, ignoring start command")
            Toast.makeText(context, "Screen recording already active", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        showLoading()
        
        // CRITICAL: Set isStreaming BEFORE sending command
        // Track arrives via ontrack BEFORE confirmation message
        // So we need isStreaming=true to accept the stream when it arrives
        isStreaming = true
        
        // Send SCREEN_RECORDING_ON command
        ConnectionManager.sendCommand("SCREEN_RECORDING_ON")
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isStreaming = false // Reset on timeout
                isLoading = false
                hideLoading()
                showError("Screen recording start timeout. Please try again.")
            }
        }
    }

    private fun stopScreen() {
        android.util.Log.d(TAG, "Stopping screen recording")
        
        // Check if not streaming (prevent duplicate commands)
        if (!isStreaming) {
            android.util.Log.w(TAG, "Not streaming, ignoring stop command")
            Toast.makeText(context, "Screen recording not active", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        showLoading()
        
        // Send SCREEN_RECORDING_OFF command
        ConnectionManager.sendCommand("SCREEN_RECORDING_OFF")
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isLoading = false
                hideLoading()
                showError("Screen recording stop timeout. Please try again.")
            }
        }
    }

    private fun toggleFullscreen() {
        android.util.Log.d(TAG, "Toggling fullscreen")
        
        val videoTrack = currentVideoTrack
        if (videoTrack == null) {
            Toast.makeText(context, "No video available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create fullscreen dialog
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        // Create container for fullscreen view
        val fullscreenContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        
        // Create fullscreen surface view
        val fullscreenSurface = SurfaceViewRenderer(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setZOrderMediaOverlay(false)
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            
            // Initialize with shared EGL context
            val eglBase = ParentPeerManager.getSharedEglBase()
            init(eglBase.eglBaseContext, null)
            setMirror(false)
        }
        
        // Attach video track to fullscreen surface
        videoTrack.addSink(fullscreenSurface)
        android.util.Log.d(TAG, "Video track attached to fullscreen surface")
        
        // Add exit button
        val exitBtn = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (48 * context.resources.displayMetrics.density).toInt(),
                (48 * context.resources.displayMetrics.density).toInt()
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(16, 16, 16, 16)
            }
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.stream_control_btn_background)
            setPadding(12, 12, 12, 12)
            setColorFilter(Color.BLACK)
            setOnClickListener {
                dialog.dismiss()
            }
        }
        
        fullscreenContainer.addView(fullscreenSurface)
        fullscreenContainer.addView(exitBtn)
        
        dialog.setContentView(fullscreenContainer)
        dialog.setOnDismissListener {
            // Remove sink and clean up fullscreen surface
            videoTrack.removeSink(fullscreenSurface)
            fullscreenSurface.release()
            android.util.Log.d(TAG, "Fullscreen mode exited")
        }
        
        dialog.show()
        android.util.Log.d(TAG, "Fullscreen mode entered")
    }

    private fun clearConfirmationTimeout() {
        confirmationTimeout?.cancel()
        confirmationTimeout = null
    }

    private fun showLoading() {
        playPauseBtn?.setImageResource(R.drawable.loading_spinner)
        playPauseBtn?.isEnabled = false
    }

    private fun hideLoading() {
        playPauseBtn?.isEnabled = true
        updatePlayPauseIcon()
    }

    private fun updateUI(isActive: Boolean) {
        // Update status indicator (red = active/streaming, green = inactive/ready)
        statusIndicator?.setBackgroundColor(
            if (isActive) Color.parseColor("#ef4444") else Color.parseColor("#22c55e")
        )
        
        // Update play/pause icon
        playPauseBtn?.isEnabled = true
        updatePlayPauseIcon()
        
        // Show/hide placeholder
        if (!isActive) {
            placeholder?.visibility = View.VISIBLE
            placeholderText?.text = "Click play button to start screen recording"
            placeholderText?.setTextColor(Color.parseColor("#a1a1aa"))
        }
    }

    private fun updatePlayPauseIcon() {
        playPauseBtn?.setImageResource(
            if (isStreaming) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun removeSurfaceView() {
        android.util.Log.d(TAG, "Removing surface view")
        
        val surface = surfaceView
        val track = currentVideoTrack
        
        if (surface != null) {
            // Remove sink from video track if we have one
            if (track != null) {
                try {
                    track.removeSink(surface)
                    android.util.Log.d(TAG, "Video track sink removed")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error removing sink", e)
                }
            }
            
            // Clear the image
            try {
                surface.clearImage()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error clearing image", e)
            }
            
            // Release and remove view
            surface.release()
            streamContainer?.removeView(surface)
            android.util.Log.d(TAG, "Surface view released and removed")
        }
        
        surfaceView = null
        // Don't clear currentVideoTrack - we need it for subsequent starts
        
        // Show placeholder
        placeholder?.visibility = View.VISIBLE
        placeholderText?.text = "Click play button to start screen recording"
        placeholderText?.setTextColor(Color.parseColor("#a1a1aa"))
    }

    private fun showError(message: String) {
        placeholderText?.text = message
        placeholderText?.setTextColor(Color.parseColor("#ef4444"))
        placeholder?.visibility = View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        clearConfirmationTimeout()
        dataChannelJob?.cancel()
        videoTrackJob?.cancel()
        scope.cancel()
        removeSurfaceView()
        currentVideoTrack = null // Clear video track reference when feature is closed
    }

    override fun getTitle(): String = context.getString(R.string.feature_screen)
    override fun getDescription(): String = context.getString(R.string.feature_screen_desc)
}
