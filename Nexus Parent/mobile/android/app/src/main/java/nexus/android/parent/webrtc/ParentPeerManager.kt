package nexus.android.parent.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.*

/**
 * ParentPeerManager - Main WebRTC Manager
 * Handles peer connection, data channel, and media streams
 * Based on desktop WebRTCManager.js
 */
class ParentPeerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ParentPeerManager"
        private const val DATA_CHANNEL_LABEL = "nexus-control"
        
        // Shared EGL context for all video renderers
        private var sharedEglBase: EglBase? = null
        
        fun getSharedEglBase(): EglBase {
            if (sharedEglBase == null) {
                sharedEglBase = EglBase.create()
            }
            return sharedEglBase!!
        }
    }

    // WebRTC components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    internal var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // State
    private var currentChildId: String? = null
    private var isRenegotiating = false
    private var lastAnswerHash: Int? = null

    // Data channel events
    private val _dataChannelEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1024
    )
    val dataChannelEvents: SharedFlow<String> = _dataChannelEvents

    // Connection state
    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    // Callbacks
    var onConnectionEstablished: ((DataChannel) -> Unit)? = null
    var onStreamReceived: ((String, MediaStream) -> Unit)? = null

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
        object Closed : ConnectionState()
    }

    /**
     * Initialize PeerConnectionFactory
     */
    fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val eglBase = getSharedEglBase()
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "✅ PeerConnectionFactory initialized with shared EGL context")
    }

    /**
     * Create RTCPeerConnection
     */
    fun createPeerConnection(): PeerConnection {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "🧊 ICE candidate generated")
                    // Will be sent via Firebase signaling
                }
            }

            override fun onDataChannel(dc: DataChannel?) {
                // Child creates data channel, we receive it here
                dc?.let {
                    Log.d(TAG, "📡 DataChannel received from child: ${it.label()}")
                    dataChannel = it
                    setupDataChannelObserver(it)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "🌐 ICE Connection State: $state")
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "🔌 Connection State: $state")
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        scope.launch {
                            _connectionState.emit(ConnectionState.Connected)
                        }
                    }
                    PeerConnection.PeerConnectionState.FAILED -> {
                        scope.launch {
                            _connectionState.emit(ConnectionState.Failed("Connection failed"))
                        }
                    }
                    PeerConnection.PeerConnectionState.CLOSED -> {
                        scope.launch {
                            _connectionState.emit(ConnectionState.Closed)
                        }
                    }
                    else -> {}
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                stream?.let {
                    Log.d(TAG, "🎥 Media stream received")
                    handleMediaStream(it)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                transceiver?.receiver?.track()?.let { track ->
                    Log.d(TAG, "🎥 Track received: ${track.kind()}")
                    // Create a media stream with a unique ID
                    val streamId = "stream_${System.currentTimeMillis()}"
                    val stream = peerConnectionFactory?.createLocalMediaStream(streamId)
                    
                    // Add track based on type
                    when (track) {
                        is VideoTrack -> stream?.addTrack(track)
                        is AudioTrack -> stream?.addTrack(track)
                        else -> Log.w(TAG, "Unknown track type: ${track.kind()}")
                    }
                    
                    stream?.let { handleMediaStream(it) }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "📶 Signaling State: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "📶 ICE Gathering State: $state")
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "🔄 Renegotiation needed")
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "🧊 ICE candidates removed")
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        Log.d(TAG, "✅ PeerConnection created")

        // Create data channel (parent creates it)
        val dataChannelInit = DataChannel.Init().apply {
            ordered = true
        }
        dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dataChannelInit)
        dataChannel?.let {
            setupDataChannelObserver(it)
            Log.d(TAG, "✅ DataChannel created: ${it.label()}")
        }

        return peerConnection!!
    }

    /**
     * Setup data channel observer
     */
    private fun setupDataChannelObserver(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                Log.d(TAG, "📡 DataChannel state: ${dc.state()}")
                if (dc.state() == DataChannel.State.OPEN) {
                    Log.d(TAG, "🚀 DataChannel is open")
                    onConnectionEstablished?.invoke(dc)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    val data = ByteArray(it.data.remaining())
                    it.data.get(data)
                    val message = String(data, Charsets.UTF_8)
                    Log.d(TAG, "📩 From Child: $message")
                    
                    // Emit to flow
                    scope.launch {
                        Log.d(TAG, "📤 Emitting to dataChannelEvents flow")
                        _dataChannelEvents.emit(message)
                        Log.d(TAG, "✅ Emitted to flow successfully")
                    }
                }
            }

            override fun onBufferedAmountChange(amount: Long) {
                if (amount > 16_000_000L) {
                    Log.w(TAG, "⚠️ DataChannel buffer very high: $amount bytes")
                }
            }
        })
    }

    /**
     * Handle received media stream
     */
    private fun handleMediaStream(stream: MediaStream) {
        val videoTracks = stream.videoTracks
        val audioTracks = stream.audioTracks

        Log.d(TAG, "📹 Stream: ${stream.id}, Video tracks: ${videoTracks.size}, Audio tracks: ${audioTracks.size}")

        if (videoTracks.isNotEmpty()) {
            val track = videoTracks[0]
            val trackId = track.id().lowercase()
            val streamId = stream.id.lowercase()
            
            // Check both track ID and stream ID for screen identification (matches desktop logic)
            val isScreen = trackId.contains("screen") || 
                          trackId.contains("display") ||
                          streamId.contains("screen")
            
            val streamType = if (isScreen) "screen" else "camera"
            
            Log.d(TAG, "📹 Video track identified as: $streamType")
            Log.d(TAG, "📹 Track ID: \"${track.id()}\", Stream ID: \"${stream.id}\"")
            onStreamReceived?.invoke(streamType, stream)
        }

        if (audioTracks.isNotEmpty()) {
            Log.d(TAG, "🎤 Audio track received")
            onStreamReceived?.invoke("mic", stream)
        }
    }

    /**
     * Add video transceiver for receiving
     */
    fun addVideoTransceiver() {
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        Log.d(TAG, "✅ Added video transceiver (recvonly)")
    }

    /**
     * Add audio transceiver for receiving
     */
    fun addAudioTransceiver() {
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        Log.d(TAG, "✅ Added audio transceiver (recvonly)")
    }

    /**
     * Create offer
     */
    suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { continuation ->
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "✅ Local description set")
                            continuation.resume(it) {}
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "❌ Set local description failed: $error")
                            continuation.cancel(Exception(error))
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "❌ Create offer failed: $error")
                continuation.cancel(Exception(error))
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Create answer (for renegotiation when child sends offer)
     */
    suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { continuation ->
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "✅ Local description set (answer)")
                            continuation.resume(it) {}
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "❌ Set local description failed: $error")
                            continuation.cancel(Exception(error))
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "❌ Create answer failed: $error")
                continuation.cancel(Exception(error))
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Set remote description (answer from child)
     */
    suspend fun setRemoteDescription(sdp: SessionDescription) = suspendCancellableCoroutine<Unit> { continuation ->
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "✅ Remote description set")
                continuation.resume(Unit) {}
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "❌ Set remote description failed: $error")
                continuation.cancel(Exception(error))
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    /**
     * Send command to child
     */
    fun sendCommand(command: String): Boolean {
        return if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(command.toByteArray(Charsets.UTF_8)),
                false
            )
            dataChannel?.send(buffer) ?: false.also {
                Log.d(TAG, "📤 Sent command: $command")
            }
        } else {
            Log.e(TAG, "❌ DataChannel not ready")
            false
        }
    }
    
    /**
     * Send binary data to child
     */
    fun sendBinaryData(data: ByteArray): Boolean {
        return if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(data),
                true // binary = true
            )
            dataChannel?.send(buffer) ?: false
        } else {
            Log.e(TAG, "❌ DataChannel not ready for binary data")
            false
        }
    }
    
    // Parent audio (for speaking to child)
    private var parentAudioSource: AudioSource? = null
    private var parentAudioTrack: AudioTrack? = null
    private var parentAudioRtpSender: RtpSender? = null
    
    /**
     * Initialize parent audio - get microphone access but keep muted
     */
    suspend fun initializeParentAudio(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎤 Initializing parent audio...")
            
            // Create audio source
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            
            parentAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            parentAudioTrack = peerConnectionFactory?.createAudioTrack("parent_audio", parentAudioSource)
            
            // Start muted
            parentAudioTrack?.setEnabled(false)
            
            // Find the audio transceiver and change its direction to SEND_RECV
            peerConnection?.let { pc ->
                val transceivers = pc.transceivers
                var audioTransceiver: RtpTransceiver? = null
                
                Log.d(TAG, "📊 Found ${transceivers.size} transceivers")
                
                for (transceiver in transceivers) {
                    val receiver = transceiver.receiver
                    val track = receiver.track()
                    
                    Log.d(TAG, "🔍 Transceiver: kind=${track?.kind()}, direction=${transceiver.direction}, mid=${transceiver.mid}")
                    
                    if (track?.kind() == "audio") {
                        audioTransceiver = transceiver
                        break
                    }
                }
                
                if (audioTransceiver != null) {
                    Log.d(TAG, "🔄 Found existing audio transceiver, changing direction to SEND_RECV")
                    
                    // Change direction to SEND_RECV
                    audioTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    
                    // Replace the sender's track with our microphone track
                    val sender = audioTransceiver.sender
                    if (sender != null) {
                        sender.setTrack(parentAudioTrack, false)
                        parentAudioRtpSender = sender
                        Log.d(TAG, "✅ Replaced audio track in existing transceiver")
                    }
                } else {
                    // No audio transceiver exists, add a new one
                    Log.d(TAG, "➕ No audio transceiver found, adding new one")
                    parentAudioRtpSender = pc.addTrack(parentAudioTrack, listOf("parent_stream"))
                }
                
                Log.d(TAG, "✅ Parent audio track configured (muted)")
                
                // Log final transceiver states
                for (transceiver in pc.transceivers) {
                    val track = transceiver.receiver.track()
                    val hasSender = transceiver.sender.track() != null
                    val senderEnabled = transceiver.sender.track()?.enabled() ?: false
                    Log.d(TAG, "🔍 Final Transceiver: kind=${track?.kind()}, direction=${transceiver.direction}, mid=${transceiver.mid}, hasSenderTrack=$hasSender, senderEnabled=$senderEnabled")
                }
                
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize parent audio", e)
            return@withContext false
        }
    }
    
    /**
     * Set parent audio enabled/disabled
     */
    fun setParentAudioEnabled(enabled: Boolean) {
        parentAudioTrack?.setEnabled(enabled)
        
        if (enabled) {
            Log.d(TAG, "🔊 Parent audio enabled")
            // Log track state
            parentAudioTrack?.let { track ->
                Log.d(TAG, "🔊 Audio track state: id=${track.id()}, enabled=${track.enabled()}, state=${track.state()}")
            }
            
            // Log sender state
            parentAudioRtpSender?.let { sender ->
                Log.d(TAG, "📡 RTP Sender: id=${sender.id()}, track=${sender.track()?.id()}")
            }
            
            // Log transceiver direction
            peerConnection?.transceivers?.forEach { transceiver ->
                val track = transceiver.receiver.track()
                if (track?.kind() == "audio") {
                    Log.d(TAG, "📡 Audio transceiver direction: ${transceiver.direction}")
                    Log.d(TAG, "📡 Audio transceiver mid: ${transceiver.mid}")
                }
            }
        } else {
            Log.d(TAG, "🔇 Parent audio disabled")
        }
    }

    /**
     * Cleanup and close connection
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up...")
        
        // Cleanup parent audio
        parentAudioRtpSender?.let { sender ->
            peerConnection?.removeTrack(sender)
        }
        parentAudioTrack?.dispose()
        parentAudioSource?.dispose()
        parentAudioRtpSender = null
        parentAudioTrack = null
        parentAudioSource = null
        
        dataChannel?.close()
        dataChannel = null
        
        peerConnection?.close()
        peerConnection = null
        
        currentChildId = null
        
        Log.d(TAG, "✅ Cleanup complete")
    }
    
    /**
     * Renegotiate connection (create new offer)
     * Used when adding parent audio track
     */
    suspend fun renegotiate(firebaseSignaling: FirebaseSignaling) {
        Log.d(TAG, "🔄 Starting renegotiation...")
        
        val signalingState = peerConnection?.signalingState()
        Log.d(TAG, "Current signaling state: $signalingState")
        
        if (signalingState != PeerConnection.SignalingState.STABLE) {
            Log.w(TAG, "⚠️ Signaling state not stable, waiting...")
            kotlinx.coroutines.delay(500)
            
            if (peerConnection?.signalingState() != PeerConnection.SignalingState.STABLE) {
                Log.w(TAG, "⚠️ Still not stable, aborting renegotiation")
                return
            }
        }
        
        if (isRenegotiating) {
            Log.w(TAG, "⚠️ Renegotiation already in progress, skipping...")
            return
        }
        
        isRenegotiating = true
        lastAnswerHash = null
        Log.d(TAG, "🗑️ Reset lastAnswerHash to accept new answer")
        
        try {
            // Log transceiver states
            peerConnection?.transceivers?.forEach { transceiver ->
                val track = transceiver.receiver.track()
                val hasSender = transceiver.sender.track() != null
                val senderEnabled = transceiver.sender.track()?.enabled() ?: false
                Log.d(TAG, "🔍 Transceiver: kind=${track?.kind()}, direction=${transceiver.direction}, mid=${transceiver.mid}, hasSenderTrack=$hasSender, senderEnabled=$senderEnabled")
            }
            
            // Create new offer
            val offer = createOffer()
            Log.d(TAG, "✅ New offer created")
            
            // Send new offer to Firebase
            firebaseSignaling.sendOffer(offer)
            Log.d(TAG, "📤 New offer sent to Firebase for renegotiation")
            Log.d(TAG, "Waiting for answer from child...")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during renegotiation", e)
            isRenegotiating = false
            throw e
        }
    }
    
    /**
     * Clear renegotiation flag (called after answer is received)
     */
    fun clearRenegotiationFlag() {
        isRenegotiating = false
        Log.d(TAG, "✅ Renegotiation complete")
    }
}
