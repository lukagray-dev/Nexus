package com.nexus.child.components.vibrateflash

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production controller managing vibration waveforms and camera flashlight hardware actuations.
 *
 * Hey friend! Here is how device actuation works under the hood:
 * 1. For vibration: On Android 12 (API 31) and newer, Google introduced VibratorManager to
 *    support modern multi-vibrator haptic engines. On older Android versions, we fallback to
 *    the classic Vibrator service. We create waveform timing arrays and dispatch them via
 *    VibrationEffect.createWaveform() with repeat = -1 (one-shot, non-looping).
 * 2. For flashlight: We use CameraManager.setTorchMode() which does not require opening an
 *    exclusive camera session (leaving camera streaming unblocked if available!).
 * 3. For strobe effects: We launch a lightweight coroutine on Dispatchers.Default that toggles
 *    the torch state with delay() intervals. We always cancel any previous active strobe job
 *    before starting a new one to prevent conflicting state loops.
 */
class VibrateFlashController(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IVibrateFlashController {

    companion object {
        private const val TAG = "VibrateFlashController"

        // Vibration Waveform Timings (in milliseconds: [delay, vibrate, sleep, vibrate...])
        private val VIBRATION_PATTERNS = mapOf(
            "short" to longArrayOf(0, 200),
            "double" to longArrayOf(0, 200, 150, 200),
            "pulse" to longArrayOf(0, 100, 100, 200, 100, 300, 100, 200, 100, 100),
            "sos" to longArrayOf(
                0,
                // S (...: 3 short pulses of 150ms with 150ms gap)
                150, 150, 150, 150, 150, 300,
                // O (---: 3 long pulses of 400ms with 150ms gap)
                400, 150, 400, 150, 400, 300,
                // S (...: 3 short pulses of 150ms with 150ms gap)
                150, 150, 150, 150, 150
            )
        )
    }

    // Resolve system vibrator service safely across Android SDK versions
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                @Suppress("DEPRECATION")
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve system vibrator service: ${e.message}", e)
            null
        }
    }

    private val cameraManager: CameraManager? by lazy {
        try {
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve CameraManager: ${e.message}", e)
            null
        }
    }

    private var flashCameraId: String? = null
    private var flashJob: Job? = null

    init {
        resolveFlashCameraId()
    }

    /**
     * Finds the primary camera ID that is equipped with a hardware flash unit.
     */
    private fun resolveFlashCameraId() {
        val manager = cameraManager ?: return
        try {
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Prefer the rear-facing camera for flashlight strobe
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    flashCameraId = id
                    Log.d(TAG, "Identified primary rear flash camera ID: $id")
                    return
                }
            }

            // Fallback: any camera with flash if no rear camera is flagged
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    flashCameraId = id
                    Log.d(TAG, "Fallback flash camera ID: $id")
                    return
                }
            }

            Log.w(TAG, "No hardware camera flash unit detected on this device.")
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating camera characteristics for flash: ${e.message}", e)
        }
    }

    override fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    override fun hasFlash(): Boolean {
        return flashCameraId != null
    }

    override fun vibrate(pattern: String): Boolean {
        val targetVibrator = vibrator
        if (targetVibrator == null || !targetVibrator.hasVibrator()) {
            Log.w(TAG, "Vibration requested but vibrator hardware is absent or unavailable.")
            return false
        }

        val timings = VIBRATION_PATTERNS[pattern.lowercase()] ?: VIBRATION_PATTERNS["short"]!!

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For a single short pulse, createOneShot gives the cleanest single actuation;
                // for patterns, createWaveform schedules the exact timing sequence.
                val effect = if (pattern.lowercase() == "short") {
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }

                // CRITICAL ANDROID HARDWARE INSIGHT:
                // If we don't supply explicit VibrationAttributes or AudioAttributes, Android
                // assigns USAGE_TOUCH or USAGE_UNKNOWN. When system touch haptics are toggled
                // off in device settings (extremely common on Xiaomi/MIUI, Samsung, and Pixel),
                // Android's VibratorManagerService silently discards the vibration with status
                // IGNORED_FOR_SETTINGS!
                // By explicitly setting USAGE_ALARM (with FLAG_AUDIBILITY_ENFORCED on AudioAttributes),
                // the vibration is prioritized as an alert alarm that activates the motor regardless
                // of touch feedback or silent mode settings.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attributes = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    targetVibrator.vibrate(effect, attributes)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attributes = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    if (vibratorManager != null) {
                        vibratorManager.vibrate(CombinedVibration.createParallel(effect), attributes)
                    } else {
                        val audioAttrs = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                        @Suppress("DEPRECATION")
                        targetVibrator.vibrate(effect, audioAttrs)
                    }
                } else {
                    val audioAttrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                    @Suppress("DEPRECATION")
                    targetVibrator.vibrate(effect, audioAttrs)
                }
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(timings, -1)
            }
            Log.d(TAG, "Successfully triggered vibration waveform '$pattern' with USAGE_ALARM")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute vibration waveform '$pattern': ${e.message}", e)
            false
        }
    }

    override fun flash(action: String): Boolean {
        val manager = cameraManager
        val camId = flashCameraId

        if (manager == null || camId == null) {
            Log.w(TAG, "Flash action requested but camera torch is unavailable.")
            return false
        }

        // Cancel any actively running strobe/beacon job before applying new state
        flashJob?.cancel()
        flashJob = null

        return when (action.lowercase()) {
            "on" -> setTorchState(camId, true)
            "off" -> setTorchState(camId, false)
            "strobe" -> {
                flashJob = coroutineScope.launch {
                    try {
                        // 4Hz strobe: 10 cycles of 100ms on, 150ms off
                        repeat(10) {
                            if (!isActive) return@launch
                            setTorchState(camId, true)
                            delay(100)
                            setTorchState(camId, false)
                            delay(150)
                        }
                    } finally {
                        setTorchState(camId, false)
                    }
                }
                true
            }
            "beacon" -> {
                flashJob = coroutineScope.launch {
                    try {
                        // Emergency Beacon: 6 cycles of 150ms flash followed by 850ms pause
                        repeat(6) {
                            if (!isActive) return@launch
                            setTorchState(camId, true)
                            delay(150)
                            setTorchState(camId, false)
                            delay(850)
                        }
                    } finally {
                        setTorchState(camId, false)
                    }
                }
                true
            }
            else -> {
                Log.w(TAG, "Unrecognized flashlight action: $action")
                false
            }
        }
    }

    override fun vibrateAndFlash(pattern: String): Boolean {
        return try {
            when (pattern.lowercase()) {
                "alert" -> {
                    val vibeOk = vibrate("double")
                    val flashOk = flash("strobe")
                    vibeOk || flashOk
                }
                "sos" -> {
                    val vibeOk = vibrate("sos")
                    val flashOk = flash("beacon")
                    vibeOk || flashOk
                }
                "pulse" -> {
                    val vibeOk = vibrate("pulse")
                    val flashOk = flash("strobe")
                    vibeOk || flashOk
                }
                else -> {
                    val vibeOk = vibrate("short")
                    val flashOk = flash("on")
                    vibeOk || flashOk
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing combined vibrate & flash pattern: ${e.message}", e)
            false
        }
    }

    private fun setTorchState(camId: String, enabled: Boolean): Boolean {
        return try {
            cameraManager?.setTorchMode(camId, enabled)
            true
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException setting torch mode to $enabled: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting torch mode to $enabled: ${e.message}", e)
            false
        }
    }

    override fun stopAll() {
        Log.d(TAG, "Stopping all active vibration and flashlight hardware actions...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.cancel()
            }
            vibrator?.cancel()
            flashJob?.cancel()
            flashJob = null
            flashCameraId?.let { camId ->
                setTorchState(camId, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration/flash: ${e.message}", e)
        }
    }

    override fun cleanup() {
        stopAll()
    }
}
