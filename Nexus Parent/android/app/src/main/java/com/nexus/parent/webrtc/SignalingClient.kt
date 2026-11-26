package com.nexus.parent.webrtc

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import org.webrtc.SessionDescription

/**
 * ChildDeviceMetadata - Metadata fetched from `/child-devices/{childId}` prior to WebRTC handshake.
 */
data class ChildDeviceMetadata(
    val status: String,
    val lastSeen: Long? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val batteryPercent: Int? = null
)

/**
 * SignalingClient.kt - Firebase Realtime Database signaling client for Nexus Parent Android.
 *
 * Hey friend! This mirrors `Nexus Parent/desktop/src/ts/webrtc/signaling-client.ts` line-for-line:
 * 1. Validates `/child-devices/{childId}` exists and is reachable (`online`, `online-waiting`,
 *    `connected`, or `connecting`).
 * 2. Clears stale `/signaling/{childId}` nodes (`offer`, `answer`, `ice-candidates`).
 * 3. Publishes Parent's self-contained Non-Trickle SDP Offer to `/signaling/{childId}/offer`.
 * 4. Subscribes to `/signaling/{childId}/answer` for both initial answers and role-reversal
 *    renegotiation offers from Child, deduplicating identical SDPs.
 * 5. Publishes renegotiation SDP Answers back to `/signaling/{childId}/offer`.
 */
class SignalingClient {

    companion object {
        private const val TAG = "ParentSignalingClient"
        private val VALID_STATUSES = setOf("online", "online-waiting", "connected", "connecting")

        fun normalizeChildId(rawId: String): String {
            return rawId.replace(Regex("[\\s-]"), "").trim()
        }

        fun isReachableStatus(status: String): Boolean {
            return status in VALID_STATUSES
        }
    }

    private var activeAnswerRef: DatabaseReference? = null
    private var activeAnswerListener: ValueEventListener? = null

    private fun database(): FirebaseDatabase {
        return FirebaseDatabase.getInstance(AppConfig.Firebase.DATABASE_URL)
    }

    /**
     * Verifies that the target Child ID exists in Firebase and is online/reachable.
     */
    suspend fun validateChildDevice(childId: String): ChildDeviceMetadata {
        val rawId = normalizeChildId(childId)
        val deviceRef = database().reference.child("child-devices").child(rawId)
        Log.d(TAG, "Validating child device: $rawId")

        val snapshot = deviceRef.get().await()
        if (!snapshot.exists()) {
            throw IllegalStateException(
                "Child device \"$childId\" was not found. Please verify the 12-digit ID."
            )
        }

        val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
        val data = snapshot.getValue(mapType) ?: emptyMap()
        val status = (data["status"] as? String) ?: "unknown"
        Log.d(TAG, "Child device status: $status")

        if (!isReachableStatus(status)) {
            throw IllegalStateException(
                "Child device is currently \"$status\". Please ensure the child app is open."
            )
        }

        return ChildDeviceMetadata(
            status = status,
            lastSeen = (data["lastSeen"] as? Number)?.toLong(),
            model = data["model"] as? String,
            manufacturer = data["manufacturer"] as? String,
            androidVersion = data["androidVersion"] as? String,
            batteryPercent = (data["batteryPercent"] as? Number)?.toInt()
        )
    }

    /**
     * Publishes Parent's Non-Trickle SDP Offer to `/signaling/{childId}/offer`.
     */
    suspend fun sendOffer(childId: String, offer: SessionDescription) {
        val rawId = normalizeChildId(childId)
        val offerRef = database().reference.child("signaling").child(rawId).child("offer")
        Log.d(TAG, "Publishing Non-Trickle SDP Offer to /signaling/$rawId/offer...")
        offerRef.setValue(offer.serialize()).await()
    }

    /**
     * Subscribes to `/signaling/{childId}/answer` for initial answers and child-initiated
     * role-reversal renegotiation offers.
     */
    fun listenForAnswer(
        childId: String,
        onRemoteSdp: (SessionDescription) -> Unit
    ): () -> Unit {
        val rawId = normalizeChildId(childId)
        val answerRef = database().reference.child("signaling").child(rawId).child("answer")

        cleanupAnswerListener()
        Log.d(TAG, "Subscribing to /signaling/$rawId/answer...")

        var lastProcessedSdp: String? = null

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
                val data = snapshot.getValue(mapType) ?: return
                val desc = data.toSessionDescription() ?: return
                if (desc.description == lastProcessedSdp) return

                lastProcessedSdp = desc.description
                Log.d(TAG, "Received remote SDP from child: type=${desc.type.canonicalForm()}")
                onRemoteSdp(desc)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Answer listener cancelled: ${error.message}")
            }
        }

        activeAnswerRef = answerRef
        activeAnswerListener = listener
        answerRef.addValueEventListener(listener)

        return { cleanupAnswerListener() }
    }

    /**
     * Publishes Parent's SDP Answer back to `/signaling/{childId}/offer` during role-reversal
     * renegotiation.
     */
    suspend fun sendRenegotiationAnswer(childId: String, answer: SessionDescription) {
        val rawId = normalizeChildId(childId)
        val offerRef = database().reference.child("signaling").child(rawId).child("offer")
        Log.d(TAG, "Publishing renegotiation SDP Answer to /signaling/$rawId/offer...")
        offerRef.setValue(answer.serialize()).await()
    }

    /**
     * Clears stale signaling session paths in Firebase.
     */
    suspend fun clearSignalingSession(childId: String) {
        val rawId = normalizeChildId(childId)
        val signalingRef = database().reference.child("signaling").child(rawId)
        cleanupAnswerListener()
        Log.d(TAG, "Clearing signaling session for: $rawId")
        runCatching {
            val updates = mapOf<String, Any?>(
                "offer" to null,
                "answer" to null,
                "ice-candidates" to null
            )
            signalingRef.updateChildren(updates).await()
        }.onFailure { err ->
            Log.w(TAG, "Warning clearing signaling session: ${err.message}")
        }
    }

    fun cleanupAnswerListener() {
        val ref = activeAnswerRef
        val listener = activeAnswerListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        activeAnswerRef = null
        activeAnswerListener = null
    }
}
