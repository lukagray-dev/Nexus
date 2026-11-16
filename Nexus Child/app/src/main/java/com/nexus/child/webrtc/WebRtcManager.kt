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
    private val onMessageReceived: (message: String) -> Unit
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
                Log.d(TAG, "Non-Trickle ICE gathering COMPLETE. Transmitting answer...")
                transmitLocalAnswerIfReady()
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
                    } else if (channel.state() == DataChannel.State.CLOSED) {
                        Log.d(TAG, "DataChannel closed. Stopping telemetry listeners...")
                        telemetryManager?.stop()
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
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onAddStream(stream: org.webrtc.MediaStream) {}

        override fun onRemoveStream(stream: org.webrtc.MediaStream) {}

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed.")
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {}
    }

    /**
     * Closes the connection and cleans up resources.
     */
    fun close() {
        Log.d(TAG, "Closing WebRtcManager and releasing hardware resources...")
        try {
            telemetryManager?.stop()
            telemetryManager = null

            dataChannel?.close()
            dataChannel = null

            peerConnection?.dispose()
            peerConnection = null

            eglBase.release()
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
