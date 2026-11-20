package com.nexus.child.components.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nexus.child.R
import com.nexus.child.databinding.ActivityAppLockSetupBinding

/**
 * AppLockSetupActivity provides the tactical configuration interface for App Lock.
 *
 * Hey friend! Here is how this setup workflow functions:
 * 1. Two-Step Verification: When configuring a PIN, the user enters 6 digits in Step 1,
 *    then confirms by typing the identical 6 digits in Step 2.
 * 2. Mismatch Protection: If the confirmation digits differ, an error is surfaced, the dots shake,
 *    and the setup state cleanly resets to Step 1 for a fresh attempt.
 * 3. Master Toggle: Allows enabling or disabling App Lock once a valid PIN exists on the device.
 * 4. Synchronization: Changes written here are instantly saved to AppLockManager and reflected
 *    across MainActivity and remote parent telemetry.
 */
class AppLockSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppLockSetupActivity"

        fun start(context: Context) {
            val intent = Intent(context, AppLockSetupActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityAppLockSetupBinding
    private val enteredPin = StringBuilder()
    private val pinDots = mutableListOf<ImageView>()

    // Two-step setup state
    private var isConfirming = false
    private var primaryPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLockSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupKeypad()
        refreshUiState()
    }

    private fun initViews() {
        // Back navigation
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Initialize 6 PIN dots
        pinDots.clear()
        pinDots.add(binding.setupDot1)
        pinDots.add(binding.setupDot2)
        pinDots.add(binding.setupDot3)
        pinDots.add(binding.setupDot4)
        pinDots.add(binding.setupDot5)
        pinDots.add(binding.setupDot6)
        updatePinDots()

        // Enable / Disable Switch listener
        binding.switchAppLockEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !AppLockManager.hasPinSet(this)) {
                // If turning on without a PIN, instruct user to finish PIN setup
                Toast.makeText(this, R.string.app_lock_enter_pin, Toast.LENGTH_SHORT).show()
                binding.switchAppLockEnable.isChecked = false
            } else {
                AppLockManager.setAppLockEnabled(this, isChecked)
                refreshUiState()
                val msgRes = if (isChecked) R.string.app_lock_enabled_toast else R.string.app_lock_disabled_toast
                Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupKeypad() {
        val numberButtons = listOf(
            binding.setupBtn0 to "0",
            binding.setupBtn1 to "1",
            binding.setupBtn2 to "2",
            binding.setupBtn3 to "3",
            binding.setupBtn4 to "4",
            binding.setupBtn5 to "5",
            binding.setupBtn6 to "6",
            binding.setupBtn7 to "7",
            binding.setupBtn8 to "8",
            binding.setupBtn9 to "9"
        )

        numberButtons.forEach { (view, digit) ->
            view.setOnClickListener {
                onDigitPressed(digit)
            }
        }

        binding.setupBtnBackspace.setOnClickListener {
            onBackspacePressed()
        }

        binding.setupBtnClear.setOnClickListener {
            onClearPressed()
        }
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length < AppLockConstants.PIN_LENGTH) {
            enteredPin.append(digit)
            updatePinDots()
            binding.textSetupError.visibility = View.GONE

            if (enteredPin.length == AppLockConstants.PIN_LENGTH) {
                handlePinEntryComplete()
            }
        }
    }

    private fun onBackspacePressed() {
        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updatePinDots()
            binding.textSetupError.visibility = View.GONE
        }
    }

    private fun onClearPressed() {
        enteredPin.clear()
        updatePinDots()
        binding.textSetupError.visibility = View.GONE
    }

    private fun handlePinEntryComplete() {
        val currentEntry = enteredPin.toString()

        if (!isConfirming) {
            // Step 1 completed: proceed to confirmation step
            primaryPin = currentEntry
            isConfirming = true
            enteredPin.clear()
            updatePinDots()

            binding.textStepPrompt.text = getString(R.string.app_lock_setup_step2)
            binding.textStepHint.text = "Type the identical 6-digit PIN to confirm"
        } else {
            // Step 2 completed: check if confirmation matches
            if (currentEntry == primaryPin) {
                // PIN matched! Save and activate
                val success = AppLockManager.setPin(this, currentEntry)
                if (success) {
                    AppLockManager.setAppLockEnabled(this, true)
                    Toast.makeText(this, R.string.app_lock_pin_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                // Mismatch: show error and reset to Step 1
                shakeView(binding.setupPinDotsContainer)
                binding.textSetupError.text = getString(R.string.app_lock_pin_mismatch)
                binding.textSetupError.visibility = View.VISIBLE

                isConfirming = false
                primaryPin = null
                enteredPin.clear()
                updatePinDots()

                binding.textStepPrompt.text = getString(R.string.app_lock_setup_step1)
                binding.textStepHint.text = "Choose a secure 6-digit PIN code"
            }
        }
    }

    private fun updatePinDots() {
        for (i in 0 until AppLockConstants.PIN_LENGTH) {
            val isFilled = i < enteredPin.length
            pinDots[i].setImageResource(
                if (isFilled) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun refreshUiState() {
        val isEnabled = AppLockManager.isAppLockEnabled(this)
        val hasPin = AppLockManager.hasPinSet(this)

        binding.switchAppLockEnable.isChecked = isEnabled

        if (isEnabled) {
            binding.textSetupStatusBadge.text = getString(R.string.app_lock_status_enabled)
            binding.textSetupStatusBadge.setTextColor(
                ContextCompat.getColor(this, R.color.status_connected)
            )
        } else {
            binding.textSetupStatusBadge.text = getString(R.string.app_lock_status_disabled)
            binding.textSetupStatusBadge.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }

        if (hasPin && !isConfirming) {
            binding.textStepPrompt.text = getString(R.string.btn_change_app_lock_pin)
            binding.textStepHint.text = "Enter a new 6-digit PIN to overwrite the existing code"
        }
    }

    private fun shakeView(view: View) {
        val animation = AnimationUtils.loadAnimation(this, R.anim.shake)
        view.startAnimation(animation)
    }
}
