package com.nexus.child.webrtc

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import org.webrtc.SessionDescription

/**
 * SignalingClient manages WebRTC signaling communication with Firebase Realtime Database.
 *
 * Hey friend! How do WebRTC peers connect if they don't know each other's IP address?
 * That is what "Signaling" is for:
 * 1. The desktop Parent writes its SDP Offer to `/signaling/{deviceId}/offer`.
 * 2. This SignalingClient listens to that node in Firebase.
 * 3. When an Offer arrives, we pass it to WebRtcManager to configure remote tracks
 *    and gather local ICE candidates.
 * 4. Once local ICE gathering is COMPLETE (Non-Trickle ICE), we send the self-contained
 *    SDP Answer to `/signaling/{deviceId}/answer`.
 */
class SignalingClient(
    private val deviceId: String,
    private val onOfferReceived: (SessionDescription) -> Unit
) {

    companion object {
        private const val TAG = "SignalingClient"
    }

    private val signalingRef = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference
        .child("signaling")
        .child(deviceId)

    private var offerListener: ValueEventListener? = null
    @Volatile
    private var isListening = false
    private var lastProcessedOfferSdp: String? = null

    /**
     * Clears any stale session data left behind from prior connections
     * so that the upcoming handshake starts with a clean slate.
     */
    suspend fun clearSessionData() {
        Log.d(TAG, "Clearing stale signaling session data for device: $deviceId")
        try {
            val updates = mapOf<String, Any?>(
                "offer" to null,
                "answer" to null,
                "ice-candidates" to null
            )
            signalingRef.updateChildren(updates).await()
            lastProcessedOfferSdp = null
            Log.d(TAG, "Signaling session data cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing signaling session: ${e.message}", e)
        }
    }

    /**
     * Starts listening for remote SDP Offers from the Parent console.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        Log.d(TAG, "Subscribing to /signaling/$deviceId/offer...")

        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    return
                }

                val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
                val data = snapshot.getValue(mapType) ?: return

                val offer = runCatching { data.toSessionDescription() }.getOrNull()
                if (offer == null || offer.description.isBlank()) {
                    Log.w(TAG, "Ignoring invalid or blank offer payload from Firebase.")
                    return
                }

                // Prevent reprocessing identical offer snapshots
                if (offer.description == lastProcessedOfferSdp) {
                    Log.d(TAG, "Ignoring duplicate offer with identical SDP.")
                    return
                }

                lastProcessedOfferSdp = offer.description
                Log.d(TAG, "Valid SDP Offer received from Parent. Passing to WebRtcManager...")
                onOfferReceived(offer)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Offer listener cancelled by Firebase: ${error.message}")
            }
        }

        offerListener?.let {
            signalingRef.child("offer").addValueEventListener(it)
        }
    }

    /**
     * Writes the complete, self-contained SDP Answer to /signaling/{deviceId}/answer.
     * In Non-Trickle ICE, this contains all gathered candidates embedded in the SDP text.
     *
     * @param answer The generated SessionDescription answer with all candidates gathered.
     */
    fun sendAnswer(answer: SessionDescription) {
        Log.d(TAG, "Publishing SDP Answer to /signaling/$deviceId/answer...")
        signalingRef.child("answer").setValue(answer.serialize()).addOnSuccessListener {
            Log.d(TAG, "SDP Answer published successfully to Firebase.")
        }.addOnFailureListener { error ->
            Log.e(TAG, "Failed to publish SDP Answer: ${error.message}", error)
        }
    }

    /**
     * Stops listeners and cleans up Firebase subscriptions.
     */
    fun stop() {
        Log.d(TAG, "Stopping signaling listeners for device: $deviceId")
        offerListener?.let {
            signalingRef.child("offer").removeEventListener(it)
            offerListener = null
        }
        isListening = false
        lastProcessedOfferSdp = null
    }
}
