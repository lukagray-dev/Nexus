package nexus.android.parent.webrtc

import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * FirebaseSignaling - Handles WebRTC signaling via Firebase
 * Based on desktop implementation
 */
class FirebaseSignaling(
    private val childId: String,
    private val peerManager: ParentPeerManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FirebaseSignaling"
    }

    private val database = FirebaseDatabase.getInstance()
    private val rawChildId = childId.replace("-", "")
    private val basePath = "signaling/$rawChildId"

    private var offerRef: DatabaseReference? = null
    private var answerRef: DatabaseReference? = null
    private var iceCandidatesRef: DatabaseReference? = null

    private var answerListener: ValueEventListener? = null
    private var childIceCandidatesListener: ChildEventListener? = null

    private var lastAnswerHash: Int? = null
    private var isProcessingAnswer = false

    /**
     * Setup Firebase signaling
     */
    fun setup() {
        offerRef = database.getReference("$basePath/offer")
        answerRef = database.getReference("$basePath/answer")
        iceCandidatesRef = database.getReference("$basePath/ice-candidates")

        Log.d(TAG, "🔗 Firebase signaling path: $basePath")

        // Listen for answer from child
        setupAnswerListener()

        // Listen for ICE candidates from child
        setupIceCandidateListener()
    }

    /**
     * Setup answer listener
     * Handles both:
     * 1. Initial connection: Child sends ANSWER
     * 2. Renegotiation: Child sends OFFER (role reversal)
     */
    private fun setupAnswerListener() {
        answerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val message = snapshot.value as? Map<*, *> ?: run {
                    Log.d(TAG, "⏳ Waiting for message from child...")
                    return
                }

                val type = message["type"] as? String
                val sdp = message["sdp"] as? String

                if (type == null || sdp == null) {
                    Log.w(TAG, "⚠️ Invalid message format")
                    return
                }

                // Check for duplicate
                val messageHash = sdp.hashCode()
                if (messageHash == lastAnswerHash) {
                    Log.d(TAG, "ℹ️ Ignoring duplicate message")
                    return
                }

                if (isProcessingAnswer) {
                    Log.w(TAG, "⚠️ Already processing a message, skipping...")
                    return
                }

                isProcessingAnswer = true
                
                // Determine if this is an answer (initial) or offer (renegotiation)
                val isRenegotiationOffer = type.equals("offer", ignoreCase = true)
                
                if (isRenegotiationOffer) {
                    Log.d(TAG, "📥 Renegotiation offer received from child (role reversal)")
                } else {
                    Log.d(TAG, "📥 Answer received from child")
                }

                scope.launch {
                    try {
                        val sessionDescription = SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type),
                            sdp
                        )
                        peerManager.setRemoteDescription(sessionDescription)
                        lastAnswerHash = messageHash
                        
                        if (isRenegotiationOffer) {
                            Log.d(TAG, "✅ Remote description set (renegotiation offer)")
                            
                            // Create and send answer back
                            val answer = peerManager.createAnswer()
                            if (answer != null) {
                                sendOffer(answer) // Send answer to 'offer' path (role reversal)
                                Log.d(TAG, "📤 Renegotiation answer sent to child")
                                
                                // Send completion confirmation
                                peerManager.sendCommand("RENEGOTIATION_COMPLETE")
                                Log.d(TAG, "📤 Sent RENEGOTIATION_COMPLETE to child")
                            }
                        } else {
                            Log.d(TAG, "✅ Remote description set (answer)")
                            
                            // Clear renegotiation flag for initial connection
                            peerManager.clearRenegotiationFlag()
                            
                            // Send RENEGOTIATION_COMPLETE to child
                            peerManager.sendCommand("RENEGOTIATION_COMPLETE")
                            Log.d(TAG, "📤 Sent RENEGOTIATION_COMPLETE to child")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error setting remote description: ${e.message}")
                    } finally {
                        isProcessingAnswer = false
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Answer listener cancelled: ${error.message}")
            }
        }

        answerRef?.addValueEventListener(answerListener!!)
    }

    /**
     * Setup ICE candidate listener
     */
    private fun setupIceCandidateListener() {
        childIceCandidatesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val candidateMap = snapshot.value as? Map<*, *> ?: return

                val candidate = candidateMap["candidate"] as? String
                val sdpMid = candidateMap["sdpMid"] as? String
                val sdpMLineIndex = (candidateMap["sdpMLineIndex"] as? Long)?.toInt()

                if (candidate != null && sdpMid != null && sdpMLineIndex != null) {
                    val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                    peerManager.peerConnection?.addIceCandidate(iceCandidate)
                    Log.d(TAG, "✅ Added ICE candidate from child")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ ICE candidate listener cancelled: ${error.message}")
            }
        }

        iceCandidatesRef?.child("child")?.addChildEventListener(childIceCandidatesListener!!)
    }

    /**
     * Send offer to Firebase
     */
    suspend fun sendOffer(offer: SessionDescription) {
        val offerMap = mapOf(
            "type" to offer.type.canonicalForm(),
            "sdp" to offer.description
        )

        offerRef?.setValue(offerMap)?.await()
        Log.d(TAG, "📤 Offer sent to Firebase")
    }

    /**
     * Send ICE candidate to Firebase
     */
    fun sendIceCandidate(candidate: IceCandidate) {
        val candidateMap = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )

        iceCandidatesRef?.child("parent")?.push()?.setValue(candidateMap)
        Log.d(TAG, "✅ ICE candidate sent to Firebase")
    }

    /**
     * Cleanup listeners
     */
    fun cleanup() {
        answerListener?.let { answerRef?.removeEventListener(it) }
        childIceCandidatesListener?.let { iceCandidatesRef?.child("child")?.removeEventListener(it) }

        offerRef = null
        answerRef = null
        iceCandidatesRef = null

        Log.d(TAG, "✅ Firebase signaling cleaned up")
    }
}
