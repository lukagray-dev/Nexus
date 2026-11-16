package com.nexus.child

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexus.child.databinding.ActivityMainBinding
import com.nexus.child.permission.PermissionGuideActivity
import com.nexus.child.permission.PermissionHelper
import com.nexus.child.permission.PermissionManager

/**
 * MainActivity serves as the primary dashboard for the Nexus Child monitoring agent.
 *
 * Designed with a strict high-contrast monochrome tactical aesthetic:
 * 1. Zero Jetpack Compose overhead - pure native Android XML with ViewBinding.
 * 2. Displays the unique Device Identifier used by Nexus Parent to initiate WebRTC surveillance.
 * 3. Shows live connection status and device metadata badges.
 * 4. Provides direct access to the step-by-step Permission Guide.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // Placeholder Dummy ID until Firebase anonymous auth & WebRTC signaling is connected
        private const val PLACEHOLDER_DEVICE_ID = "NEX-9482-6105"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeaderBadges()
        setupDeviceIdCard()
        setupPermissionsCard()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions status when returning to the dashboard
        refreshPermissionsStatus()
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
        binding.textDeviceId.text = PLACEHOLDER_DEVICE_ID

        binding.btnCopyDeviceId.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Nexus Device ID", PLACEHOLDER_DEVICE_ID)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, R.string.device_id_copied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds click events and initial state for the Permissions summary card.
     */
    private fun setupPermissionsCard() {
        binding.btnOpenPermissions.setOnClickListener {
            // Launch the dedicated step-by-step permission guide flow
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
