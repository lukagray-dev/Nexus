package nexus.android.child.components.vibrateflash

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
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
 * Controller for managing vibration and flashlight patterns on the child device
 */
class VibrationFlashController(private val context: Context) {
    
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null
    private var flashJob: Job? = null
    
    init {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to get camera ID", e)
        }
    }
    
    // Vibration Patterns (timings in milliseconds)
    private val vibrationPatterns = mapOf(
        "short" to longArrayOf(0, 200),
        "double" to longArrayOf(0, 200, 200, 200),
        "triple" to longArrayOf(0, 200, 200, 200, 200, 200),
        "sos" to longArrayOf(0, 200, 200, 200, 200, 200, 400, 600, 400, 600, 400, 600, 400, 200, 200, 200, 200, 200),
        "heartbeat" to longArrayOf(0, 100, 100, 100, 400, 100, 100, 100),
        "wave" to longArrayOf(0, 100, 100, 200, 100, 300, 100, 400, 100, 300, 100, 200, 100, 100),
        "earthquake" to longArrayOf(0, 50, 50, 100, 50, 150, 50, 100, 50, 200, 50, 100, 50, 150, 50, 100, 50, 50)
    )
    
    /**
     * Execute vibration pattern
     */
    fun vibrate(pattern: String): Boolean {
        return try {
            val timings = vibrationPatterns[pattern] ?: vibrationPatterns["short"]!!
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
            
            Log.d(TAG, "✅ Vibration pattern executed: $pattern")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute vibration pattern: $pattern", e)
            false
        }
    }
    
    /**
     * Execute flash pattern
     */
    fun flash(pattern: String, scope: CoroutineScope): Boolean {
        if (cameraId == null) {
            Log.e(TAG, "❌ Camera not available for flash")
            return false
        }
        
        // Cancel any ongoing flash pattern
        flashJob?.cancel()
        
        flashJob = scope.launch(Dispatchers.IO) {
            try {
                when (pattern) {
                    "blink" -> {
                        flashOn()
                        delay(300)
                        flashOff()
                    }
                    "double_blink" -> {
                        repeat(2) {
                            flashOn()
                            delay(200)
                            flashOff()
                            delay(200)
                        }
                    }
                    "strobe" -> {
                        repeat(10) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(100)
                            flashOff()
                            delay(100)
                        }
                    }
                    "sos_flash" -> {
                        // S (3 short)
                        repeat(3) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(200)
                            flashOff()
                            delay(200)
                        }
                        delay(400)
                        // O (3 long)
                        repeat(3) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(600)
                            flashOff()
                            delay(400)
                        }
                        delay(400)
                        // S (3 short)
                        repeat(3) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(200)
                            flashOff()
                            delay(200)
                        }
                    }
                    "pulse" -> {
                        repeat(5) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(100)
                            flashOff()
                            delay(100)
                            flashOn()
                            delay(100)
                            flashOff()
                            delay(400)
                        }
                    }
                    "beacon" -> {
                        repeat(8) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(150)
                            flashOff()
                            delay(850)
                        }
                    }
                    "disco" -> {
                        repeat(20) {
                            if (!isActive) return@launch
                            flashOn()
                            delay(50)
                            flashOff()
                            delay(50)
                        }
                    }
                    else -> {
                        flashOn()
                        delay(300)
                        flashOff()
                    }
                }
                
                Log.d(TAG, "✅ Flash pattern executed: $pattern")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to execute flash pattern: $pattern", e)
            } finally {
                flashOff()
            }
        }
        
        return true
    }
    
    /**
     * Execute combined vibration and flash pattern
     */
    fun vibrateAndFlash(pattern: String, scope: CoroutineScope): Boolean {
        return try {
            when (pattern) {
                "alert" -> {
                    vibrate("double")
                    flash("double_blink", scope)
                }
                "attention" -> {
                    vibrate("triple")
                    flash("strobe", scope)
                }
                "emergency" -> {
                    vibrate("sos")
                    flash("sos_flash", scope)
                }
                else -> {
                    vibrate("short")
                    flash("blink", scope)
                }
            }
            
            Log.d(TAG, "✅ Combined pattern executed: $pattern")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute combined pattern: $pattern", e)
            false
        }
    }
    
    /**
     * Turn flash on
     */
    private fun flashOn() {
        try {
            cameraId?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(it, true)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn flash on", e)
        }
    }
    
    /**
     * Turn flash off
     */
    private fun flashOff() {
        try {
            cameraId?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(it, false)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn flash off", e)
        }
    }
    
    /**
     * Stop all ongoing patterns
     */
    fun stopAll() {
        vibrator.cancel()
        flashJob?.cancel()
        flashOff()
        Log.d(TAG, "Stopped all vibration and flash patterns")
    }
    
    companion object {
        private const val TAG = "VibrationFlashController"
    }
}
