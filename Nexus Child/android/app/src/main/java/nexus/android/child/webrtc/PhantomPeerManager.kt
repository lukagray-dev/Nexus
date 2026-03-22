package nexus.android.child.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume

private const val TAG = "PhantomPeerManager"

class PhantomPeerManager(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private lateinit var eglBase: EglBase

    fun initializePeerConnectionFactory() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        eglBase = EglBase.create()
        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext, true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        
        Log.d(TAG, "✅ PeerConnectionFactory initialized")
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        Log.d(TAG, "✅ PeerConnection created")
    }

    fun setRemoteDescription(sdp: SessionDescription, sdpObserver: SdpObserver) {
        Log.d(TAG, "📥 Setting remote description (${sdp.type})")
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate): Boolean {
        Log.d(TAG, "📥 Adding ICE candidate: ${candidate.sdp.take(50)}...")
        return peerConnection?.addIceCandidate(candidate) ?: false
    }

    fun getPeerConnection(): PeerConnection? = peerConnection
    fun getFactory(): PeerConnectionFactory = peerConnectionFactory
    fun getEglBase(): EglBase = eglBase

    suspend fun createAnswer(): SessionDescription? {
        val constraints = MediaConstraints()
        
        return suspendCancellableCoroutine { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resume(null)
            
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    Log.d(TAG, "✅ Answer created, setting local description")
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "✅ Local description set")
                            cont.resume(sdp)
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "❌ Failed to set local description: $error")
                            cont.resume(null)
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to create answer: $error")
                    cont.resume(null)
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }

    suspend fun createOffer(): SessionDescription? {
        val constraints = MediaConstraints()
        
        return suspendCancellableCoroutine { cont ->
            val pc = peerConnection ?: return@suspendCancellableCoroutine cont.resume(null)
            
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    Log.d(TAG, "✅ Offer created, setting local description")
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "✅ Local description set")
                            cont.resume(sdp)
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "❌ Failed to set local description: $error")
                            cont.resume(null)
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, sdp)
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to create offer: $error")
                    cont.resume(null)
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }
}