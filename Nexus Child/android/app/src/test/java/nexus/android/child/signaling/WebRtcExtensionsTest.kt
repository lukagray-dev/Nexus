package nexus.android.child.signaling

import org.junit.Assert.*
import org.junit.Test
import org.webrtc.SessionDescription

/**
 * Unit tests for WebRTC extension functions.
 * Tests serialization and deserialization of SessionDescription objects.
 */
class WebRtcExtensionsTest {

    @Test
    fun testSerialize_withOfferType_returnsCorrectMap() {
        // Given
        val sdp = "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\n"
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)

        // When
        val result = sessionDescription.serialize()

        // Then
        assertEquals("offer", result["type"])
        assertEquals(sdp, result["sdp"])
        assertEquals(2, result.size)
    }

    @Test
    fun testSerialize_withAnswerType_returnsCorrectMap() {
        // Given
        val sdp = "v=0\r\no=- 789012 3 IN IP4 192.168.1.1\r\n"
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)

        // When
        val result = sessionDescription.serialize()

        // Then
        assertEquals("answer", result["type"])
        assertEquals(sdp, result["sdp"])
    }

    @Test
    fun testSerialize_withEmptySdp_returnsMapWithEmptyString() {
        // Given
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, "")

        // When
        val result = sessionDescription.serialize()

        // Then
        assertEquals("offer", result["type"])
        assertEquals("", result["sdp"])
    }

    @Test
    fun testToSessionDescription_withValidOfferMap_returnsOffer() {
        // Given
        val map = mapOf(
            "type" to "offer",
            "sdp" to "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\n"
        )

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.OFFER, result.type)
        assertEquals("v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\n", result.description)
    }

    @Test
    fun testToSessionDescription_withValidAnswerMap_returnsAnswer() {
        // Given
        val map = mapOf(
            "type" to "answer",
            "sdp" to "v=0\r\no=- 789012 3 IN IP4 192.168.1.1\r\n"
        )

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.ANSWER, result.type)
        assertEquals("v=0\r\no=- 789012 3 IN IP4 192.168.1.1\r\n", result.description)
    }

    @Test
    fun testToSessionDescription_withMissingType_returnsDefaultOffer() {
        // Given
        val map = mapOf("sdp" to "v=0\r\n")

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.OFFER, result.type)
        assertEquals("", result.description)
    }

    @Test
    fun testToSessionDescription_withMissingSdp_returnsEmptyOffer() {
        // Given
        val map = mapOf("type" to "offer")

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.OFFER, result.type)
        assertEquals("", result.description)
    }

    @Test
    fun testToSessionDescription_withEmptyMap_returnsDefaultOffer() {
        // Given
        val map = emptyMap<String, Any>()

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.OFFER, result.type)
        assertEquals("", result.description)
    }

    @Test
    fun testToSessionDescription_withNullType_returnsDefaultOffer() {
        // Given
        val map = mapOf(
            "type" to null,
            "sdp" to "v=0\r\n"
        )

        // When
        val result = map.toSessionDescription()

        // Then
        assertEquals(SessionDescription.Type.OFFER, result.type)
        assertEquals("", result.description)
    }

    @Test
    fun testToSessionDescription_withInvalidType_throwsException() {
        // Given
        val map = mapOf(
            "type" to "invalid_type",
            "sdp" to "v=0\r\n"
        )

        // When/Then - fromCanonicalForm throws IllegalArgumentException for invalid types
        try {
            map.toSessionDescription()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testRoundTrip_offerSerializeAndDeserialize_preservesData() {
        // Given
        val originalSdp = "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\ns=WebRTC Session\r\n"
        val original = SessionDescription(SessionDescription.Type.OFFER, originalSdp)

        // When
        val serialized = original.serialize()
        val deserialized = serialized.toSessionDescription()

        // Then
        assertEquals(original.type, deserialized.type)
        assertEquals(original.description, deserialized.description)
    }

    @Test
    fun testRoundTrip_answerSerializeAndDeserialize_preservesData() {
        // Given
        val originalSdp = "v=0\r\no=- 789012 3 IN IP4 192.168.1.1\r\ns=Answer Session\r\n"
        val original = SessionDescription(SessionDescription.Type.ANSWER, originalSdp)

        // When
        val serialized = original.serialize()
        val deserialized = serialized.toSessionDescription()

        // Then
        assertEquals(original.type, deserialized.type)
        assertEquals(original.description, deserialized.description)
    }
}
