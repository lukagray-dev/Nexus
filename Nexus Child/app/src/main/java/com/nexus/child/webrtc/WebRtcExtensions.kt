package com.nexus.child.webrtc

import org.webrtc.SessionDescription

/**
 * Extension functions for converting WebRTC SessionDescription objects
 * to and from Firebase-compatible maps.
 *
 * Hey friend! WebRTC SessionDescription contains an SDP text string and a type
 * (either "offer" or "answer"). Firebase Realtime Database stores nested key-value
 * objects, so these helpers make it clean and easy to serialize and deserialize them.
 */

/**
 * Converts a SessionDescription into a map with "type" and "sdp" entries.
 */
fun SessionDescription.serialize(): Map<String, String> {
    return mapOf(
        "type" to this.type.canonicalForm(),
        "sdp" to this.description
    )
}

/**
 * Reconstructs a SessionDescription from a Firebase map snapshot.
 */
fun Map<*, *>.toSessionDescription(): SessionDescription {
    val rawType = this["type"] as? String ?: "offer"
    val rawSdp = this["sdp"] as? String ?: ""
    val sdpType = SessionDescription.Type.fromCanonicalForm(rawType)
    return SessionDescription(sdpType, rawSdp)
}
