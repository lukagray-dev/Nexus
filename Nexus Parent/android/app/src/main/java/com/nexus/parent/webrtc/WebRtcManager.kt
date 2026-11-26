package com.nexus.parent.webrtc

import android.content.Context
import android.util.Log
import com.nexus.parent.media.mic.AudioSpectrumAnalyzer
import com.nexus.parent.media.mic.VoiceChangerDspEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class VideoFeedType {
    CAMERA,
    SCREEN
}

/**
 * WebRtcManager.kt - Native WebRTC peer connection & DataChannel engine for Nexus Parent Android.
 *
 * Hey friend! Here is the exact parity pipeline matching Desktop `webrtc-manager.ts`:
 * 1. Initializes `PeerConnectionFactory` with hardware video codecs + shared `EglBase.Context`.
 * 2. Authenticates anonymously with Firebase and validates `/child-devices/{childId}`.
 * 3. Creates a fresh `PeerConnection` using `GATHER_ONCE` (200-400ms Non-Trickle ICE).
 * 4. Adds `recvonly` video transceiver (codec-ordered) and `sendrecv` audio transceiver, plus the
 *    ordered `'nexus-control'` DataChannel.
 * 5. Publishes self-contained Offer SDP to `/signaling/{childId}/offer` and awaits Answer on
 *    `/signaling/{childId}/answer` (30s timeout).
 * 6. Keeps the `/answer` listener alive after connect to handle role-reversal renegotiation offers
 *    (`type == OFFER && signalingState == STABLE`), answering immediately on `/offer` and sending
 *    `RENEGOTIATION_COMPLETE` over the DataChannel!
 */
class WebRtcManager(context: Context) {

