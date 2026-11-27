package com.nexus.parent.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.webrtc.SessionDescription

/**
 * WebRtcExtensionsTest.kt - Verifies Firebase RTDB SDP map serialization and codec ordering.
 */
class WebRtcExtensionsTest {

    @Test
    fun `serializes and reconstructs OFFER and ANSWER SessionDescriptions`() {
        val offer = SessionDescription(SessionDescription.Type.OFFER, "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n")
        val map = offer.serialize()

        assertEquals("offer", map["type"])
        assertEquals(offer.description, map["sdp"])

        val decoded = map.toSessionDescription()
        assertNotNull(decoded)
        assertEquals(SessionDescription.Type.OFFER, decoded?.type)
        assertEquals(offer.description, decoded?.description)
        assertNull(mapOf("type" to "invalid", "sdp" to "").toSessionDescription())
    }

    @Test
    fun `sorts video codecs cleanly for hardware acceleration vs software preference`() {
        val codecs = listOf("video/VP8", "video/rtx", "video/VP9", "video/H264")

        val hwSorted = sortVideoCodecsForAcceleration(
            codecs = codecs,
            hardwareAcceleration = true,
            mimeSelector = { it }
        )
        assertEquals(listOf("video/H264", "video/VP9", "video/VP8", "video/rtx"), hwSorted)

        val swSorted = sortVideoCodecsForAcceleration(
            codecs = codecs,
            hardwareAcceleration = false,
            mimeSelector = { it }
        )
        assertEquals(listOf("video/VP8", "video/VP9", "video/H264", "video/rtx"), swSorted)
    }

    @Test
    fun `SignalingClient buildOfferPayload attaches parent UID cleanly`() {
        val offer = SessionDescription(SessionDescription.Type.OFFER, "v=0\r\n")
        val payload = SignalingClient.buildOfferPayload(offer, "parent-uid-abc")
        assertEquals("offer", payload["type"])
        assertEquals("v=0\r\n", payload["sdp"])
        assertEquals("parent-uid-abc", payload["uid"])
    }
}
