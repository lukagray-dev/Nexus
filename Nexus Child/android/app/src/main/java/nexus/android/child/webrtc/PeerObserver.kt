package nexus.android.child.webrtc

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.VideoTrack

/**
 * Thin adapter that converts WebRTC callbacks into concise lambdas.
 */
class PeerObserver(
    private val onIce:   (IceCandidate) -> Unit = {},
    private val onDataChannelReceived: (DataChannel) -> Unit = {},
    private val onTrack: (VideoTrack)   -> Unit = {},
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit = {},
) : PeerConnection.Observer {

    /* ICE → signalling RTDB */
    override fun onIceCandidate(c: IceCandidate) {
        android.util.Log.d("PeerObserver", "🧊 ICE candidate generated: ${c.sdpMid}")
        onIce(c)
    }

    /* Connection state change */
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        android.util.Log.d("PeerObserver", "🔌 Connection state changed: $newState")
        onConnectionStateChange(newState)
    }

    /* DataChannel received */
    override fun onDataChannel(dc: DataChannel) {
        android.util.Log.d("PeerObserver", "📦 DataChannel received: ${dc.label()}")
        onDataChannelReceived(dc)
    }

    /* Up‑stream tracks (video and audio) */
    override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>) {
        val track = r?.track()
        when (track) {
            is VideoTrack -> onTrack(track)
            is org.webrtc.AudioTrack -> track.setEnabled(true)
        }
    }

    /* Unused WebRTC callbacks */
    override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(b: Boolean) {}
    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
    override fun onAddStream(ms: MediaStream?) {}
    override fun onRemoveStream(ms: MediaStream?) {}
    override fun onRenegotiationNeeded() {}
}
