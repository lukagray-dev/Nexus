package nexus.android.parent.features.mic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject
import org.webrtc.AudioTrack

/**
 * MicFeature - Remote microphone monitoring feature
 * Allows parents to listen to audio from child device
 * Uses WebRTC for real-time audio streaming with visualizer
 */
class MicFeature(context: Context) : BaseFeature(context) {

    private var statusIndicator: View? = null
    private var playPauseBtn: ImageView? = null
    private var muteBtn: ImageView? = null
    private var pulseRing: View? = null
    private var placeholder: LinearLayout? = null
    private var placeholderText: TextView? = null
    private var visualizerContainer: FrameLayout? = null
    private var visualizerView: AudioVisualizerView? = null
    
    private var isStreaming = false
    private var isLoading = false
    private var isParentAudioMuted = true // Parent starts muted
    private var currentAudioTrack: AudioTrack? = null
    
    // Parent audio (for speaking to child)
    private var parentAudioInitialized = false
    private var pulseAnimator: android.animation.ValueAnimator? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null
    private var audioTrackJob: Job? = null
    private var confirmationTimeout: Job? = null
    
    companion object {
        private const val TAG = "MicFeature"
        private const val REQUEST_MIC_PERMISSION = 1001
    }

    override fun createView(container: ViewGroup): View {
        // Find views
        statusIndicator = container.findViewById(R.id.mic_status_indicator)
        playPauseBtn = container.findViewById(R.id.mic_play_pause_btn)
        muteBtn = container.findViewById(R.id.mic_mute_btn)
        pulseRing = container.findViewById(R.id.mic_pulse_ring)
        placeholder = container.findViewById(R.id.mic_placeholder)
        placeholderText = container.findViewById(R.id.mic_placeholder_text)
        visualizerContainer = container.findViewById(R.id.mic_visualizer_container)
        
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            listenForConfirmations()
        }
        
        if (audioTrackJob == null || audioTrackJob?.isActive == false) {
            listenForAudioTrack()
        }
        
