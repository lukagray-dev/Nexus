package nexus.android.child.components.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Controller for managing wallpaper changes on the child device
 */
class WallpaperController(private val context: Context) {
    
    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)
    private var receivingWallpaper = false
    private var wallpaperBuffer = ByteArrayOutputStream()
    private var expectedSize = 0L
    private var receivedSize = 0L
    private var wallpaperFilename = ""
    
    /**
     * Start receiving wallpaper data
     * @param size Expected size of the wallpaper image in bytes
     * @param filename Name of the wallpaper file
     */
    fun startReceivingWallpaper(size: Long, filename: String) {
        Log.d(TAG, "Starting to receive wallpaper: $filename, size: $size bytes")
        receivingWallpaper = true
        expectedSize = size
        receivedSize = 0
        wallpaperFilename = filename
        wallpaperBuffer.reset()
    }
    
    /**
     * Receive a chunk of wallpaper data
     * @param chunk Byte array containing part of the wallpaper image
     * @return true if wallpaper is complete and ready to be set
     */
    fun receiveWallpaperChunk(chunk: ByteArray): Boolean {
        if (!receivingWallpaper) {
            Log.w(TAG, "Received wallpaper chunk but not in receiving mode")
            return false
        }
        
        try {
            wallpaperBuffer.write(chunk)
            receivedSize += chunk.size
            
            val progress = (receivedSize.toFloat() / expectedSize * 100).toInt()
            Log.d(TAG, "Received wallpaper chunk: $receivedSize / $expectedSize bytes ($progress%)")
            
            // Check if we've received all data
            if (receivedSize >= expectedSize) {
                Log.d(TAG, "Wallpaper data complete, ready to set")
                receivingWallpaper = false
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving wallpaper chunk", e)
            cancelWallpaperReceive()
        }
        
        return false
    }
    
    /**
     * Set the received wallpaper as the device's home screen wallpaper
     * @return true if wallpaper was set successfully
     */
    fun setWallpaper(): Boolean {
        try {
            val imageData = wallpaperBuffer.toByteArray()
            
            if (imageData.isEmpty()) {
                Log.e(TAG, "No wallpaper data to set")
                return false
            }
            
            Log.d(TAG, "Setting wallpaper from ${imageData.size} bytes")
            
            // Decode bitmap from byte array
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode wallpaper image")
                return false
            }
            
            // Set as wallpaper
            wallpaperManager.setBitmap(bitmap)
            
            Log.d(TAG, "✅ Wallpaper set successfully")
            
            // Clean up
            bitmap.recycle()
            wallpaperBuffer.reset()
            
            return true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to set wallpaper", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper", e)
            return false
        }
    }
    
    /**
     * Cancel wallpaper receive operation
     */
    fun cancelWallpaperReceive() {
        Log.d(TAG, "Cancelling wallpaper receive")
        receivingWallpaper = false
        wallpaperBuffer.reset()
        expectedSize = 0
        receivedSize = 0
        wallpaperFilename = ""
    }
    
    /**
     * Check if currently receiving wallpaper data
     */
    fun isReceivingWallpaper(): Boolean = receivingWallpaper
    
    /**
     * Get current receive progress (0-100)
     */
    fun getReceiveProgress(): Int {
        return if (expectedSize > 0) {
            (receivedSize.toFloat() / expectedSize * 100).toInt()
        } else {
            0
        }
    }
    
    companion object {
        private const val TAG = "WallpaperController"
    }
}
