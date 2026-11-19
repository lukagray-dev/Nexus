package com.nexus.child

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.child.databinding.ActivityMainBinding
import com.nexus.child.permission.PermissionGuideActivity
import com.nexus.child.permission.PermissionHelper
import com.nexus.child.permission.PermissionManager
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.scheduler.PersistentJobService
import com.nexus.child.service.watchdog.WatchdogService
import com.nexus.child.webrtc.AuthManager
import com.nexus.child.webrtc.DeviceIdManager
import kotlinx.coroutines.launch

/**
 * MainActivity serves as the tactical dashboard for the Nexus Child monitoring agent.
 *
 * Hey friend! Here is how this presentation layer operates in our production architecture:
 * 1. Clean Separation of Concerns: MainActivity NO LONGER holds WebRTC or hardware controller lifecycles.
 *    All background operations, WebRTC signaling, audio/video streaming, and command handlers
 *    run continuously inside NexusBackgroundService.
 * 2. Background Service Trigger: When MainActivity launches, it ensures NexusBackgroundService is running
 *    and registers the persistent 15-minute watchdog with PersistentJobService.
 * 3. Reactive StateFlow Observation: Observes NexusBackgroundService.isConnected using repeatOnLifecycle
 *    to display real-time connection status (LED indicator & status text).
 * 4. Device Identifier Management: Asynchronously retrieves or formats the persistent 12-digit Child ID
 *    and provides one-tap clipboard copying.
 * 5. Lifecycle Independence: If the child leaves the app or closes MainActivity, the background service
 *    remains completely active in the foreground notification area without interruption!
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var rawDeviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeaderBadges()
        setupDeviceIdCard()
        setupPermissionsCard()

        // 1. Start the persistent continuous Foreground Service & Watchdog Daemon
        NexusBackgroundService.start(this)
        WatchdogService.start(this)

        // 2. Schedule the persistent JobScheduler watchdog to survive reboots
        PersistentJobService.schedule(this)

        // 3. Populate device ID asynchronously
        loadDeviceIdentifier()

        // 4. Reactively observe live connection state from the background service
        observeConnectionState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions status counter when returning from system settings
        refreshPermissionsStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed. NexusBackgroundService continues running in the background.")
    }

    /**
     * Resolves the persistent 12-digit Child ID and displays the formatted representation.
     */
    private fun loadDeviceIdentifier() {
        binding.textDeviceId.text = getString(R.string.status_connecting)

        lifecycleScope.launch {
            try {
                // Ensure Firebase is anonymously authenticated first
                AuthManager.ensureAuthenticated(this@MainActivity)

                // Retrieve or generate unique 12-digit Device ID
                val id = DeviceIdManager.getOrGenerateDeviceId(this@MainActivity)
                rawDeviceId = id

                // Format as "XXXX XXXX XXXX" for comfortable visual reading
                val formattedId = DeviceIdManager.format(id)
                binding.textDeviceId.text = formattedId
                Log.d(TAG, "Device ID ready for display: $id ($formattedId)")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device ID: ${e.message}", e)
                binding.textDeviceId.text = getString(R.string.status_disconnected)
            }
        }
    }

    /**
     * Collects live connection status from NexusBackgroundService.isConnected StateFlow
     * while the activity is in the STARTED lifecycle state.
     */
    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NexusBackgroundService.isConnected.collect { isConnected ->
                    updateConnectionStatusUi(isConnected)
                }
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
     * Evaluates all 15 system permissions and updates counts and button states.
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
