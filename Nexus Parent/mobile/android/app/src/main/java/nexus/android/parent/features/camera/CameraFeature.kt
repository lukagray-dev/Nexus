package nexus.android.parent.features.camera

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
 * CameraFeature - Remote camera monitoring feature
 * Allows parents to view live camera feed from child device
 * Uses WebRTC for real-time video streaming
 */
class CameraFeature(context: Context) : BaseFeature(context) {

    private var statusIndicator: View? = null
    private var playPauseBtn: ImageView? = null
    private var switchBtn: ImageView? = null
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
        private const val TAG = "CameraFeature"
    }

    override fun createView(container: ViewGroup): View {
        // Find views
        statusIndicator = container.findViewById(R.id.camera_status_indicator)
        playPauseBtn = container.findViewById(R.id.camera_play_pause_btn)
        switchBtn = container.findViewById(R.id.camera_switch_btn)
        fullscreenBtn = container.findViewById(R.id.camera_fullscreen_btn)
        placeholder = container.findViewById(R.id.camera_placeholder)
        placeholderText = container.findViewById(R.id.camera_placeholder_text)
        streamContainer = container.findViewById(R.id.camera_stream_container)
        
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            listenForConfirmations()
        }
        
        if (videoTrackJob == null || videoTrackJob?.isActive == false) {
            listenForVideoTrack()
        }
        
        return container
    }

    private fun setupListeners() {
        playPauseBtn?.setOnClickListener {
            if (!isLoading) {
                toggleCamera()
            }
        }
        
        switchBtn?.setOnClickListener {
            if (!isLoading) {
                if (isStreaming) {
                    switchCamera()
                } else {
                    Toast.makeText(context, "Start camera first", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        fullscreenBtn?.setOnClickListener {
            if (isStreaming && currentVideoTrack != null) {
                toggleFullscreen()
            } else {
                Toast.makeText(context, "Start camera first", Toast.LENGTH_SHORT).show()
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
                        "CAMERA_STARTED" -> {
                            android.util.Log.d(TAG, "Camera started confirmed (JSON)")
                            handleCameraStarted()
                        }
                        "CAMERA_STOPPED" -> {
                            android.util.Log.d(TAG, "Camera stopped confirmed (JSON)")
                            handleCameraStopped()
                        }
                        "CAMERA_SWITCHED" -> {
                            android.util.Log.d(TAG, "Camera switched confirmed (JSON)")
                            handleCameraSwitched()
                        }
                    }
                } catch (e: Exception) {
                    // Not JSON, check if it's a plain string message
                    when (message) {
                        "CAMERA_STARTED" -> {
                            android.util.Log.d(TAG, "Camera started confirmed (plain)")
                            handleCameraStarted()
                        }
                        "CAMERA_STOPPED" -> {
                            android.util.Log.d(TAG, "Camera stopped confirmed (plain)")
                            handleCameraStopped()
                        }
                        "CAMERA_SWITCHED" -> {
                            android.util.Log.d(TAG, "Camera switched confirmed (plain)")
                            handleCameraSwitched()
                        }
                    }
                }
            }
        }
    }

    private fun handleCameraStarted() {
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
        
        Toast.makeText(context, "Camera started", Toast.LENGTH_SHORT).show()
    }

    private fun handleCameraStopped() {
        clearConfirmationTimeout()
        isStreaming = false
        isLoading = false
        updateUI(false)
        hideLoading()
        removeSurfaceView()
        Toast.makeText(context, "Camera stopped", Toast.LENGTH_SHORT).show()
    }

    private fun handleCameraSwitched() {
        clearConfirmationTimeout()
        isLoading = false
        hideSwitchLoading()
        Toast.makeText(context, "Camera switched", Toast.LENGTH_SHORT).show()
    }

    private fun listenForVideoTrack() {
        // Listen for video track from WebRTC
        videoTrackJob = scope.launch {
            ConnectionManager.cameraVideoTrack.collect { videoTrack ->
                android.util.Log.d(TAG, "📹 Camera video track received!")
                currentVideoTrack = videoTrack
                attachVideoTrack(videoTrack)
            }
        }
    }

    fun attachVideoTrack(videoTrack: VideoTrack) {
        android.util.Log.d(TAG, "Attaching video track")
        
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
                    }
                    
                    // Remove and re-add sink to ensure proper attachment
                    try {
                        videoTrack.removeSink(surface)
                    } catch (e: Exception) {
                        // Ignore if sink wasn't attached
                    }
                    
                    videoTrack.addSink(surface)
                    android.util.Log.d(TAG, "✅ Video track attached to surface view")
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

    private fun toggleCamera() {
        if (isStreaming) {
            stopCamera()
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        android.util.Log.d(TAG, "Starting camera")
        
        isLoading = true
        showLoading()
        
        val command = JSONObject().apply {
            put("cmd", "CAMERA_ON")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isLoading = false
                hideLoading()
                showError("Camera start timeout. Please try again.")
            }
        }
    }

    private fun stopCamera() {
        android.util.Log.d(TAG, "Stopping camera")
        
        isLoading = true
        showLoading()
        
        val command = JSONObject().apply {
            put("cmd", "CAMERA_OFF")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isLoading = false
                hideLoading()
                showError("Camera stop timeout. Please try again.")
            }
        }
    }

    private fun switchCamera() {
        android.util.Log.d(TAG, "Switching camera")
        
        isLoading = true
        showSwitchLoading()
        
        val command = JSONObject().apply {
            put("cmd", "CAMERA_SWITCH")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Set timeout for switch confirmation
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Switch confirmation timeout")
                isLoading = false
                hideSwitchLoading()
                Toast.makeText(context, "Camera switch timeout", Toast.LENGTH_SHORT).show()
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
    
    private fun showSwitchLoading() {
        switchBtn?.setImageResource(R.drawable.loading_spinner)
        switchBtn?.isEnabled = false
    }
    
    private fun hideSwitchLoading() {
        switchBtn?.isEnabled = true
        switchBtn?.setImageResource(R.drawable.ic_camera)
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
            placeholderText?.text = "Click play button to start camera"
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
        placeholderText?.text = "Click play button to start camera"
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

    override fun getTitle(): String = context.getString(R.string.feature_camera)
    override fun getDescription(): String = context.getString(R.string.feature_camera_desc)
}
