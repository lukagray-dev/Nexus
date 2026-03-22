package nexus.android.child.signaling

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import nexus.android.child.configuration.AppConfig
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SignalingClient"

/**
 * Firebase signaling client for WebRTC handshake.
 * 
 * Schema:
 * - signaling/{deviceId}/offer - Parent's offer
 * - signaling/{deviceId}/answer - Child's answer
 * - signaling/{deviceId}/ice-candidates/parent[] - Parent's ICE candidates
 * - signaling/{deviceId}/ice-candidates/child[] - Child's ICE candidates
 */
class SignalingClient(
    private val deviceId: String,
    private val onOfferReceived: (SessionDescription) -> Unit,
    private val onIceCandidateReceived: (IceCandidate) -> Unit
) {
    private val db = FirebaseDatabase
        .getInstance(AppConfig.Firebase.DATABASE_URL)
        .reference.child("signaling").child(deviceId)
    
    private var offerListener: ValueEventListener? = null
    private var iceCandidateListener: ChildEventListener? = null

    // De-dupe on RTDB push keys (reconnects will re-deliver existing children)
    private val seenParentCandidateKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var started = false

    /* ─── Send SDP ─────────────────────────────── */
    
    fun sendAnswer(sdp: SessionDescription) {
        Log.d(TAG, "📤 Sending answer to Firebase")
        db.child("answer").setValue(sdp.serialize())
    }

    /* ─── Send ICE Candidate (Trickle ICE) ─────── */
    
    fun sendIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "📤 Sending ICE candidate: ${candidate.sdp.take(50)}...")
        
        val candidateData = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        
        // Append to array using push()
        db.child("ice-candidates").child("child").push().setValue(candidateData)
    }

    /* ─── Session Reset (offer/answer + ICE) ───── */

    /**
     * Deletes existing offer/answer and ICE candidate subtrees so the next handshake is fresh.
     * Intended for:
     * - Child restart
     * - Connection failure / disconnect transitions
     */
    suspend fun clearSessionData() {
        Log.d(TAG, "🧽 Clearing signaling session data (offer/answer/ice-candidates)")
        val updates = mapOf(
            "offer" to null,
            "answer" to null,
            "ice-candidates/parent" to null,
            "ice-candidates/child" to null,
        )
        db.updateChildren(updates).await()
        seenParentCandidateKeys.clear()
    }

    /* ─── Start Listeners ──────────────────────── */

    fun start() {
        if (started) return
        started = true
        listenForOffer()
        listenForParentIceCandidates()
    }

    /* ─── Listen for Offer ─────────────────────── */
    
    private fun listenForOffer() {
        offerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                val mapType = object : GenericTypeIndicator<Map<String, Any>>() {}
                snapshot.getValue(mapType)?.let { data ->
                    Log.d(TAG, "📥 Received offer from Parent")
                    val offer = runCatching { data.toSessionDescription() }.getOrNull()
                    if (offer == null || offer.description.isBlank()) {
                        Log.w(TAG, "Ignoring invalid/empty offer payload")
                        return
                    }
                    onOfferReceived(offer)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Offer listener cancelled: ${error.message}")
            }
        }
        offerListener?.let { db.child("offer").addValueEventListener(it) }
    }

    /* ─── Listen for Parent ICE Candidates (Trickle ICE) ─── */
    
    private fun listenForParentIceCandidates() {
        val parentCandidatesRef = db.child("ice-candidates").child("parent")
        iceCandidateListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val key = snapshot.key ?: return
                if (!seenParentCandidateKeys.add(key)) return

                val map = snapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {})
                val sdp = map?.get("candidate") as? String
                val sdpMid = map?.get("sdpMid") as? String
                val sdpMLineIndex = (map?.get("sdpMLineIndex") as? Long)?.toInt()
                if (sdp.isNullOrBlank() || sdpMid.isNullOrBlank() || sdpMLineIndex == null) {
                    Log.w(TAG, "Ignoring malformed ICE candidate at key=$key")
                    return
                }

                Log.d(TAG, "📥 Received Parent ICE candidate key=$key")
                onIceCandidateReceived(IceCandidate(sdpMid, sdpMLineIndex, sdp))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ ICE candidate listener cancelled: ${error.message}")
            }
        }
        iceCandidateListener?.let { parentCandidatesRef.addChildEventListener(it) }
    }

    /* ─── Cleanup ──────────────────────────────── */
    
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up signaling listeners")
        try {
            offerListener?.let { db.child("offer").removeEventListener(it) }
            iceCandidateListener?.let { db.child("ice-candidates").child("parent").removeEventListener(it) }
            offerListener = null
            iceCandidateListener = null
            seenParentCandidateKeys.clear()
            started = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup error: ${e.message}")
        }
    }
}
