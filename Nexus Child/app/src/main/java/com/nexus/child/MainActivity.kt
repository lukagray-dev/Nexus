package com.nexus.child

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexus.child.components.mic.MicCommandHandler
import com.nexus.child.components.mic.MicrophoneController
import com.nexus.child.components.mic.ParentAudioSpeakerManager
import com.nexus.child.databinding.ActivityMainBinding
import com.nexus.child.permission.PermissionGuideActivity
import com.nexus.child.permission.PermissionHelper
import com.nexus.child.permission.PermissionManager
import com.nexus.child.webrtc.AuthManager
import com.nexus.child.webrtc.DeviceIdManager
import com.nexus.child.webrtc.DeviceStatusManager
import com.nexus.child.webrtc.SignalingClient
import com.nexus.child.webrtc.WebRtcManager
import kotlinx.coroutines.launch

/**
 * MainActivity serves as the primary dashboard for the Nexus Child monitoring agent.
 *
 * Hey friend! Here is what this Activity does:
 * 1. Bootstraps anonymous Firebase authentication seamlessly in the background.
 * 2. Fetches or generates the device's persistent 12-digit Child ID.
 * 3. Shows the real 12-digit identifier on screen so the parent can type it into their desktop.
 * 4. Activates DeviceStatusManager for live online/offline heartbeat presence reporting.
 * 5. Wires SignalingClient to listen for incoming WebRTC connection offers from the parent.
 * 6. Hands the offer to WebRtcManager, which gathers local candidates (Non-Trickle ICE)
 *    and establishes the peer connection and DataChannel.
 * 7. Displays live tactical status indicators (Awaiting Connection vs. Session Active).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    private var rawDeviceId: String? = null
    private var statusManager: DeviceStatusManager? = null
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null
    private var microphoneController: MicrophoneController? = null
    private var parentAudioSpeakerManager: ParentAudioSpeakerManager? = null
    private var micCommandHandler: MicCommandHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeaderBadges()
        setupDeviceIdCard()
        setupPermissionsCard()

        // Boot the real WebRTC, Firebase, and device identification pipeline
        initializeWebRtcPipeline()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions status when returning from permission settings
        refreshPermissionsStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroying. Cleaning up WebRTC and signaling resources...")
        micCommandHandler?.cleanup()
        parentAudioSpeakerManager?.cleanup()
        microphoneController?.cleanup()
        webRtcManager?.close()
        signalingClient?.stop()
        statusManager?.stop()
    }

    /**
     * Initializes the full WebRTC, Firebase Authentication, Device ID,
     * and signaling listeners inside the Activity's lifecycleScope.
     */
    private fun initializeWebRtcPipeline() {
        binding.textDeviceId.text = getString(R.string.status_connecting)
        updateConnectionStatusUi(isConnected = false)

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Step 1: Ensuring anonymous Firebase authentication...")
                AuthManager.ensureAuthenticated(this@MainActivity)

                Log.d(TAG, "Step 2: Retrieving or generating unique 12-digit Device ID...")
                val id = DeviceIdManager.getOrGenerateDeviceId(this@MainActivity)
                rawDeviceId = id

                // Display the formatted 12-digit ID: "XXXX XXXX XXXX"
                val formattedId = DeviceIdManager.format(id)
                binding.textDeviceId.text = formattedId
                Log.d(TAG, "Device ID ready: $id (Display: $formattedId)")

                Log.d(TAG, "Step 3: Initializing presence and heartbeat status manager...")
                val statusMgr = DeviceStatusManager(id)
                statusManager = statusMgr
                statusMgr.start()

                Log.d(TAG, "Step 4: Initializing signaling client...")
                val sigClient = SignalingClient(
                    deviceId = id,
                    onOfferReceived = { offer ->
                        Log.d(TAG, "SDP Offer received from parent. Delegating to WebRtcManager...")
                        webRtcManager?.handleRemoteOffer(offer)
                    },
                    onRenegotiationAnswerReceived = { answer ->
                        Log.d(TAG, "Renegotiation Answer received from parent. Delegating to WebRtcManager...")
                        webRtcManager?.handleRenegotiationAnswer(answer)
                    }
                )
                signalingClient = sigClient

                // Clear any prior stale handshake sessions before listening
                sigClient.clearSessionData()
                sigClient.startListening()

                Log.d(TAG, "Step 5: Initializing ParentAudioSpeakerManager and WebRtcManager...")
                val speakerMgr = ParentAudioSpeakerManager(this@MainActivity)
                parentAudioSpeakerManager = speakerMgr

                val rtcManager = WebRtcManager(
                    context = this@MainActivity,
                    deviceStatusManager = statusMgr,
                    signalingClient = sigClient,
                    onConnectionStateChanged = { isConnected ->
                        Log.d(TAG, "WebRTC peer connection state changed: isConnected=$isConnected")
                        updateConnectionStatusUi(isConnected)
                        if (!isConnected) {
                            Log.d(TAG, "Connection lost or closed. Resetting microphone and loudspeaker states...")
                            micCommandHandler?.onPeerDisconnected()
                        }
                    },
                    onMessageReceived = { message ->
                        Log.d(TAG, "Control command received from parent DataChannel: $message")
                        handleDataChannelCommand(message)
                    },
                    onRemoteAudioTrackReceived = { audioTrack ->
                        speakerMgr.onRemoteAudioTrackAdded(audioTrack)
                    }
                )
                webRtcManager = rtcManager

                Log.d(TAG, "Step 6: Initializing MicrophoneController and MicCommandHandler...")
                val micCtrl = MicrophoneController(
                    context = this@MainActivity,
                    factoryProvider = { rtcManager.getPeerConnectionFactory() },
                    peerConnectionProvider = { rtcManager.getPeerConnection() }
                )
                microphoneController = micCtrl

                micCommandHandler = MicCommandHandler(
                    micController = micCtrl,
                    parentAudioSpeakerManager = speakerMgr,
                    renegotiationTrigger = { onComplete ->
                        rtcManager.performRenegotiation(onComplete)
                    },
                    dataSender = { msg ->
                        rtcManager.sendData(msg)
                    }
                )

                // Ready and awaiting parent connection
                updateConnectionStatusUi(isConnected = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC pipeline: ${e.message}", e)
                binding.textDeviceId.text = getString(R.string.status_disconnected)
            }
        }
    }

    /**
     * Updates the status LED indicator and status text label based on live connection state.
     */
    private fun updateConnectionStatusUi(isConnected: Boolean) {
        if (isConnected) {
            binding.indicatorStatusLed.setBackgroundResource(R.drawable.status_led_active)
            binding.textConnectionStatus.text = getString(R.string.status_connected)
            binding.textConnectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )
        } else {
            binding.indicatorStatusLed.setBackgroundResource(R.drawable.status_led_inactive)
            binding.textConnectionStatus.text = getString(R.string.status_ready)
            binding.textConnectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
        }
    }

    /**
     * Handles control messages incoming from the parent console over the DataChannel.
     */
    private fun handleDataChannelCommand(command: String) {
        Log.d(TAG, "Processing incoming command: $command")
        val handledByMic = micCommandHandler?.handleCommand(command) ?: false
        if (!handledByMic) {
            if (command == "PING") {
                webRtcManager?.sendData("PONG")
            }
        }
    }

    /**
     * Sets up the tactical metadata badge in the top header.
     */
    private fun setupHeaderBadges() {
        binding.badgeTargetDevice.text = PermissionHelper.getDeviceModelBadge()
    }

    /**
     * Configures the Device Identifier display and one-tap clipboard copy action.
     */
    private fun setupDeviceIdCard() {
        binding.btnCopyDeviceId.setOnClickListener {
            val idToCopy = rawDeviceId ?: binding.textDeviceId.text.toString().replace(" ", "")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Nexus Device ID", idToCopy)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, R.string.device_id_copied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds click events and initial state for the Permissions summary card.
     */
    private fun setupPermissionsCard() {
        binding.btnOpenPermissions.setOnClickListener {
            PermissionGuideActivity.start(this)
        }

        refreshPermissionsStatus()
    }

    /**
     * Evaluates all critical system permissions and updates counts and button states.
     */
    private fun refreshPermissionsStatus() {
        val grantedCount = PermissionManager.getGrantedCount(this)
        val totalCount = PermissionManager.getTotalCount()
        val allGranted = PermissionManager.areAllGranted(this)

        binding.textPermissionsStatus.text = getString(
            R.string.permission_status_format,
            grantedCount,
            totalCount
        )

        if (allGranted) {
            binding.iconPermissionsCard.setImageResource(R.drawable.ic_shield_check)
            binding.iconPermissionsCard.setColorFilter(
                ContextCompat.getColor(this, R.color.status_connected)
            )
            binding.btnOpenPermissions.text = getString(R.string.btn_permissions_complete)
        } else {
            binding.iconPermissionsCard.setImageResource(R.drawable.ic_shield)
            binding.iconPermissionsCard.setColorFilter(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.btnOpenPermissions.text = getString(R.string.btn_configure_permissions)
        }
    }
}
