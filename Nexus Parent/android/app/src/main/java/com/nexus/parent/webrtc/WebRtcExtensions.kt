package com.nexus.parent.webrtc

import org.webrtc.SessionDescription

/**
 * WebRtcExtensions.kt - Pure SDP serialization and codec-ordering helpers.
 *
 * Hey friend! Keeping SDP serialization and codec ranking in pure Kotlin functions lets us
 * unit test them on the JVM without booting Android or native JNI libraries.
 */

fun SessionDescription.serialize(): Map<String, String> {
    return mapOf(
        "type" to type.canonicalForm(),
        "sdp" to description
    )
}

fun Map<*, *>.toSessionDescription(): SessionDescription? {
    val rawType = this["type"] as? String ?: return null
    val rawSdp = this["sdp"] as? String ?: return null
    if (rawSdp.isBlank()) return null
    val sdpType = runCatching { SessionDescription.Type.fromCanonicalForm(rawType) }.getOrNull()
        ?: return null
    return SessionDescription(sdpType, rawSdp)
}

/**
 * Sorts MIME video codec names for hardware acceleration (`H264 -> VP9 -> VP8`) or software
 * fallback (`VP8 -> VP9 -> H264`), matching Desktop `sortVideoCodecsForAcceleration()`.
 */
fun <T> sortVideoCodecsForAcceleration(
    codecs: List<T>,
    hardwareAcceleration: Boolean,
    mimeSelector: (T) -> String
): List<T> {
    val priority = if (hardwareAcceleration) {
        listOf("video/h264", "video/vp9", "video/vp8")
    } else {
        listOf("video/vp8", "video/vp9", "video/h264")
    }
    return codecs.sortedBy { codec ->
        val idx = priority.indexOf(mimeSelector(codec).lowercase())
        if (idx == -1) priority.size else idx
    }
}
