package nexus.android.child.webrtc

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.*

/**
 * Unit tests for PeerObserver.
 * Tests callback invocations and WebRTC event handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PeerObserverTest {

    private lateinit var onIceCallback: (IceCandidate) -> Unit
    private lateinit var onDataChannelCallback: (DataChannel) -> Unit
    private lateinit var onTrackCallback: (VideoTrack) -> Unit
    private lateinit var onConnectionStateChangeCallback: (PeerConnection.PeerConnectionState) -> Unit

    @Before
    fun setup() {
        onIceCallback = mock()
        onDataChannelCallback = mock()
        onTrackCallback = mock()
        onConnectionStateChangeCallback = mock()
    }

    @Test
    fun testOnIceCandidate_invokesCallback() {
        // Given
        val observer = PeerObserver(onIce = onIceCallback)
        val iceCandidate: IceCandidate = mock()

        // When
        observer.onIceCandidate(iceCandidate)

        // Then
        verify(onIceCallback).invoke(iceCandidate)
    }

    @Test
    fun testOnDataChannel_invokesCallback() {
        // Given
        val observer = PeerObserver(onDataChannelReceived = onDataChannelCallback)
        val dataChannel: DataChannel = mock {
            on { label() } doReturn "test-channel"
        }

        // When
        observer.onDataChannel(dataChannel)

        // Then
        verify(onDataChannelCallback).invoke(dataChannel)
    }

    @Test
    fun testOnConnectionChange_invokesCallback() {
        // Given
        val observer = PeerObserver(onConnectionStateChange = onConnectionStateChangeCallback)
        val newState = PeerConnection.PeerConnectionState.CONNECTED

        // When
        observer.onConnectionChange(newState)

        // Then
        verify(onConnectionStateChangeCallback).invoke(newState)
    }

    @Test
    fun testOnAddTrack_withVideoTrack_invokesCallback() {
        // Given
        val observer = PeerObserver(onTrack = onTrackCallback)
        val videoTrack: VideoTrack = mock()
        val rtpReceiver: RtpReceiver = mock {
            on { track() } doReturn videoTrack
        }
        val mediaStreams = emptyArray<MediaStream>()

        // When
        observer.onAddTrack(rtpReceiver, mediaStreams)

        // Then
        verify(onTrackCallback).invoke(videoTrack)
    }

    @Test
    fun testOnAddTrack_withAudioTrack_enablesTrack() {
        // Given
        val observer = PeerObserver()
        val audioTrack: org.webrtc.AudioTrack = mock()
        val rtpReceiver: RtpReceiver = mock {
            on { track() } doReturn audioTrack
        }
        val mediaStreams = emptyArray<MediaStream>()

        // When
        observer.onAddTrack(rtpReceiver, mediaStreams)

        // Then
        verify(audioTrack).setEnabled(true)
    }

    @Test
    fun testOnAddTrack_withNullReceiver_doesNotCrash() {
        // Given
        val observer = PeerObserver(onTrack = onTrackCallback)
        val mediaStreams = emptyArray<MediaStream>()

        // When/Then - should not throw
        observer.onAddTrack(null, mediaStreams)
        verifyNoInteractions(onTrackCallback)
    }

    @Test
    fun testOnAddTrack_withNullTrack_doesNotCrash() {
        // Given
        val observer = PeerObserver(onTrack = onTrackCallback)
        val rtpReceiver: RtpReceiver = mock {
            on { track() } doReturn null
        }
        val mediaStreams = emptyArray<MediaStream>()

        // When/Then - should not throw
        observer.onAddTrack(rtpReceiver, mediaStreams)
        verifyNoInteractions(onTrackCallback)
    }

    @Test
    fun testUnusedCallbacks_doNotThrow() {
        // Given
        val observer = PeerObserver()

        // When/Then - should not throw
        observer.onSignalingChange(PeerConnection.SignalingState.STABLE)
        observer.onIceConnectionChange(PeerConnection.IceConnectionState.CONNECTED)
        observer.onIceConnectionReceivingChange(true)
        observer.onIceGatheringChange(PeerConnection.IceGatheringState.COMPLETE)
        observer.onIceCandidatesRemoved(emptyArray())
        observer.onAddStream(mock())
        observer.onRemoveStream(mock())
        observer.onRenegotiationNeeded()
    }

    @Test
    fun testDefaultCallbacks_doNotThrow() {
        // Given - observer with no callbacks provided
        val observer = PeerObserver()
        val iceCandidate: IceCandidate = mock()
        val dataChannel: DataChannel = mock { on { label() } doReturn "test" }

        // When/Then - should not throw with default empty lambdas
        observer.onIceCandidate(iceCandidate)
        observer.onDataChannel(dataChannel)
        observer.onConnectionChange(PeerConnection.PeerConnectionState.CONNECTED)
    }

    @Test
    fun testMultipleCallbacks_allInvoked() {
        // Given
        val observer = PeerObserver(
            onIce = onIceCallback,
            onDataChannelReceived = onDataChannelCallback,
            onTrack = onTrackCallback,
            onConnectionStateChange = onConnectionStateChangeCallback
        )

        val iceCandidate: IceCandidate = mock()
        val dataChannel: DataChannel = mock { on { label() } doReturn "test" }
        val videoTrack: VideoTrack = mock()
        val rtpReceiver: RtpReceiver = mock { on { track() } doReturn videoTrack }

        // When
        observer.onIceCandidate(iceCandidate)
        observer.onDataChannel(dataChannel)
        observer.onAddTrack(rtpReceiver, emptyArray())
        observer.onConnectionChange(PeerConnection.PeerConnectionState.CONNECTED)

        // Then
        verify(onIceCallback).invoke(iceCandidate)
        verify(onDataChannelCallback).invoke(dataChannel)
        verify(onTrackCallback).invoke(videoTrack)
        verify(onConnectionStateChangeCallback).invoke(PeerConnection.PeerConnectionState.CONNECTED)
    }

    @Test
    fun testConnectionStateChange_allStates() {
        // Given
        val observer = PeerObserver(onConnectionStateChange = onConnectionStateChangeCallback)

        // When/Then
        PeerConnection.PeerConnectionState.values().forEach { state ->
            observer.onConnectionChange(state)
            verify(onConnectionStateChangeCallback).invoke(state)
        }
    }
}
