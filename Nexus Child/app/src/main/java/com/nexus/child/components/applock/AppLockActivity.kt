package com.nexus.child.components.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nexus.child.MainActivity
import com.nexus.child.R
import com.nexus.child.databinding.ActivityAppLockBinding

/**
 * AppLockActivity acts as the tactical security gatekeeper for Nexus Child.
 *
 * Hey friend! Here is how this lock screen protects the device:
 * 1. Entry Interception: If App Lock is enabled in AppLockManager, no user can view MainActivity
 *    without passing this screen.
 * 2. 6-Digit PIN Evaluation: The numeric keypad feeds into a 6-digit buffer with visual dot indicators.
 * 3. Invisible Intruder Selfie: Upon entering the 6th digit, the front camera silently captures a frame
 *    using Camera2 API in the background. Zero camera preview or shutter sound is triggered!
 * 4. 3-Attempt Lockout: If 3 wrong PINs are entered, input is locked down for 60 seconds. A real-time
 *    countdown informs the intruder of the lockout.
 * 5. Telemetry Reporting: Every attempt (valid or invalid) is logged locally and streamed live
 *    to the parent dashboard if WebRTC is connected.
 * 6. Back Button Hardening: Pressing Back minimizes the app to the home screen rather than bypassing the lock.
 */
class AppLockActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppLockActivity"
        const val EXTRA_SKIP_LOCK_CHECK = "skip_app_lock_check"

        /**
         * Helper to start the lock screen with proper activity task flags.
         */
        fun start(context: Context) {
            val intent = Intent(context, AppLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityAppLockBinding
    private val enteredPin = StringBuilder()
    private val pinDots = mutableListOf<ImageView>()
    private var lockoutTimer: CountDownTimer? = null
    private lateinit var selfieCapturer: SelfieCapturer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selfieCapturer = SelfieCapturer(this)

        initPinDots()
        setupKeypad()
        checkLockoutState()
    }

    override fun onResume() {
        super.onResume()
        // If parent remotely disabled app lock while this activity was showing, dismiss immediately!
        if (!AppLockManager.isAppLockEnabled(this)) {
            Log.d(TAG, "App lock was disabled remotely; dismissing lock screen")
            AppLockManager.setSessionUnlocked(true)
            launchMainActivity()
            return
        }
        checkLockoutState()
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent hardware back button from bypassing security; minimize app to home screen instead
        moveTaskToBack(true)
    }

    private fun initPinDots() {
        pinDots.clear()
        pinDots.add(binding.dot1)
        pinDots.add(binding.dot2)
        pinDots.add(binding.dot3)
        pinDots.add(binding.dot4)
        pinDots.add(binding.dot5)
        pinDots.add(binding.dot6)
        updatePinDots()
    }

    private fun setupKeypad() {
        // Number buttons 0-9
        val numberButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )

        numberButtons.forEach { (view, digit) ->
            view.setOnClickListener {
                onDigitPressed(digit)
            }
        }

        // Backspace
        binding.btnKeyBackspace.setOnClickListener {
            onBackspacePressed()
        }

        // Clear
        binding.btnKeyClear.setOnClickListener {
            onClearPressed()
        }
    }

    private fun onDigitPressed(digit: String) {
        if (AppLockManager.isLockedOut(this)) {
            shakeView(binding.pinDotsContainer)
            return
        }

        if (enteredPin.length < AppLockConstants.PIN_LENGTH) {
            enteredPin.append(digit)
            updatePinDots()

            if (enteredPin.length == AppLockConstants.PIN_LENGTH) {
                processPinSubmission()
            }
        }
    }

    private fun onBackspacePressed() {
        if (AppLockManager.isLockedOut(this)) return

        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updatePinDots()
        }
    }

    private fun onClearPressed() {
        if (AppLockManager.isLockedOut(this)) return

        enteredPin.clear()
        updatePinDots()
    }

    private fun updatePinDots() {
        for (i in 0 until AppLockConstants.PIN_LENGTH) {
            val isFilled = i < enteredPin.length
            pinDots[i].setImageResource(
                if (isFilled) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun processPinSubmission() {
        val pinCandidate = enteredPin.toString()

        // Disable input while taking selfie and verifying
        binding.keypadGrid.isEnabled = false

        // Silently capture front-facing photograph
        selfieCapturer.captureSelfie { selfieBase64 ->
            val isCorrect = AppLockManager.verifyPin(this, pinCandidate)

            if (isCorrect) {
                handleSuccessfulUnlock(selfieBase64)
            } else {
                handleFailedUnlock(selfieBase64)
            }
        }
    }

    private fun handleSuccessfulUnlock(selfieBase64: String?) {
        Log.d(TAG, "PIN verified successfully. Unlocking app session...")
        AppLockManager.recordSuccessfulUnlock(this)

        val record = AppLockRecord(
            pinCorrect = true,
            failedAttempts = 0,
            isLockedOut = false,
            selfieBase64 = selfieBase64
        )
        AppLockController.notifyAttempt(this, record)

        launchMainActivity()
    }

    private fun handleFailedUnlock(selfieBase64: String?) {
        val failedCount = AppLockManager.recordFailedAttempt(this)
        val isLocked = AppLockManager.isLockedOut(this)

        Log.w(TAG, "PIN verification failed (attempt $failedCount / ${AppLockConstants.MAX_FAILED_ATTEMPTS})")

        val record = AppLockRecord(
            pinCorrect = false,
            failedAttempts = failedCount,
            isLockedOut = isLocked,
            selfieBase64 = selfieBase64
        )
        AppLockController.notifyAttempt(this, record)

        // Visual shake animation
        shakeView(binding.pinDotsContainer)

        // Clear input buffer
        enteredPin.clear()
        updatePinDots()
        binding.keypadGrid.isEnabled = true

        if (isLocked) {
            checkLockoutState()
        } else {
            val remaining = AppLockConstants.MAX_FAILED_ATTEMPTS - failedCount
            binding.textLockoutWarning.text = getString(R.string.app_lock_wrong_pin, remaining)
            binding.textLockoutWarning.visibility = View.VISIBLE
        }
    }

    private fun checkLockoutState() {
        if (AppLockManager.isLockedOut(this)) {
            val remainingSec = AppLockManager.getRemainingLockoutSeconds(this)
            binding.keypadGrid.alpha = 0.3f
            binding.keypadGrid.isEnabled = false
            binding.textLockoutWarning.visibility = View.VISIBLE

            lockoutTimer?.cancel()
            lockoutTimer = object : CountDownTimer(remainingSec * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val sec = (millisUntilFinished + 999) / 1000
                    binding.textLockoutWarning.text = getString(R.string.app_lock_locked_out_timer, sec)
                }

                override fun onFinish() {
                    binding.keypadGrid.alpha = 1.0f
                    binding.keypadGrid.isEnabled = true
                    binding.textLockoutWarning.visibility = View.GONE
                }
            }.start()
        } else {
            binding.keypadGrid.alpha = 1.0f
            binding.keypadGrid.isEnabled = true
            binding.textLockoutWarning.visibility = View.GONE
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SKIP_LOCK_CHECK, true)
        }
        startActivity(intent)
        finish()
    }

    private fun shakeView(view: View) {
        val animation = AnimationUtils.loadAnimation(this, R.anim.shake)
        view.startAnimation(animation)
    }
}
