package com.nexus.child.components.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Production implementation of IWallpaperController managing wallpaper retrieval,
 * live change monitoring, and setting wallpapers across Home, Lock, or Both screens.
 *
 * Hey friend! Here is how Android handles wallpapers:
 * 1. Android separates Home Screen (FLAG_SYSTEM) and Lock Screen (FLAG_LOCK).
 * 2. When a user applies a single wallpaper to the whole phone, Android does NOT store
 *    a separate lock screen file; getWallpaperFile(FLAG_LOCK) simply returns null.
 *    Our controller detects this condition and sets isSame = true so the parent desktop
 *    knows that both screens share the exact same image!
 * 3. We downscale snapshots to a max dimension of 1080p and compress with 80% JPEG quality,
 *    shrinking a 10MB wallpaper down to ~120KB without visible quality loss on the desktop HUD!
 * 4. For live monitoring: We use WallpaperManager.OnColorsChangedListener on Android 8.1+
 *    supplemented with a runtime BroadcastReceiver for ACTION_WALLPAPER_CHANGED.
 */
class WallpaperController(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IWallpaperController {

    companion object {
        private const val TAG = "WallpaperController"
        private const val MAX_DIMENSION_PX = 1080
        private const val JPEG_QUALITY = 80
    }

    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(context)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMonitoringActive = false
    private var changeCallback: ((WallpaperDataBundle) -> Unit)? = null

    // API 27+ Color Listener
    private var colorsChangedListener: Any? = null
    // Fallback broadcast receiver
    private var wallpaperChangeReceiver: BroadcastReceiver? = null

    override fun hasWallpaperPermission(): Boolean {
        // SET_WALLPAPER is a normal permission granted upon install.
        // We verify that WallpaperManager is responsive.
        return try {
            wallpaperManager.isWallpaperSupported
        } catch (e: Exception) {
            Log.e(TAG, "Failed verifying wallpaper support: ${e.message}", e)
            false
        }
    }

    override fun getActiveWallpapers(): WallpaperDataBundle {
        Log.d(TAG, "Retrieving current active wallpapers from WallpaperManager...")
        var homeBitmap: Bitmap? = null
        var lockBitmap: Bitmap? = null

        try {
            // Attempt retrieving via getWallpaperFile (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                homeBitmap = decodeWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                lockBitmap = decodeWallpaperFile(WallpaperManager.FLAG_LOCK)
            }

            // Fallback for home screen if getWallpaperFile returned null or on older OS
            if (homeBitmap == null) {
                homeBitmap = drawableToBitmap(wallpaperManager.drawable)
            }

            // If lockBitmap is null or identical, the device shares the home screen wallpaper on both
            val isDistinctLock = (lockBitmap != null) && !areBitmapsIdentical(homeBitmap, lockBitmap)
            val isSame = !isDistinctLock

            val homeBase64 = homeBitmap?.let { compressBitmapToBase64(it) }
            val lockBase64 = if (isDistinctLock) {
                compressBitmapToBase64(lockBitmap!!)
            } else {
                null
            }

            Log.d(TAG, "Wallpapers retrieved: home=${homeBase64 != null}, lock=${lockBase64 != null}, isSame=$isSame")
            return WallpaperDataBundle(
                homeBase64 = homeBase64,
                lockBase64 = lockBase64,
                isSame = isSame
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving active wallpapers: ${e.message}", e)
            return WallpaperDataBundle(homeBase64 = null, lockBase64 = null, isSame = true)
        } finally {
            homeBitmap?.recycle()
            lockBitmap?.recycle()
        }
    }

    private fun decodeWallpaperFile(which: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null

        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = wallpaperManager.getWallpaperFile(which)
            pfd?.let {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading wallpaper file for which=$which (using drawable fallback)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Exception reading wallpaper file for which=$which: ${e.message}")
            null
        } finally {
            try {
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 720
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1280

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun compressBitmapToBase64(srcBitmap: Bitmap): String {
        // Downscale keeping aspect ratio if larger than MAX_DIMENSION_PX
        val width = srcBitmap.width
        val height = srcBitmap.height
        val maxDim = maxOf(width, height)

        val targetBitmap = if (maxDim > MAX_DIMENSION_PX) {
            val scale = MAX_DIMENSION_PX.toFloat() / maxDim.toFloat()
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(srcBitmap, newWidth, newHeight, true)
        } else {
            srcBitmap
        }

        val outputStream = ByteArrayOutputStream()
        targetBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val byteArray = outputStream.toByteArray()

        if (targetBitmap != srcBitmap) {
            targetBitmap.recycle()
        }

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun areBitmapsIdentical(b1: Bitmap?, b2: Bitmap?): Boolean {
        if (b1 == null || b2 == null) return false
        if (b1.width != b2.width || b1.height != b2.height) return false

        // Compare sample hash or byte buffers for efficient equality check
        return try {
            val stream1 = ByteArrayOutputStream()
            val stream2 = ByteArrayOutputStream()
            b1.compress(Bitmap.CompressFormat.JPEG, 40, stream1)
            b2.compress(Bitmap.CompressFormat.JPEG, 40, stream2)
            val bytes1 = stream1.toByteArray()
            val bytes2 = stream2.toByteArray()

            if (bytes1.size != bytes2.size) return false

            val md = MessageDigest.getInstance("MD5")
            val hash1 = md.digest(bytes1)
            md.reset()
            val hash2 = md.digest(bytes2)
            MessageDigest.isEqual(hash1, hash2)
        } catch (e: Exception) {
            false
        }
    }

    override fun startWallpaperMonitoring(onWallpaperChanged: (WallpaperDataBundle) -> Unit): Boolean {
        if (isMonitoringActive) {
            Log.d(TAG, "Wallpaper monitoring is already active.")
            changeCallback = onWallpaperChanged
            return true
        }

        Log.d(TAG, "Starting live wallpaper change monitoring...")
        changeCallback = onWallpaperChanged
        isMonitoringActive = true

        // 1. Android 8.1+ OnColorsChangedListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
                    Log.d(TAG, "OnColorsChangedListener detected wallpaper change!")
                    dispatchWallpaperChange()
                }
                wallpaperManager.addOnColorsChangedListener(listener, mainHandler)
                colorsChangedListener = listener
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register OnColorsChangedListener: ${e.message}")
            }
        }

        // 2. Fallback runtime broadcast receiver for Intent.ACTION_WALLPAPER_CHANGED
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "ACTION_WALLPAPER_CHANGED broadcast received!")
                    dispatchWallpaperChange()
                }
            }
            @Suppress("DEPRECATION")
            val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
            context.registerReceiver(receiver, filter)
            wallpaperChangeReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering ACTION_WALLPAPER_CHANGED receiver: ${e.message}")
        }

        return true
    }

    private fun dispatchWallpaperChange() {
        if (!isMonitoringActive) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Short throttle delay to let Android finish writing the new wallpaper to storage
                kotlinx.coroutines.delay(500)
                val updated = getActiveWallpapers()
                mainHandler.post {
                    changeCallback?.invoke(updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed dispatching wallpaper change event: ${e.message}", e)
            }
        }
    }

    override fun stopWallpaperMonitoring(): Boolean {
        if (!isMonitoringActive) return false
        Log.d(TAG, "Stopping live wallpaper change monitoring...")
        isMonitoringActive = false
        changeCallback = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            (colorsChangedListener as? WallpaperManager.OnColorsChangedListener)?.let {
                try {
                    wallpaperManager.removeOnColorsChangedListener(it)
                } catch (ignored: Exception) {}
            }
            colorsChangedListener = null
        }

        wallpaperChangeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (ignored: Exception) {}
        }
        wallpaperChangeReceiver = null

        return true
    }

    override fun isMonitoring(): Boolean = isMonitoringActive

    override fun applyWallpaper(imageData: ByteArray, target: String): Boolean {
        Log.d(TAG, "Applying received wallpaper data (${imageData.size} bytes) for target: $target")
        var bitmap: Bitmap? = null
        return try {
            bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode byte array into Bitmap")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val whichFlag = when (target.lowercase()) {
                    "home" -> WallpaperManager.FLAG_SYSTEM
                    "lock" -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(bitmap, null, true, whichFlag)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            Log.d(TAG, "Successfully applied new wallpaper to $target screen.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper: ${e.message}", e)
            false
        } finally {
            bitmap?.recycle()
        }
    }

    override fun cleanup() {
        stopWallpaperMonitoring()
    }
}