        return container
    }

    private fun setupListeners() {
        playPauseBtn?.setOnClickListener {
            if (!isLoading) {
                toggleMic()
            }
        }
        
        muteBtn?.setOnClickListener {
            toggleParentAudio()
        }
    }

    private fun listenForConfirmations() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                android.util.Log.d(TAG, "📨 Received data channel message: $message")
                
                try {
                    // Try parsing as JSON first
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    android.util.Log.d(TAG, "Parsed JSON type: $type")
                    
                    when (type) {
                        "MIC_STARTED" -> {
                            android.util.Log.d(TAG, "Mic started confirmed (JSON)")
                            handleMicStarted()
                        }
                        "MIC_STOPPED" -> {
                            android.util.Log.d(TAG, "Mic stopped confirmed (JSON)")
                            handleMicStopped()
                        }
                    }
                } catch (e: Exception) {
                    // Not JSON, check if it's a plain string message
                    android.util.Log.d(TAG, "Not JSON, checking plain string: $message")
                    when (message) {
                        "MIC_STARTED" -> {
                            android.util.Log.d(TAG, "Mic started confirmed (plain)")
                            handleMicStarted()
                        }
                        "MIC_STOPPED" -> {
                            android.util.Log.d(TAG, "Mic stopped confirmed (plain)")
                            handleMicStopped()
                        }
                    }
                }
            }
        }
    }

    private fun handleMicStarted() {
        android.util.Log.d(TAG, "handleMicStarted called")
        clearConfirmationTimeout()
        isStreaming = true
        isLoading = false
        android.util.Log.d(TAG, "State updated: isStreaming=$isStreaming, isLoading=$isLoading")
        updateUI(true)
        hideLoading()
        
        // Re-attach audio track if we have one (for subsequent starts)
        currentAudioTrack?.let { track ->
            android.util.Log.d(TAG, "Re-attaching existing audio track")
            attachAudioTrack(track)
        }
        
        Toast.makeText(context, "Microphone started", Toast.LENGTH_SHORT).show()
    }

    private fun handleMicStopped() {
        clearConfirmationTimeout()
        isStreaming = false
        isLoading = false
        updateUI(false)
        hideLoading()
        stopVisualizer()
        Toast.makeText(context, "Microphone stopped", Toast.LENGTH_SHORT).show()
    }

    private fun listenForAudioTrack() {
        // Listen for audio track from WebRTC
        audioTrackJob = scope.launch {
            ConnectionManager.micAudioTrack.collect { audioTrack ->
                android.util.Log.d(TAG, "🎤 Mic audio track received!")
                currentAudioTrack = audioTrack
                attachAudioTrack(audioTrack)
            }
        }
    }

    fun attachAudioTrack(audioTrack: AudioTrack) {
        android.util.Log.d(TAG, "Attaching audio track")
        
        scope.launch(Dispatchers.Main) {
            try {
                // Store current audio track
                currentAudioTrack = audioTrack
                
                // Create visualizer if not exists
                if (visualizerView == null && visualizerContainer != null) {
                    android.util.Log.d(TAG, "Creating audio visualizer")
                    
                    visualizerView = AudioVisualizerView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                    
                    // Add visualizer to container (before play button)
                    visualizerContainer?.addView(visualizerView, 0)
                    android.util.Log.d(TAG, "✅ Audio visualizer added to container")
                }
                
                // Start visualizer animation
                visualizerView?.startAnimation()
                
                // Hide placeholder
                placeholder?.visibility = View.GONE
                android.util.Log.d(TAG, "✅ Audio visualizer should now be visible")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error attaching audio track", e)
                showError("Failed to display audio: ${e.message}")
            }
        }
    }

    private fun toggleMic() {
        if (isStreaming) {
            stopMic()
        } else {
            startMic()
        }
    }
    
    private fun toggleParentAudio() {
        // Check microphone permission first
        if (!checkMicrophonePermission()) {
            requestMicrophonePermission()
            return
        }
        
        scope.launch {
            if (!parentAudioInitialized) {
                // Initialize parent audio on first use
                val success = initializeParentAudio()
                if (!success) {
                    Toast.makeText(context, "Failed to access microphone", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }
            
            if (isParentAudioMuted) {
                unmuteParentAudio()
            } else {
                muteParentAudio()
            }
        }
    }
    
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestMicrophonePermission() {
        // Get the activity from context
        val activity = context as? android.app.Activity
        if (activity != null) {
            val dialogView = android.view.LayoutInflater.from(context)
                .inflate(R.layout.dialog_mic_permission, null)
            
            val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.dialog_cancel_btn)
            val grantButton = dialogView.findViewById<android.widget.Button>(R.id.dialog_grant_btn)
            
            val dialog = android.app.AlertDialog.Builder(context, R.style.DarkDialogTheme)
                .setView(dialogView)
                .setCancelable(true)
                .create()
            
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
            
            grantButton.setOnClickListener {
                dialog.dismiss()
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_MIC_PERMISSION
                )
            }
            
            dialog.show()
        } else {
            Toast.makeText(context, "Unable to request permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun initializeParentAudio(): Boolean = withContext(Dispatchers.Main) {
        try {
            android.util.Log.d(TAG, "🎤 Initializing parent audio...")
            
            // Request microphone permission and add audio track to peer connection
            val success = ConnectionManager.initializeParentAudio()
            
            if (success) {
                parentAudioInitialized = true
                android.util.Log.d(TAG, "✅ Parent audio initialized (muted)")
                Toast.makeText(context, "Microphone ready", Toast.LENGTH_SHORT).show()
                return@withContext true
            } else {
                android.util.Log.e(TAG, "❌ Failed to initialize parent audio")
                return@withContext false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error initializing parent audio", e)
            return@withContext false
        }
    }
    
    private fun unmuteParentAudio() {
        android.util.Log.d(TAG, "🔊 Unmuting parent audio")
        
        // Enable parent audio track
        ConnectionManager.setParentAudioEnabled(true)
        isParentAudioMuted = false
        
        // Send command to child to increase volume
        val command = JSONObject().apply {
            put("cmd", "PARENT_AUDIO_UNMUTE")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Update UI
        muteBtn?.setImageResource(R.drawable.ic_mic_unmute)
        muteBtn?.setBackgroundResource(R.drawable.stream_control_btn_background_active)
        
        // Start pulsing animation
        startPulseAnimation()
        
        Toast.makeText(context, "Speaking to child", Toast.LENGTH_SHORT).show()
    }
    
    private fun muteParentAudio() {
        android.util.Log.d(TAG, "🔇 Muting parent audio")
        
        // Disable parent audio track
        ConnectionManager.setParentAudioEnabled(false)
        isParentAudioMuted = true
        
        // Send command to child to restore volume
        val command = JSONObject().apply {
            put("cmd", "PARENT_AUDIO_MUTE")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Update UI
        muteBtn?.setImageResource(R.drawable.ic_mic_mute)
        muteBtn?.setBackgroundResource(R.drawable.stream_control_btn_background)
        
        // Stop pulsing animation
        stopPulseAnimation()
        
        Toast.makeText(context, "Stopped speaking", Toast.LENGTH_SHORT).show()
    }
    
    private fun startPulseAnimation() {
        pulseRing?.visibility = View.VISIBLE
        
        // Create pulsing animation
        pulseAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                // Scale from 1.0 to 1.3
                val scale = 1.0f + (value * 0.3f)
                pulseRing?.scaleX = scale
                pulseRing?.scaleY = scale
                // Fade from 0.8 to 0.2
                pulseRing?.alpha = 0.8f - (value * 0.6f)
            }
            
            start()
        }
    }
    
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseRing?.visibility = View.GONE
        pulseRing?.alpha = 0f
        pulseRing?.scaleX = 1f
        pulseRing?.scaleY = 1f
    }
    
    /**
     * Handle permission result from MainActivity
     */
    fun onPermissionResult(requestCode: Int, granted: Boolean) {
        if (requestCode == REQUEST_MIC_PERMISSION) {
            if (granted) {
                Toast.makeText(context, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                // Try to toggle again now that permission is granted
                toggleParentAudio()
            } else {
                Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startMic() {
        android.util.Log.d(TAG, "Starting microphone")
        
        isLoading = true
        android.util.Log.d(TAG, "isLoading set to true, calling showLoading()")
        showLoading()
        
        val command = JSONObject().apply {
            put("cmd", "MIC_ON")
        }
        ConnectionManager.sendCommand(command.toString())
        android.util.Log.d(TAG, "MIC_ON command sent")
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isLoading = false
                hideLoading()
                showError("Mic start timeout. Please try again.")
            }
        }
    }

    private fun stopMic() {
        android.util.Log.d(TAG, "Stopping microphone")
        
        isLoading = true
        showLoading()
        
        val command = JSONObject().apply {
            put("cmd", "MIC_OFF")
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Set timeout
        confirmationTimeout = scope.launch {
            delay(30000) // 30 seconds
            if (isLoading) {
                android.util.Log.w(TAG, "Confirmation timeout")
                isLoading = false
                hideLoading()
                showError("Mic stop timeout. Please try again.")
            }
        }
    }

    private fun clearConfirmationTimeout() {
        confirmationTimeout?.cancel()
        confirmationTimeout = null
    }

    private fun showLoading() {
        android.util.Log.d(TAG, "showLoading called, button: $playPauseBtn")
        playPauseBtn?.let { btn ->
            android.util.Log.d(TAG, "Setting loading spinner on button")
            btn.setImageResource(R.drawable.loading_spinner)
            btn.isEnabled = false
            android.util.Log.d(TAG, "Loading spinner set, button enabled: ${btn.isEnabled}")
        } ?: android.util.Log.e(TAG, "playPauseBtn is null!")
    }

    private fun hideLoading() {
        android.util.Log.d(TAG, "hideLoading called, isStreaming: $isStreaming, button: $playPauseBtn")
        playPauseBtn?.let { btn ->
            btn.isEnabled = true
            android.util.Log.d(TAG, "Button enabled, calling updatePlayPauseIcon()")
            updatePlayPauseIcon()
        } ?: android.util.Log.e(TAG, "playPauseBtn is null in hideLoading!")
    }

    private fun updateUI(isActive: Boolean) {
        android.util.Log.d(TAG, "updateUI called, isActive: $isActive")
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
            placeholderText?.text = "Audio visualizer will appear here"
            placeholderText?.setTextColor(Color.parseColor("#a1a1aa"))
        }
    }

    private fun updatePlayPauseIcon() {
        val iconRes = if (isStreaming) R.drawable.ic_pause else R.drawable.ic_play
        android.util.Log.d(TAG, "updatePlayPauseIcon called, isStreaming: $isStreaming, setting icon: $iconRes")
        playPauseBtn?.setImageResource(iconRes)
    }

    private fun stopVisualizer() {
        android.util.Log.d(TAG, "Stopping visualizer")
        visualizerView?.stopAnimation()
        visualizerView?.let { view ->
            visualizerContainer?.removeView(view)
        }
        visualizerView = null
        
        // Show placeholder
        placeholder?.visibility = View.VISIBLE
        placeholderText?.text = "Audio visualizer will appear here"
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
        audioTrackJob?.cancel()
        scope.cancel()
        stopVisualizer()
        currentAudioTrack = null // Clear audio track reference when feature is closed
    }

    override fun getTitle(): String = context.getString(R.string.feature_mic)
    override fun getDescription(): String = context.getString(R.string.feature_mic_desc)
}

/**
 * Custom view for audio visualization
 * Displays animated frequency bars
 */
class AudioVisualizerView(context: Context) : View(context) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount = 64
    private val barHeights = FloatArray(barCount) { 0f }
    private var animationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        setBackgroundColor(Color.BLACK)
    }
    
    fun startAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            while (isActive) {
                // Simulate audio levels (in real implementation, get from AudioTrack)
                for (i in barHeights.indices) {
                    barHeights[i] = (Math.random() * 0.8 + 0.2).toFloat()
                }
                invalidate()
                delay(50) // 20 FPS
            }
        }
    }
    
    fun stopAnimation() {
        animationJob?.cancel()
        barHeights.fill(0f)
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val barWidth = width.toFloat() / barCount
        val maxBarHeight = height * 0.8f
        
        for (i in barHeights.indices) {
            val barHeight = barHeights[i] * maxBarHeight
            val x = i * barWidth
            val y = height - barHeight
            
            // Create gradient for bars
            val gradient = LinearGradient(
                x, y, x, height.toFloat(),
                intArrayOf(
                    Color.parseColor("#fbbf24"), // Yellow
                    Color.parseColor("#f59e0b"), // Orange
                    Color.parseColor("#ef4444")  // Red
                ),
                null,
                Shader.TileMode.CLAMP
            )
            
            paint.shader = gradient
            paint.style = Paint.Style.FILL
            
            canvas.drawRect(
                x + 1,
                y,
                x + barWidth - 1,
                height.toFloat(),
                paint
            )
        }
    }
}
