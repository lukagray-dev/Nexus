package com.nexus.child.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexus.child.R
import com.nexus.child.databinding.ActivityPermissionGuideBinding

/**
 * PermissionGuideActivity provides a dedicated, step-by-step walkthrough
 * for every required permission and capability in Nexus Child.
 *
 * Capabilities covered:
 * 1. Runtime Permissions: Camera, Microphone, Location, Call Logs, Contacts,
 *    Phone State, SMS, Photos & Videos, Music & Audio, Notifications.
 * 2. System Privileges: All Files Access, Battery Optimization,
 *    Accessibility Service, Notification Listener.
 *
 * Flow logic:
 * - Direct OS runtime dialog integration with automatic state evaluation.
 * - Dynamic fallback to system settings if runtime requests are suppressed.
 * - Live onResume inspection with smooth auto-advancing upon authorization.
 */
class PermissionGuideActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PERMISSION_NAMES = "extra_permission_names"
        private const val STATE_CURRENT_INDEX = "state_current_index"

        /**
         * Launch the guided permission flow for all pending permissions
         * or a specific subset.
         */
        fun start(activity: Activity, types: List<PermissionType>? = null) {
            val intent = Intent(activity, PermissionGuideActivity::class.java).apply {
                val targets = types ?: PermissionManager.getPendingPermissions(activity)
                putStringArrayListExtra(
                    EXTRA_PERMISSION_NAMES,
                    ArrayList(targets.map { it.name })
                )
            }
            activity.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityPermissionGuideBinding
    private var pendingList: List<PermissionType> = emptyList()
    private var currentIndex: Int = 0
    private var isAdvancing: Boolean = false
    private var hasRequestedRuntimeInCurrentStep: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Handles runtime OS permission dialog callbacks.
     */
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (currentIndex < pendingList.size) {
            val current = pendingList[currentIndex]
            if (current.isGranted(this)) {
                triggerPermissionGrantedFlow()
            } else {
                // If not all permissions were granted, refresh UI so user can open settings
                updatePermissionCard(current)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Parse target permissions from caller intent, or resolve from manager
        val names = intent.getStringArrayListExtra(EXTRA_PERMISSION_NAMES)
        pendingList = if (!names.isNullOrEmpty()) {
            names.mapNotNull { name ->
                try {
                    PermissionType.valueOf(name)
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            PermissionManager.getPendingPermissions(this)
        }

        currentIndex = savedInstanceState?.getInt(STATE_CURRENT_INDEX, 0) ?: 0

        // If no permissions are pending, conclude the activity
        if (pendingList.isEmpty() || currentIndex >= pendingList.size) {
            setResult(RESULT_OK)
            finish()
            return
        }

        setupClickListeners()
        renderCurrentPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_INDEX, currentIndex)
    }

    override fun onResume() {
        super.onResume()
        if (currentIndex < pendingList.size) {
            val current = pendingList[currentIndex]
            if (current.isGranted(this) && !isAdvancing) {
                triggerPermissionGrantedFlow()
            } else {
                updatePermissionCard(current)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun setupClickListeners() {
        binding.btnGuideBack.setOnClickListener {
            finish()
        }

        binding.btnGrantAction.setOnClickListener {
            handleGrantAction()
        }

        binding.btnSkipAction.setOnClickListener {
            advanceToNextPermission()
        }
    }

    /**
     * Dispatches runtime request dialog or opens corresponding system settings screen.
     */
    private fun handleGrantAction() {
        if (currentIndex >= pendingList.size) return
        val current = pendingList[currentIndex]

        if (current.isRuntimePermission && !current.isGranted(this)) {
            val perms = current.getRequiredRuntimePermissions()
            if (perms.isNotEmpty() && !hasRequestedRuntimeInCurrentStep) {
                hasRequestedRuntimeInCurrentStep = true
                runtimePermissionLauncher.launch(perms)
                return
            }
        }

        // For system settings or if runtime dialog was already presented, launch intent
        try {
            val intent = current.getSettingsIntent(this)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(PermissionHelper.createAppDetailsIntent(this))
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    R.string.toast_unable_to_open_settings,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Prepares views for the current step.
     */
    private fun renderCurrentPermission() {
        if (currentIndex >= pendingList.size) {
            setResult(RESULT_OK)
            finish()
            return
        }

        hasRequestedRuntimeInCurrentStep = false
        val current = pendingList[currentIndex]

        binding.textStepIndicator.text = getString(
            R.string.guide_step_format,
            currentIndex + 1,
            pendingList.size
        )

        updatePermissionCard(current)
    }

    /**
     * Binds permission model data to UI elements.
     */
    private fun updatePermissionCard(item: PermissionType) {
        binding.textPermissionTitle.text = getString(item.titleRes)
        binding.textPermissionDescription.text = getString(item.descriptionRes)
        binding.textPermissionCategory.text = item.categoryName
        binding.imagePermissionIcon.setImageResource(item.iconRes)
        binding.imagePermissionIcon.setColorFilter(
            ContextCompat.getColor(this, R.color.text_primary)
        )

        val granted = item.isGranted(this)
        if (granted) {
            binding.guideStatusLed.setBackgroundResource(R.drawable.status_led_active)
            binding.guideStatusText.text = getString(R.string.guide_granted)
            binding.guideStatusText.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )
            binding.btnGrantAction.text = getString(R.string.guide_granted)
            binding.btnGrantAction.isEnabled = false
        } else {
            binding.guideStatusLed.setBackgroundResource(R.drawable.status_led_inactive)
            binding.guideStatusText.text = "PENDING AUTHORIZATION"
            binding.guideStatusText.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.btnGrantAction.isEnabled = true

            binding.btnGrantAction.text = if (item.isRuntimePermission && !hasRequestedRuntimeInCurrentStep) {
                getString(R.string.guide_grant_permission)
            } else {
                getString(R.string.guide_open_settings)
            }
        }
    }

    /**
     * Feedback sequence when permission is granted.
     */
    private fun triggerPermissionGrantedFlow() {
        if (isAdvancing) return
        isAdvancing = true

        binding.guideStatusLed.setBackgroundResource(R.drawable.status_led_active)
        binding.guideStatusText.text = getString(R.string.guide_granted)
        binding.guideStatusText.setTextColor(
            ContextCompat.getColor(this, R.color.status_connected)
        )
        binding.imagePermissionIcon.setImageResource(R.drawable.ic_shield_check)
        binding.imagePermissionIcon.setColorFilter(
            ContextCompat.getColor(this, R.color.status_connected)
        )

        mainHandler.postDelayed({
            isAdvancing = false
            advanceToNextPermission()
        }, 800)
    }

    /**
     * Moves to the next permission in the list or finishes with success.
     */
    private fun advanceToNextPermission() {
        currentIndex++
        if (currentIndex >= pendingList.size) {
            Toast.makeText(
                this,
                R.string.guide_all_done_title,
                Toast.LENGTH_SHORT
            ).show()
            setResult(RESULT_OK)
            finish()
        } else {
            renderCurrentPermission()
        }
    }
}
