package com.nexus.child.components.applock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SelfieCapturer silently captures a still photograph using Android Camera2 API from the front camera.
 *
 * Hey friend! Here is how silent, invisible photo capture works:
 * 1. Front Camera Discovery: Iterates through CameraCharacteristics to identify the lens facing FRONT.
 * 2. Background Processing: Runs entirely on a dedicated HandlerThread ("NexusSelfieThread") so the
 *    main UI thread never stutters while the camera hardware powers up.
 * 3. Invisible ImageReader: Instead of attaching a SurfaceView or TextureView, we attach an ImageReader
 *    surface. This means zero preview is shown on screen — the child never sees a flash or camera box!
 * 4. Automatic Orientation: Front camera sensors are physically mounted sideways in phones. We read
 *    SENSOR_ORIENTATION and apply rotation and horizontal mirroring so the selfie looks natural.
 * 5. Robust Fallback: If camera permission is missing, or the camera is in use, or hardware takes
 *    longer than 2.5s, it times out gracefully and invokes the callback with null so PIN verification
 *    proceeds without a hitch.
 */
class SelfieCapturer(private val context: Context) {

    companion object {
        private const val TAG = "SelfieCapturer"
        private const val CAPTURE_WIDTH = 640
        private const val CAPTURE_HEIGHT = 480
        private const val JPEG_QUALITY = 65
        private const val CAPTURE_TIMEOUT_MS = 2500L
    }

    private val isCapturing = AtomicBoolean(false)

    /**
     * Attempts to take a silent front-facing photo and returns Base64 JPEG string via callback.
     *
     * @param onComplete Callback invoked with Base64 string if successful, or null on error/timeout.
     */
    fun captureSelfie(onComplete: (String?) -> Unit) {
        // 1. Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted; proceeding without selfie")
            onComplete(null)
            return
        }

        // 2. Prevent overlapping captures
        if (!isCapturing.compareAndSet(false, true)) {
            Log.w(TAG, "Selfie capture already in progress")
            onComplete(null)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)

        val finishWithResult = { result: String? ->
            if (completed.compareAndSet(false, true)) {
                isCapturing.set(false)
                mainHandler.post { onComplete(result) }
            }
        }

        // 3. Safety timeout fallback
        mainHandler.postDelayed({
            if (!completed.get()) {
                Log.w(TAG, "Selfie capture timed out after ${CAPTURE_TIMEOUT_MS}ms")
                finishWithResult(null)
            }
        }, CAPTURE_TIMEOUT_MS)

        // 4. Start background capture thread
        val thread = HandlerThread("NexusSelfieThread").apply { start() }
        val handler = Handler(thread.looper)

        handler.post {
            try {
                executeCapturePipeline(handler, thread, finishWithResult)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting capture pipeline: ${e.message}", e)
                cleanup(null, null, thread)
                finishWithResult(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun executeCapturePipeline(
        handler: Handler,
        thread: HandlerThread,
        finishWithResult: (String?) -> Unit
    ) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            Log.e(TAG, "CameraManager unavailable")
            cleanup(null, null, thread)
            finishWithResult(null)
            return
        }

        // Find front-facing camera ID and sensor orientation
        var frontCameraId: String? = null
        var sensorOrientation = 270

        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                frontCameraId = id
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                break
            }
        }

        if (frontCameraId == null) {
            Log.w(TAG, "No front camera found on this device")
            cleanup(null, null, thread)
            finishWithResult(null)
            return
        }

        var cameraDevice: CameraDevice? = null
        var imageReader: ImageReader? = null

        imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val base64 = processImage(image, sensorOrientation)
                image.close()
                cleanup(cameraDevice, imageReader, thread)
                finishWithResult(base64)
            } else {
                cleanup(cameraDevice, imageReader, thread)
                finishWithResult(null)
            }
        }, handler)

        cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                try {
                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureBuilder.addTarget(imageReader!!.surface)
                    captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), null, handler)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to dispatch capture request: ${e.message}")
                                    cleanup(camera, imageReader, thread)
                                    finishWithResult(null)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "CaptureSession configuration failed")
                                cleanup(camera, imageReader, thread)
                                finishWithResult(null)
                            }
                        },
                        handler
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring camera session: ${e.message}", e)
                    cleanup(camera, imageReader, thread)
                    finishWithResult(null)
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                cleanup(camera, imageReader, thread)
                finishWithResult(null)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera open error: $error")
                cleanup(camera, imageReader, thread)
                finishWithResult(null)
            }
        }, handler)
    }

    private fun processImage(image: Image, sensorOrientation: Int): String? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            // Apply rotation and horizontal mirror for front camera
            val matrix = Matrix().apply {
                postRotate(sensorOrientation.toFloat())
                postScale(-1f, 1f) // Mirror horizontally
            }

            val orientedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            val stream = ByteArrayOutputStream()
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            val jpegBytes = stream.toByteArray()

            Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to Base64: ${e.message}", e)
            null
        }
    }

    private fun cleanup(camera: CameraDevice?, reader: ImageReader?, thread: HandlerThread) {
        try {
            camera?.close()
            reader?.close()
            thread.quitSafely()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up camera resources: ${e.message}")
        }
    }
}
