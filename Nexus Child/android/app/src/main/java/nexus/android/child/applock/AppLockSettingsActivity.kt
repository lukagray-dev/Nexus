package nexus.android.child.applock

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import nexus.android.child.R

/**
 * Settings activity for managing app lock configuration.
 * Allows enabling/disabling lock, setting/changing PIN.
 */
class AppLockSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppLockSettings"
    }

    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var setPinButton: MaterialButton
    private lateinit var changePinButton: MaterialButton
    private lateinit var resetButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)

        initViews()
        updateUI()
    }

    private fun initViews() {
        enableSwitch = findViewById(R.id.app_lock_enable_switch)
        statusText = findViewById(R.id.app_lock_status_text)
        setPinButton = findViewById(R.id.btn_set_pin)
        changePinButton = findViewById(R.id.btn_change_pin)
        resetButton = findViewById(R.id.btn_reset_lock)

        // Enable/Disable switch
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppLockManager.setAppLockEnabled(this, isChecked)
            updateUI()
            Toast.makeText(
                this,
                if (isChecked) "App lock enabled" else "App lock disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Set PIN button
        setPinButton.setOnClickListener {
            showSetPinDialog()
        }

        // Change PIN button
        changePinButton.setOnClickListener {
            showChangePinDialog()
        }

        // Reset button
        resetButton.setOnClickListener {
            showResetConfirmDialog()
        }
    }

    private fun updateUI() {
        val isEnabled = AppLockManager.isAppLockEnabled(this)
        val hasPinSet = AppLockManager.hasPinSet(this)
        val failedAttempts = AppLockManager.getFailedAttempts(this)
        val isLockedOut = AppLockManager.isLockedOut(this)

        enableSwitch.isChecked = isEnabled

        // Update status text
        val statusLines = mutableListOf<String>()
        statusLines.add("Status: ${if (isEnabled) "Enabled" else "Disabled"}")
        statusLines.add("PIN Set: ${if (hasPinSet) "Yes" else "No"}")
        statusLines.add("Failed Attempts: $failedAttempts/${AppLockManager.MAX_FAILED_ATTEMPTS}")

        if (isLockedOut) {
            val remainingMs = AppLockManager.getRemainingLockoutTime(this)
            val minutes = remainingMs / 60000
            val seconds = (remainingMs % 60000) / 1000
            statusLines.add("Locked out for ${minutes}m ${seconds}s")
        }

        statusText.text = statusLines.joinToString("\n")

        // Enable/disable buttons based on state
        setPinButton.isEnabled = isEnabled
        changePinButton.isEnabled = isEnabled && hasPinSet
        resetButton.isEnabled = failedAttempts > 0 || isLockedOut
    }

    private fun showSetPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 4-digit PIN"
            maxLines = 1
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Set App Lock PIN")
            .setMessage("Enter a 4-digit PIN to lock the app")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val pin = input.text.toString().trim()
                if (validatePin(pin)) {
                    showConfirmPinDialog(pin, isChanging = false)
                } else {
                    Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmPinDialog(pin: String, isChanging: Boolean) {
        val confirmInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Confirm PIN"
            maxLines = 1
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm PIN")
            .setMessage("Re-enter the PIN to confirm")
            .setView(confirmInput)
            .setPositiveButton("Confirm") { _, _ ->
                val confirmPin = confirmInput.text.toString().trim()
                if (confirmPin == pin) {
                    AppLockManager.setPin(this, pin)
                    Toast.makeText(
                        this,
                        if (isChanging) "PIN changed successfully" else "PIN set successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUI()
                    Log.d(TAG, if (isChanging) "PIN changed successfully" else "PIN set successfully")
                } else {
                    Toast.makeText(this, "PINs do not match. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePinDialog() {
        val oldPinInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter current PIN"
            maxLines = 1
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Change PIN")
            .setMessage("Enter your current PIN first")
            .setView(oldPinInput)
            .setPositiveButton("Next") { _, _ ->
                val oldPin = oldPinInput.text.toString().trim()
                if (AppLockManager.verifyPin(this, oldPin)) {
                    showNewPinDialog()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewPinDialog() {
        val newPinInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter new PIN"
            maxLines = 1
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Set New PIN")
            .setMessage("Enter a new 4-digit PIN")
            .setView(newPinInput)
            .setPositiveButton("Next") { _, _ ->
                val newPin = newPinInput.text.toString().trim()
                if (validatePin(newPin)) {
                    showConfirmPinDialog(newPin, isChanging = true)
                } else {
                    Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Failed Attempts")
            .setMessage("Clear failed attempts counter and remove lockout?")
            .setPositiveButton("Reset") { _, _ ->
                AppLockManager.resetFailedAttempts(this)
                Toast.makeText(this, "Failed attempts reset", Toast.LENGTH_SHORT).show()
                updateUI()
                Log.d(TAG, "Failed attempts reset")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePin(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
