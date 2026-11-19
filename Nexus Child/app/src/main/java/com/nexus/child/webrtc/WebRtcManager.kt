package com.nexus.child.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebRtcManager orchestrates peer connection setup, hardware video factories,
 * Non-Trickle ICE gathering, and DataChannel messaging on Android.
 *
 * Hey friend! Here is how the WebRTC connection pipeline works step-by-step:
 * 1. Initialize PeerConnectionFactory with hardware accelerated video codecs.
 * 2. When the parent sends an SDP Offer, we set it as RemoteDescription.
 * 3. We call createAnswer() to generate our response.
 * 4. We call setLocalDescription(answer), which tells Android to start gathering
 *    local network routes and ICE candidates.
 * 5. In Non-Trickle ICE mode, we wait until iceGatheringState reaches COMPLETE.
 *    At that exact moment, all candidates are embedded directly inside the SDP text!
 * 6. We publish the complete answer to Firebase.
 * 7. WebRTC establishes the encrypted direct peer connection, and the DataChannel opens!
 */
class WebRtcManager(
    private val context: Context,
    private val deviceStatusManager: DeviceStatusManager,
    private val signalingClient: SignalingClient,
    private val onConnectionStateChanged: (isConnected: Boolean) -> Unit,
    private val onMessageReceived: (message: String) -> Unit,
    private val onRemoteAudioTrackReceived: (audioTrack: org.webrtc.AudioTrack) -> Unit = {}
) {

    companion object {
        private const val TAG = "WebRtcManager"
        private const val ICE_GATHERING_FALLBACK_TIMEOUT_MS = 3000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)

    private lateinit var eglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var telemetryManager: DeviceTelemetryManager? = null

    private val isAnswering = AtomicBoolean(false)
    private val answerSent = AtomicBoolean(false)

    // Listener for pre-registering media tracks (e.g. Camera VideoTrack) on PeerConnection creation
    private var onPeerConnectionCreatedListener: ((PeerConnection) -> Unit)? = null

    // Callback invoked when the WebRTC DataChannel transitions to OPEN
    var onDataChannelOpened: (() -> Unit)? = null

    // Renegotiation state flags (used during MIC_ON/MIC_OFF role reversal)
    private val isRenegotiating = AtomicBoolean(false)
    private val renegotiationOfferSent = AtomicBoolean(false)
    private var pendingRenegotiationOfferReadyCallback: (() -> Unit)? = null

    /**
     * Exposes the shared EGL base context used by hardware video encoder/decoder factories.
     */
    fun getEglBaseContext(): EglBase.Context? =
        if (::eglBase.isInitialized) eglBase.eglBaseContext else null

    /**
     * Exposes the active PeerConnection for media track manipulation.
     */
    fun getPeerConnection(): PeerConnection? = peerConnection

    /**
     * Registers a listener to be notified whenever a PeerConnection is created,
     * allowing media tracks (such as the camera VideoTrack) to be pre-registered
     * before SDP answer generation.
     */
    fun setOnPeerConnectionCreatedListener(listener: (PeerConnection) -> Unit) {
        onPeerConnectionCreatedListener = listener
        peerConnection?.let { listener(it) }
    }

    /**
     * Exposes the PeerConnectionFactory for AudioSource and AudioTrack creation.
     */
    fun getPeerConnectionFactory(): PeerConnectionFactory? =
        if (::peerConnectionFactory.isInitialized) peerConnectionFactory else null

    init {
        initializePeerConnectionFactory()
        telemetryManager = DeviceTelemetryManager(context) { packet ->
            sendData(packet)
        }
        // Telemetry monitoring is lazy: it only starts when the parent actually connects!
    }

    /**
     * Initializes Google WebRTC's PeerConnectionFactory with hardware-accelerated
     * video encoder and decoder factories using an EGL context.
     */
    private fun initializePeerConnectionFactory() {
        Log.d(TAG, "Initializing PeerConnectionFactory with hardware codecs...")

        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(context.applicationContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(initOptions)

        eglBase = EglBase.create()

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )

        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory initialized successfully.")
    }

    /**
     * Prepares or resets the PeerConnection instance with Google STUN servers.
     */
    fun createPeerConnection() {
        peerConnection?.close()
        peerConnection = null
        answerSent.set(false)
        isAnswering.set(false)

        Log.d(TAG, "Creating new PeerConnection with Google STUN servers...")

        val iceServers = AppConfig.WebRtc.STUN_SERVERS.map { url ->
            PeerConnection.IceServer.builder(url).createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerObserver)
        Log.d(TAG, "PeerConnection instance created.")
        peerConnection?.let { pc ->
            Log.d(TAG, "Notifying onPeerConnectionCreated listener for track pre-registration...")
            onPeerConnectionCreatedListener?.invoke(pc)
        }
    }

    /**
     * Handles an incoming SDP Offer received from the desktop parent via Firebase.
     */
    fun handleRemoteOffer(offer: SessionDescription) {
        scope.launch {
            Log.d(TAG, "Handling incoming remote offer. Current signaling state...")
            if (peerConnection == null) {
                createPeerConnection()
            }

            val pc = peerConnection ?: return@launch
            answerSent.set(false)
            isAnswering.set(true)

            Log.d(TAG, "Setting remote description (type=${offer.type.canonicalForm()})...")

            pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteDescription") {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully. Generating answer...")
                    createAnswer()
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote description: $error")
                    isAnswering.set(false)
                }
            }, offer)
        }
    }

    /**
     * Creates an SDP Answer and applies it as local description to start ICE gathering.
     */
    private fun createAnswer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints()

        pc.createAnswer(object : SimpleSdpObserver("createAnswer") {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(TAG, "Answer created successfully. Setting local description to start ICE gathering...")

                pc.setLocalDescription(object : SimpleSdpObserver("setLocalDescription") {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set. ICE candidate gathering started...")

                        // Schedule a fallback timer: if gathering doesn't complete within
                        // fallback timeout, send whatever candidates we have accumulated so far.
                        mainHandler.postDelayed({
                            if (isAnswering.get() && !answerSent.get()) {
                                Log.d(TAG, "ICE gathering timeout reached. Transmitting collected SDP answer.")
                                transmitLocalAnswerIfReady()
                            }
                        }, ICE_GATHERING_FALLBACK_TIMEOUT_MS)
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local description: $error")
                        isAnswering.set(false)
                    }
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
                isAnswering.set(false)
            }
        }, constraints)
    }

    /**
     * Transmits the local SDP answer to Firebase if it hasn't been sent yet.
     */
    private fun transmitLocalAnswerIfReady() {
        if (answerSent.getAndSet(true)) {
            return
        }

        val pc = peerConnection ?: return
        val localDesc = pc.localDescription

        if (localDesc != null && localDesc.description.isNotBlank()) {
            Log.d(TAG, "Transmitting Non-Trickle SDP answer to Firebase...")
            signalingClient.sendAnswer(localDesc)
            isAnswering.set(false)
        } else {
            Log.w(TAG, "Local description was null or blank when trying to transmit answer.")
            answerSent.set(false)
        }
    }

    /**
     * Initiates WebRTC session renegotiation with temporary role reversal (Child = Offerer).
     *
     * Hey friend! Why does Child create the offer here?
     * Because native Android AudioRecord hardware needs to be added to the PeerConnection
     * BEFORE generating the SDP offer. This ensures the SDP includes the audio track line (m=audio),
     * and when Desktop Parent answers, the audio hardware driver initializes smoothly!
     *
     * @param onOfferReady Optional callback invoked once the offer is transmitted to Firebase.
     */
    fun performRenegotiation(onOfferReady: (() -> Unit)? = null) {
        scope.launch {
            val pc = peerConnection
            if (pc == null) {
                Log.e(TAG, "Cannot renegotiate: PeerConnection is null")
                return@launch
            }

            Log.d(TAG, "Starting role-reversal renegotiation offer creation...")
            isRenegotiating.set(true)
            renegotiationOfferSent.set(false)
            pendingRenegotiationOfferReadyCallback = onOfferReady

            val constraints = MediaConstraints()
            pc.createOffer(object : SimpleSdpObserver("createRenegotiationOffer") {
                override fun onCreateSuccess(desc: SessionDescription) {
                    Log.d(TAG, "Renegotiation offer created. Setting local description...")
                    pc.setLocalDescription(object : SimpleSdpObserver("setLocalDescRenegotiation") {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set for renegotiation. Gathering ICE candidates...")
                            // In Non-Trickle ICE, if gathering is already complete, transmit immediately
                            if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                                transmitRenegotiationOfferIfReady()
                            } else {
                                // Fallback timeout: transmit collected candidates if gathering takes too long
                                mainHandler.postDelayed({
                                    if (isRenegotiating.get() && !renegotiationOfferSent.get()) {
                                        Log.d(TAG, "ICE gathering timeout for renegotiation reached. Transmitting collected SDP offer.")
                                        transmitRenegotiationOfferIfReady()
                                    }
                                }, ICE_GATHERING_FALLBACK_TIMEOUT_MS)
                            }
                        }

                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description for renegotiation: $error")
                            isRenegotiating.set(false)
                        }
                    }, desc)
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Failed to create renegotiation offer: $error")
                    isRenegotiating.set(false)
                }
            }, constraints)
        }
    }

    /**
     * Transmits the role-reversal SDP renegotiation offer to Firebase at /signaling/{id}/answer.
     */
    private fun transmitRenegotiationOfferIfReady() {
        if (renegotiationOfferSent.getAndSet(true)) {
            return
        }

        val pc = peerConnection ?: return
        val localDesc = pc.localDescription

        if (localDesc != null && localDesc.description.isNotBlank()) {
            Log.d(TAG, "Transmitting role-reversal renegotiation offer to Firebase (/answer)...")
            signalingClient.sendRenegotiationOffer(localDesc)
            val callback = pendingRenegotiationOfferReadyCallback
            pendingRenegotiationOfferReadyCallback = null
            callback?.invoke()
        } else {
            Log.w(TAG, "Local description was null or blank when trying to transmit renegotiation offer.")
            renegotiationOfferSent.set(false)
        }
    }

    /**
     * Applies the Parent's SDP Answer received during role-reversal renegotiation.
     */
    fun handleRenegotiationAnswer(answer: SessionDescription) {
        scope.launch {
            val pc = peerConnection ?: return@launch
            Log.d(TAG, "Setting parent's renegotiation answer as remote description...")
            pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteDescRenegotiationAnswer") {
                override fun onSetSuccess() {
                    Log.d(TAG, "Parent's renegotiation answer set successfully. Signaling state: ${pc.signalingState()}")
                    isRenegotiating.set(false)
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote description for renegotiation answer: $error")
                    isRenegotiating.set(false)
                }
            }, answer)
        }
    }

    /**
     * Sends a text message across the established WebRTC DataChannel.
     */
    fun sendData(message: String): Boolean {
        val channel = dataChannel
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            return channel.send(buffer)
        } else {
            Log.w(TAG, "Cannot send data: DataChannel is not open.")
            return false
        }
    }

    /**
     * Primary PeerConnection Observer handling lifecycle, ICE gathering, and DataChannel events.
     */
    private val peerObserver = object : PeerConnection.Observer {

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            Log.d(TAG, "ICE Gathering State changed: $state")
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                if (isRenegotiating.get()) {
                    Log.d(TAG, "Non-Trickle ICE gathering COMPLETE for renegotiation. Transmitting offer...")
                    transmitRenegotiationOfferIfReady()
                } else {
                    Log.d(TAG, "Non-Trickle ICE gathering COMPLETE for answer. Transmitting answer...")
                    transmitLocalAnswerIfReady()
                }
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            // In Non-Trickle ICE, candidates are bundled automatically into the local SDP
            Log.d(TAG, "Gathered local ICE candidate: ${candidate.sdp.take(40)}...")
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed.")
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.d(TAG, "PeerConnection State changed: $newState")

            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    Log.d(TAG, "WebRTC peer connection successfully ESTABLISHED.")
                    deviceStatusManager.setStatus(DeviceStatusManager.STATUS_CONNECTED)
                    mainHandler.post { onConnectionStateChanged(true) }
                }
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.CLOSED -> {
                    Log.d(TAG, "WebRTC peer connection lost or closed ($newState). Stopping telemetry...")
                    telemetryManager?.stop()
                    deviceStatusManager.setStatus(DeviceStatusManager.STATUS_ONLINE_WAITING)
                    mainHandler.post { onConnectionStateChanged(false) }
                }
                PeerConnection.PeerConnectionState.CONNECTING -> {
                    deviceStatusManager.setStatus(DeviceStatusManager.STATUS_CONNECTING)
                }
                else -> Unit
            }
        }

        override fun onDataChannel(channel: DataChannel) {
            Log.d(TAG, "Incoming DataChannel received from Parent: ${channel.label()}")
            dataChannel = channel

            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}

                override fun onStateChange() {
                    Log.d(TAG, "DataChannel '${channel.label()}' state: ${channel.state()}")
                    if (channel.state() == DataChannel.State.OPEN) {
                        Log.d(TAG, "DataChannel is OPEN. Starting telemetry listeners and dispatching initial snapshot...")
                        telemetryManager?.start()
                        telemetryManager?.onDataChannelOpened()
                        mainHandler.post { onDataChannelOpened?.invoke() }
                    } else if (channel.state() == DataChannel.State.CLOSED) {
                        Log.d(TAG, "DataChannel closed. Stopping telemetry listeners and notifying disconnection...")
                        telemetryManager?.stop()
                        mainHandler.post { onConnectionStateChanged(false) }
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    val text = String(data, StandardCharsets.UTF_8)
                    Log.d(TAG, "Received message from Parent: $text")
                    mainHandler.post { onMessageReceived(text) }
                }
            })
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            Log.d(TAG, "Signaling State: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(TAG, "ICE Connection State: $state")
            if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.CLOSED) {
                Log.d(TAG, "ICE connection lost ($state). Notifying disconnection...")
                mainHandler.post { onConnectionStateChanged(false) }
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onAddStream(stream: org.webrtc.MediaStream) {}

        override fun onRemoveStream(stream: org.webrtc.MediaStream) {}

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed.")
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {
            val track = receiver?.track()
            Log.d(TAG, "onAddTrack callback received track: kind=${track?.kind()}, id=${track?.id()}")
            if (track is org.webrtc.AudioTrack) {
                Log.d(TAG, "Passing incoming remote AudioTrack to listener for parent voice intercom...")
                onRemoteAudioTrackReceived(track)
            }
        }
    }

    /**
     * Closes the connection and cleans up resources.
     */
    fun close() {
        Log.d(TAG, "Closing WebRtcManager and releasing hardware resources...")
        try {
            isRenegotiating.set(false)
            renegotiationOfferSent.set(false)
            pendingRenegotiationOfferReadyCallback = null
            telemetryManager?.stop()
            telemetryManager = null

            dataChannel?.close()
            dataChannel = null

            peerConnection?.dispose()
            peerConnection = null

            eglBase.release()
            mainHandler.post { onConnectionStateChanged(false) }
        } catch (e: Exception) {
            Log.e(TAG, "Error during WebRtcManager close: ${e.message}", e)
        }
    }

    /**
     * Minimal helper for SdpObserver callbacks.
     */
    private open class SimpleSdpObserver(private val operationName: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "[$operationName] onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "[$operationName] onSetFailure: $error")
        }
    }
}
