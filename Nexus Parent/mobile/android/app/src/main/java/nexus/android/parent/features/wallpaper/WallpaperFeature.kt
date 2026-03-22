package nexus.android.parent.features.wallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * WallpaperFeature - Remote wallpaper management
 * Allows parents to send and set wallpaper images on child device
 */
class WallpaperFeature(context: Context) : BaseFeature(context) {

    private var dropZone: FrameLayout? = null
    private var placeholder: LinearLayout? = null
    private var previewImg: ImageView? = null
    private var clearBtn: ImageView? = null
    private var setBtn: Button? = null
    private var statusText: TextView? = null
    private var scrollView: ScrollView? = null
    
    private var selectedImageUri: Uri? = null
    private var selectedImageBitmap: Bitmap? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusJob: Job? = null

    companion object {
        const val PICK_IMAGE_REQUEST = 1001
        const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
    }

    override fun createView(container: ViewGroup): View {
        // Container is the panel root, find views from it
        dropZone = container.findViewById(R.id.wallpaper_drop_zone)
        placeholder = container.findViewById(R.id.wallpaper_placeholder)
        previewImg = container.findViewById(R.id.wallpaper_preview_img)
        clearBtn = container.findViewById(R.id.clear_wallpaper_btn)
        setBtn = container.findViewById(R.id.set_wallpaper_btn)
        statusText = container.findViewById(R.id.wallpaper_status)
        
        // Find ScrollView and enable nested scrolling
        val panelContent = container.findViewById<ViewGroup>(R.id.panel_content)
        scrollView = panelContent?.getChildAt(0) as? ScrollView
        scrollView?.let { sv ->
            sv.isNestedScrollingEnabled = true
            // Request parent to not intercept touch events when scrolling
            sv.setOnTouchListener { v, event ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        
        setupListeners()
        
        return container
    }

    private fun setupListeners() {
        // Click drop zone to select image
        dropZone?.setOnClickListener {
            openImagePicker()
        }
        
        // Clear button
        clearBtn?.setOnClickListener {
            clearPreview()
        }
        
        // Set wallpaper button
        setBtn?.setOnClickListener {
            setWallpaper()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        
        // Try to start activity
        try {
            (context as? Activity)?.startActivityForResult(intent, PICK_IMAGE_REQUEST)
        } catch (e: Exception) {
            showStatus("Failed to open image picker", "error")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                showPreview(uri)
            }
        }
    }

    private fun showPreview(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                // Load bitmap
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        showStatus("Failed to load image", "error")
                    }
                    return@launch
                }
                
                // Check file size
                val byteArray = bitmapToByteArray(bitmap)
                if (byteArray.size > MAX_IMAGE_SIZE) {
                    withContext(Dispatchers.Main) {
                        showStatus("Image too large. Please select an image under 10MB", "error")
                    }
                    return@launch
                }
                
                selectedImageUri = uri
                selectedImageBitmap = bitmap
                
                withContext(Dispatchers.Main) {
                    // Show preview
                    previewImg?.setImageBitmap(bitmap)
                    previewImg?.visibility = View.VISIBLE
                    placeholder?.visibility = View.GONE
                    clearBtn?.visibility = View.VISIBLE
                    setBtn?.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("Failed to load image: ${e.message}", "error")
                }
            }
        }
    }

    private fun clearPreview() {
        selectedImageUri = null
        selectedImageBitmap = null
        
        previewImg?.setImageBitmap(null)
        previewImg?.visibility = View.GONE
        placeholder?.visibility = View.VISIBLE
        clearBtn?.visibility = View.GONE
        setBtn?.visibility = View.GONE
        statusText?.text = ""
    }

    private fun setWallpaper() {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            showStatus("Please select an image first", "warning")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    setBtn?.isEnabled = false
                    setBtn?.text = "Sending..."
                    showStatus("Preparing image...", "info")
                }
                
                // Convert bitmap to byte array
                val imageData = bitmapToByteArray(bitmap, 85) // 85% quality
                
                withContext(Dispatchers.Main) {
                    showStatus("Sending wallpaper...", "info")
                }
                
                // Send command header
                val command = JSONObject().apply {
                    put("cmd", "SET_WALLPAPER")
                    put("size", imageData.size)
                    put("filename", "wallpaper_${System.currentTimeMillis()}.jpg")
                }
                ConnectionManager.sendCommand(command.toString())
                
                // Send image data in chunks (16KB chunks)
                val chunkSize = 16384
                val totalChunks = (imageData.size + chunkSize - 1) / chunkSize
                
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, imageData.size)
                    val chunk = imageData.copyOfRange(start, end)
                    
                    // Send binary chunk
                    ConnectionManager.sendBinaryData(chunk)
                    
                    // Update progress
                    val progress = ((i + 1) * 100) / totalChunks
                    withContext(Dispatchers.Main) {
                        setBtn?.text = "Sending... $progress%"
                    }
                    
                    // Small delay to avoid overwhelming the connection
                    delay(10)
                }
                
                withContext(Dispatchers.Main) {
                    showStatus("Wallpaper sent! Waiting for confirmation...", "info")
                    setBtn?.text = "Waiting..."
                }
                
                // Listen for status (timeout after 30 seconds)
                listenForWallpaperStatus()
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("Failed to send wallpaper: ${e.message}", "error")
                    setBtn?.isEnabled = true
                    setBtn?.text = "Set Wallpaper"
                }
            }
        }
    }

    private fun listenForWallpaperStatus() {
        scope.launch {
            // Timeout after 30 seconds
            delay(30000)
            if (setBtn?.isEnabled == false) {
                setBtn?.isEnabled = true
                setBtn?.text = "Set Wallpaper"
                showStatus("Timeout waiting for response", "error")
            }
        }
        
        // Listen for wallpaper status messages from ConnectionManager
        scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                when (message) {
                    "WALLPAPER_RECEIVING" -> {
                        showStatus("Child is receiving wallpaper...", "info")
                    }
                    "WALLPAPER_SET_SUCCESS" -> {
                        showStatus("Wallpaper set successfully!", "success")
                        setBtn?.isEnabled = true
                        setBtn?.text = "Set Wallpaper"
                    }
                    "WALLPAPER_SET_FAILED" -> {
                        showStatus("Failed to set wallpaper on child device", "error")
                        setBtn?.isEnabled = true
                        setBtn?.text = "Set Wallpaper"
                    }
                    else -> {
                        if (message.startsWith("WALLPAPER_ERROR")) {
                            showStatus("Error: $message", "error")
                            setBtn?.isEnabled = true
                            setBtn?.text = "Set Wallpaper"
                        }
                    }
                }
            }
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun showStatus(message: String, type: String) {
        statusJob?.cancel()
        
        statusText?.apply {
            text = message
            setTextColor(when (type) {
                "success" -> Color.parseColor("#4ade80")
                "error" -> Color.parseColor("#ef4444")
                "warning" -> Color.parseColor("#fbbf24")
                "info" -> Color.parseColor("#60a5fa")
                else -> Color.parseColor("#60a5fa")
            })
        }
        
        // Clear status after 5 seconds for success/info
        if (type == "success" || type == "info") {
            statusJob = scope.launch {
                delay(5000)
                statusText?.text = ""
            }
        }
    }

    override fun onStop() {
        super.onStop()
        statusJob?.cancel()
        scope.cancel()
    }

    override fun getTitle(): String = context.getString(R.string.feature_wallpaper)
    override fun getDescription(): String = context.getString(R.string.feature_wallpaper_desc)
}