    companion object {
        private const val TAG = "ParentWebRtcManager"
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val signalingClient = SignalingClient()

    private val eglBase: EglBase
    private val audioDeviceModule: JavaAudioDeviceModule
    private val peerConnectionFactory: PeerConnectionFactory

    val voiceChangerDsp = VoiceChangerDspEngine()
    val audioSpectrumAnalyzer = AudioSpectrumAnalyzer()

    private var peerConnection: PeerConnection? = null
    private var audioTransceiver: RtpTransceiver? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var dataChannel: DataChannel? = null
    private var currentChildId: String? = null
    private var unsubscribeAnswer: (() -> Unit)? = null
    private var iceGatheringDeferred: CompletableDeferred<Unit>? = null

    private var rawVideoTrack: VideoTrack? = null
    private var activeVideoType: VideoFeedType? = null

    @Volatile
    private var remoteMicAudioEnabled = false

    private val remoteAudioSink = AudioTrackSink { audioData, _, _, numberOfChannels, _, _ ->
        audioSpectrumAnalyzer.onRenderPcmBuffer(audioData, numberOfChannels)
    }
    private var sinkAttachedTrack: AudioTrack? = null

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _activeVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val activeVideoTrack: StateFlow<VideoTrack?> = _activeVideoTrack.asStateFlow()

    private val _remoteAudioTrack = MutableStateFlow<AudioTrack?>(null)
    val remoteAudioTrack: StateFlow<AudioTrack?> = _remoteAudioTrack.asStateFlow()

    private val _dataChannelMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dataChannelMessages: SharedFlow<String> = _dataChannelMessages.asSharedFlow()

    private val _dataChannelOpened = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dataChannelOpened: SharedFlow<Unit> = _dataChannelOpened.asSharedFlow()

    init {
        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(appContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioBufferCallback { audioBuffer, _, channelCount, sampleRate, bytesRead, captureTimeNs ->
                // Mutate 10ms captured PCM bytes in-place with active Voice Changer preset BEFORE Opus encoding!
                val boundedSlice = audioBuffer.duplicate().apply {
                    position(0)
                    limit(bytesRead.coerceIn(0, capacity()))
                }
                voiceChangerDsp.processCaptureBufferInPlace(boundedSlice, sampleRate, channelCount)
                captureTimeNs
            }
            .createAudioDeviceModule()

        eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun getEglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    fun getActiveVideoType(): VideoFeedType? = activeVideoType

    fun setActiveVideoType(type: VideoFeedType?) {
        if (activeVideoType == type) return
        activeVideoType = type
        _activeVideoTrack.value = if (type != null) rawVideoTrack else null
    }

    fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteMicAudioEnabled = enabled
        _remoteAudioTrack.value?.let { track ->
            runCatching {
                track.setEnabled(enabled)
                track.setVolume(if (enabled) 1.0 else 0.0)
            }
        }
    }

    /**
     * Captures Parent's local microphone into WebRTC and attaches it to the existing `SEND_RECV`
     * audio transceiver sender without triggering SDP renegotiation.
     */
    @Synchronized
    fun startOutboundAudioTrack(noiseSuppression: Boolean): Boolean {
        val transceiver = audioTransceiver ?: return false
        stopOutboundAudioTrack()

        return runCatching {
            runCatching { audioDeviceModule.setNoiseSuppressorEnabled(noiseSuppression) }
            val constraints = buildAudioConstraints(noiseSuppression)
            val source = peerConnectionFactory.createAudioSource(constraints)
                ?: return false
            val trackId = "NEXUS_PARENT_MIC_${System.currentTimeMillis()}"
            val track = peerConnectionFactory.createAudioTrack(trackId, source)
                ?: run {
                    source.dispose()
                    return false
                }
            track.setEnabled(true)
            val attached = transceiver.sender.setTrack(track, false)
            if (!attached) {
                track.setEnabled(false)
                track.dispose()
                source.dispose()
                return false
            }
            localAudioSource = source
            localAudioTrack = track
            true
        }.getOrElse { err ->
            Log.e(TAG, "Failed starting outbound parent audio track: ${err.message}", err)
            stopOutboundAudioTrack()
            false
        }
    }

    @Synchronized
    fun stopOutboundAudioTrack() {
        runCatching {
            audioTransceiver?.sender?.setTrack(null, false)
        }
        runCatching {
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
        }
        localAudioTrack = null

        runCatching {
            localAudioSource?.dispose()
        }
        localAudioSource = null
    }

    @Synchronized
    fun reconfigureOutboundAudio(noiseSuppression: Boolean): Boolean {
        if (localAudioTrack == null) return true
        return startOutboundAudioTrack(noiseSuppression)
    }

    private fun buildAudioConstraints(noiseSuppression: Boolean): MediaConstraints {
        val flag = noiseSuppression.toString()
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", flag))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
    }

    /**
     * Initiates a Fast Non-Trickle WebRTC handshake with the target child device.
     */
    suspend fun connect(
        childId: String,
        hardwareAcceleration: Boolean,
        onProgress: ((String) -> Unit)? = null
    ): ChildDeviceMetadata {
        val rawId = SignalingClient.normalizeChildId(childId)
        Log.d(TAG, "Starting connection sequence for Child ID: $rawId")

        onProgress?.invoke("Authenticating session...")
        AuthManager.ensureAuthenticated(appContext)

        onProgress?.invoke("Verifying child device presence...")
        val metadata = signalingClient.validateChildDevice(rawId)
        currentChildId = rawId

        signalingClient.clearSignalingSession(rawId)

        onProgress?.invoke("Establishing peer connection...")
        val pc = createFreshPeerConnection()

        // 1. Add Video (recvonly) and Audio (sendrecv) transceivers matching Desktop
        val videoTransceiver = pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        applyVideoCodecPreference(videoTransceiver, hardwareAcceleration)

        audioTransceiver = pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        )

        // 2. Create ordered 'nexus-control' DataChannel
        setupDataChannel(pc)

        // 3. Create SDP Offer & gather local + STUN ICE candidates (GATHER_ONCE)
        val offer = pc.createOfferSuspend()
        pc.setLocalDescriptionSuspend(offer)
        waitForIceGatheringComplete(pc, AppConfig.WebRtc.ICE_GATHERING_TIMEOUT_MS)

        val completedSdp = pc.localDescription
            ?: throw IllegalStateException("Local description was null after ICE gathering completed.")

        signalingClient.sendOffer(rawId, completedSdp)

        // 4. Await Child's Non-Trickle Answer & retain listener for role-reversal renegotiation
        awaitInitialHandshakeAndBindRenegotiation(rawId, pc)

        return metadata
    }

    private suspend fun awaitInitialHandshakeAndBindRenegotiation(
        rawId: String,
        pc: PeerConnection
    ) {
        val handshakeCompleted = CompletableDeferred<Unit>()

        unsubscribeAnswer = signalingClient.listenForAnswer(rawId) { remoteSdp ->
            scope.launch {
                try {
                    val state = pc.signalingState()
                    if (state == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                        Log.d(TAG, "Applying initial Non-Trickle SDP Answer from child...")
                        pc.setRemoteDescriptionSuspend(remoteSdp)
                        if (!handshakeCompleted.isCompleted) {
                            handshakeCompleted.complete(Unit)
                        }
                        return@launch
                    }

                    // Role-Reversal Renegotiation Offer from Child (e.g. MIC_ON / MIC_OFF)
                    if (remoteSdp.type == SessionDescription.Type.OFFER &&
                        state == PeerConnection.SignalingState.STABLE
                    ) {
                        Log.d(TAG, "Handling role-reversal renegotiation OFFER from child...")
                        pc.setRemoteDescriptionSuspend(remoteSdp)
                        val parentAnswer = pc.createAnswerSuspend()
                        pc.setLocalDescriptionSuspend(parentAnswer)
                        signalingClient.sendRenegotiationAnswer(rawId, parentAnswer)
                        sendCommand("RENEGOTIATION_COMPLETE")
                        Log.d(TAG, "Renegotiation answer published and RENEGOTIATION_COMPLETE sent.")
                    }
                } catch (err: Exception) {
                    Log.e(TAG, "Signaling processing failed: ${err.message}", err)
                    if (!handshakeCompleted.isCompleted) {
                        handshakeCompleted.completeExceptionally(err)
                    }
                }
            }
        }

        try {
            withTimeout(AppConfig.WebRtc.INITIAL_HANDSHAKE_TIMEOUT_MS) {
                handshakeCompleted.await()
            }
        } catch (err: Throwable) {
            disconnect(clearRemoteSignaling = false)
            if (err is kotlinx.coroutines.TimeoutCancellationException) {
                throw IllegalStateException("Connection timed out waiting for child device response.")
            }
            throw err
        }
    }

    private fun createFreshPeerConnection(): PeerConnection {
        teardownPeerObjectsOnly()

        val iceServers = AppConfig.WebRtc.STUN_SERVERS.map { url ->
            PeerConnection.IceServer.builder(url).createIceServer()
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, peerObserver)
            ?: throw IllegalStateException("Failed to create PeerConnection.")
        peerConnection = pc
        _connectionState.value = PeerConnection.PeerConnectionState.NEW
        return pc
    }

    private fun setupDataChannel(pc: PeerConnection) {
        val init = DataChannel.Init().apply { ordered = true }
        val dc = pc.createDataChannel(AppConfig.WebRtc.DATA_CHANNEL_NAME, init)
        dataChannel = dc

        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                val state = dc.state()
                Log.d(TAG, "DataChannel '${dc.label()}' state: $state")
                if (state == DataChannel.State.OPEN) {
                    _dataChannelOpened.tryEmit(Unit)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                _dataChannelMessages.tryEmit(text)
            }
        })
    }

    fun sendCommand(command: String): Boolean {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val bytes = command.toByteArray(StandardCharsets.UTF_8)
            return dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        }
        Log.w(TAG, "Cannot send command '$command': DataChannel is not open.")
        return false
    }

    fun disconnect(clearRemoteSignaling: Boolean = true) {
        Log.d(TAG, "Disconnecting active WebRTC session...")
        unsubscribeAnswer?.invoke()
        unsubscribeAnswer = null

        val childIdToClear = currentChildId
        currentChildId = null

        // Proactive DISCONNECT over DataChannel so Child performs immediate hardware teardown
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            runCatching { sendCommand("DISCONNECT") }
        }

        teardownPeerObjectsOnly()

        if (clearRemoteSignaling && childIdToClear != null) {
            scope.launch {
                signalingClient.clearSignalingSession(childIdToClear)
            }
        }
        _connectionState.value = PeerConnection.PeerConnectionState.CLOSED
    }

    private fun teardownPeerObjectsOnly() {
        stopOutboundAudioTrack()
        audioTransceiver = null
        remoteMicAudioEnabled = false

        sinkAttachedTrack?.let { prevTrack ->
            runCatching { prevTrack.removeSink(remoteAudioSink) }
        }
        sinkAttachedTrack = null

        rawVideoTrack = null
        activeVideoType = null
        _activeVideoTrack.value = null
        _remoteAudioTrack.value = null
        iceGatheringDeferred?.cancel()
        iceGatheringDeferred = null

        runCatching { dataChannel?.close() }
        dataChannel = null

        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }
        peerConnection = null
    }

    private fun applyVideoCodecPreference(
        transceiver: RtpTransceiver?,
        hardwareAcceleration: Boolean
    ) {
        if (transceiver == null) return
        runCatching {
            val caps = peerConnectionFactory.getRtpReceiverCapabilities(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
            )
            if (caps != null && caps.codecs.isNotEmpty()) {
                val sorted = sortVideoCodecsForAcceleration(caps.codecs, hardwareAcceleration) {
                    "video/${it.name}"
                }
                transceiver.setCodecPreferences(sorted)
            }
        }.onFailure { err ->
            Log.w(TAG, "Could not apply video codec preferences: ${err.message}")
        }
    }

    private suspend fun waitForIceGatheringComplete(pc: PeerConnection, maxWaitMs: Long) {
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
        val deferred = CompletableDeferred<Unit>()
        iceGatheringDeferred = deferred
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            deferred.complete(Unit)
            return
        }
        runCatching {
            withTimeout(maxWaitMs) { deferred.await() }
        }
        if (iceGatheringDeferred === deferred) {
            iceGatheringDeferred = null
        }
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                iceGatheringDeferred?.complete(Unit)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.d(TAG, "PeerConnection state changed: $newState")
            _connectionState.value = newState
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            Log.d(TAG, "ICE connection state changed: $newState")
            if (newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                newState == PeerConnection.IceConnectionState.FAILED ||
                newState == PeerConnection.IceConnectionState.CLOSED
            ) {
                val current = _connectionState.value
                if (current == PeerConnection.PeerConnectionState.CONNECTED) {
                    _connectionState.value = PeerConnection.PeerConnectionState.DISCONNECTED
                }
            }
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            val track = receiver?.track() ?: return
            when (track) {
                is AudioTrack -> {
                    if (sinkAttachedTrack !== track) {
                        sinkAttachedTrack?.let { prev ->
                            runCatching { prev.removeSink(remoteAudioSink) }
                        }
                        runCatching { track.addSink(remoteAudioSink) }
                        sinkAttachedTrack = track
                    }
                    runCatching {
                        track.setEnabled(remoteMicAudioEnabled)
                        track.setVolume(if (remoteMicAudioEnabled) 1.0 else 0.0)
                    }
                    _remoteAudioTrack.value = track
                }
                is VideoTrack -> {
                    rawVideoTrack = track
                    if (activeVideoType != null) {
                        _activeVideoTrack.value = track
                    }
                }
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    private suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    if (cont.isActive) cont.resume(desc)
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createOffer failed"))
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.createAnswerSuspend(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    if (cont.isActive) cont.resume(desc)
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createAnswer failed"))
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.setLocalDescriptionSuspend(desc: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            setLocalDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed"))
                }
            }, desc)
        }

    private suspend fun PeerConnection.setRemoteDescriptionSuspend(desc: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed"))
                }
            }, desc)
        }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
