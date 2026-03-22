package nexus.android.parent.features.applock

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * AppLockFeature - App lock monitoring feature
 * Displays unlock attempt alerts with selfies and manages app lock settings
 */
class AppLockFeature(context: Context) : BaseFeature(context) {

    private var listContainer: LinearLayout? = null
    private var placeholder: LinearLayout? = null
    private var toggleBtn: Button? = null
    private var changePinBtn: Button? = null
    private var clearBtn: Button? = null
    private var statsText: TextView? = null
    private var scrollView: ScrollView? = null
    
    private val alerts = mutableListOf<AppLockAlert>()
    
    private var isEnabled = true
    private var isRendering = false
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // Container is the panel root, find views from it
        listContainer = container.findViewById(R.id.applock_list_container)
        placeholder = container.findViewById(R.id.applock_placeholder)
        toggleBtn = container.findViewById(R.id.applock_toggle_btn)
        changePinBtn = container.findViewById(R.id.applock_change_pin_btn)
        clearBtn = container.findViewById(R.id.applock_clear_btn)
        statsText = container.findViewById(R.id.applock_stats)
        
        // Find ScrollView and enable nested scrolling
        scrollView = container.findViewById(R.id.applock_scroll_view)
        scrollView?.let { sv ->
            sv.isNestedScrollingEnabled = true
            sv.setOnTouchListener { v, event ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            listenForAlerts()
        }
        
        // Re-render existing data
        renderAlerts()
        updateToggleButton()
        updateStats()
        
        return container
    }

    private fun setupListeners() {
        toggleBtn?.setOnClickListener {
            toggleLock()
        }
        
        changePinBtn?.setOnClickListener {
            showChangePinDialog()
        }
        
        clearBtn?.setOnClickListener {
            clearAll()
        }
    }

    private fun listenForAlerts() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "APPLOCK_ALERT" -> {
                            val alert = AppLockAlert.fromJson(json)
                            addAlert(alert)
                        }
                        "APP_LOCK_STATUS", "APPLOCK_STATUS", "APP_LOCK_ENABLED_SET" -> {
                            val enabled = json.optBoolean("enabled", true)
                            android.util.Log.d("AppLockFeature", "Received status update: enabled=$enabled")
                            isEnabled = enabled
                            updateToggleButton()
                            Toast.makeText(context, "App lock ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore non-JSON messages
                }
            }
        }
    }

    private fun addAlert(alert: AppLockAlert) {
        alerts.add(0, alert)
        // Limit to 500 alerts
        if (alerts.size > 500) {
            alerts.removeAt(alerts.size - 1)
        }
        renderAlerts()
        updateStats()
    }

    private fun renderAlerts() {
        if (isRendering) return
        isRendering = true
        
        listContainer?.removeAllViews()
        
        if (alerts.isEmpty()) {
            placeholder?.visibility = View.VISIBLE
        } else {
            placeholder?.visibility = View.GONE
            // Only render first 50 to prevent ANR
            val itemsToRender = alerts.take(50)
            itemsToRender.forEach { alert ->
                listContainer?.addView(createAlertItem(alert))
            }
        }
        
        isRendering = false
    }

    private fun createAlertItem(alert: AppLockAlert): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        
        // Selfie image (if available)
        if (alert.selfie != null) {
            val selfieImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (80 * context.resources.displayMetrics.density).toInt(),
                    (80 * context.resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (12 * context.resources.displayMetrics.density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(alert.getSelfiebitmap())
                setBackgroundColor(Color.parseColor("#27272a"))
                setPadding(2, 2, 2, 2)
            }
            item.addView(selfieImg)
            
            // Click to view full image
            selfieImg.setOnClickListener {
                showSelfieDialog(alert)
            }
        } else {
            // Placeholder icon if no selfie
            val placeholderIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (80 * context.resources.displayMetrics.density).toInt(),
                    (80 * context.resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (12 * context.resources.displayMetrics.density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(R.drawable.ic_applock)
                setBackgroundColor(Color.parseColor("#27272a"))
                alpha = 0.3f
                setPadding(20, 20, 20, 20)
            }
            item.addView(placeholderIcon)
        }
        
        // Content
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val statusText = TextView(context).apply {
            text = alert.getStatusText()
            textSize = 13f
            setTextColor(when {
                alert.isLockedOut -> Color.parseColor("#ef4444")
                alert.pinCorrect -> Color.parseColor("#4ade80")
                else -> Color.parseColor("#ef4444")
            })
        }
        content.addView(statusText)
        
        val timeText = TextView(context).apply {
            text = alert.formatTime()
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
            setPadding(0, 4, 0, 0)
        }
        content.addView(timeText)
        
        if (alert.failedAttempts > 0) {
            val attemptsText = TextView(context).apply {
                text = "Failed attempts: ${alert.failedAttempts}"
                textSize = 11f
                setTextColor(Color.parseColor("#fbbf24"))
                setPadding(0, 4, 0, 0)
            }
            content.addView(attemptsText)
        }
        
        item.addView(content)
        
        return item
    }

    private fun showSelfieDialog(alert: AppLockAlert) {
        val bitmap = alert.getSelfiebitmap() ?: return
        
        // Save current scroll position
        val scrollY = scrollView?.scrollY ?: 0
        
        // Hide main content
        listContainer?.visibility = View.GONE
        statsText?.visibility = View.GONE
        toggleBtn?.visibility = View.GONE
        changePinBtn?.visibility = View.GONE
        clearBtn?.visibility = View.GONE
        placeholder?.visibility = View.GONE
        
        // Create detail view matching desktop app
        val detailView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#000000"))
        }
        
        // Header with back button
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#18181B"))
        }
        
        val backBtn = Button(context).apply {
            text = "← Back"
            textSize = 13f
            setTextColor(Color.parseColor("#fbbf24"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 16, 0)
        }
        header.addView(backBtn)
        
        val titleText = TextView(context).apply {
            text = "Alert Details"
            textSize = 16f
            setTextColor(Color.parseColor("#e4e4e7"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(titleText)
        
        detailView.addView(header)
        
        // Selfie image container
        val imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * context.resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(16, 8, 16, 16)
            }
            setBackgroundColor(Color.parseColor("#27272a"))
        }
        
        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
        }
        imageContainer.addView(imageView)
        detailView.addView(imageContainer)
        
        // Info grid
        val infoGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 0, 16, 16)
        }
        
        // Status
        infoGrid.addView(createInfoItem("Status", alert.getStatusText(), when {
            alert.isLockedOut -> Color.parseColor("#fbbf24")
            alert.pinCorrect -> Color.parseColor("#4ade80")
            else -> Color.parseColor("#f87171")
        }))
        
        // Date & Time
        infoGrid.addView(createInfoItem("Date & Time", alert.formatTime(), Color.parseColor("#e4e4e7")))
        
        // Failed Attempts
        infoGrid.addView(createInfoItem("Failed Attempts", alert.failedAttempts.toString(), Color.parseColor("#e4e4e7")))
        
        // Device Status
        infoGrid.addView(createInfoItem("Device Status", if (alert.isLockedOut) "Locked Out" else "Active", Color.parseColor("#e4e4e7")))
        
        detailView.addView(infoGrid)
        
        // Add detail view to scroll view
        val scrollContent = scrollView?.getChildAt(0) as? LinearLayout
        scrollContent?.addView(detailView, 0)
        
        // Back button handler
        backBtn.setOnClickListener {
            scrollContent?.removeView(detailView)
            listContainer?.visibility = View.VISIBLE
            statsText?.visibility = View.VISIBLE
            toggleBtn?.visibility = View.VISIBLE
            changePinBtn?.visibility = View.VISIBLE
            clearBtn?.visibility = View.VISIBLE
            if (alerts.isEmpty()) {
                placeholder?.visibility = View.VISIBLE
            }
            // Restore scroll position
            scrollView?.post {
                scrollView?.scrollTo(0, scrollY)
            }
        }
    }
    
    private fun createInfoItem(label: String, value: String, valueColor: Int): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
            }
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.parseColor("#27272a"))
        }
        
        val labelText = TextView(context).apply {
            text = label.uppercase()
            textSize = 10f
            setTextColor(Color.parseColor("#71717a"))
        }
        item.addView(labelText)
        
        val valueText = TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(valueColor)
            setPadding(0, 6, 0, 0)
        }
        item.addView(valueText)
        
        return item
    }

    private fun toggleLock() {
        val newState = !isEnabled
        val command = JSONObject().apply {
            put("cmd", "SET_APP_LOCK_ENABLED")
            put("enabled", newState)
        }
        ConnectionManager.sendCommand(command.toString())
        
        // Don't update UI optimistically - wait for confirmation from child
        Toast.makeText(context, "Sending command to ${if (newState) "enable" else "disable"} app lock...", Toast.LENGTH_SHORT).show()
    }

    private fun showChangePinDialog() {
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#18181B"))
        }
        
        val descText = TextView(context).apply {
            text = "Enter a new 4-digit PIN for the child's app lock."
            textSize = 12f
            setTextColor(Color.parseColor("#a1a1aa"))
            setPadding(0, 0, 0, 16)
        }
        dialogView.addView(descText)
        
        val newPinInput = EditText(context).apply {
            hint = "New PIN"
            setHintTextColor(Color.parseColor("#52525b"))
            setTextColor(Color.parseColor("#e4e4e7"))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.parseColor("#424242"))
        }
        dialogView.addView(newPinInput)
        
        val confirmPinInput = EditText(context).apply {
            hint = "Confirm PIN"
            setHintTextColor(Color.parseColor("#52525b"))
            setTextColor(Color.parseColor("#e4e4e7"))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.parseColor("#424242"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
        dialogView.addView(confirmPinInput)
        
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .setTitle("Change PIN")
            .setView(dialogView)
            .setPositiveButton("Change PIN") { _, _ ->
                val newPin = newPinInput.text.toString()
                val confirmPin = confirmPinInput.text.toString()
                
                when {
                    newPin.length != 4 -> {
                        Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                    }
                    newPin != confirmPin -> {
                        Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val command = JSONObject().apply {
                            put("cmd", "SET_APP_LOCK_PIN")
                            put("pin", newPin)
                        }
                        ConnectionManager.sendCommand(command.toString())
                        Toast.makeText(context, "PIN change command sent", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Style the dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        
        // Style dialog buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.parseColor("#fbbf24"))
            setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.parseColor("#a1a1aa"))
            setBackgroundColor(Color.TRANSPARENT)
        }
        
        // Style title
        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.apply {
            setTextColor(Color.parseColor("#e4e4e7"))
            setBackgroundColor(Color.parseColor("#18181B"))
        }
    }

    private fun clearAll() {
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#18181B"))
        }
        
        val messageText = TextView(context).apply {
            text = "Clear all app lock alerts? This cannot be undone."
            textSize = 14f
            setTextColor(Color.parseColor("#e4e4e7"))
        }
        dialogView.addView(messageText)
        
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .setTitle("Clear All Alerts")
            .setView(dialogView)
            .setPositiveButton("Clear") { _, _ ->
                alerts.clear()
                renderAlerts()
                updateStats()
                Toast.makeText(context, "All alerts cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Style the dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        
        // Style dialog buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.parseColor("#ef4444"))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.parseColor("#a1a1aa"))
        }
        
        // Style title
        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.apply {
            setTextColor(Color.parseColor("#e4e4e7"))
            setBackgroundColor(Color.parseColor("#18181B"))
        }
    }

    private fun updateToggleButton() {
        toggleBtn?.apply {
            android.util.Log.d("AppLockFeature", "Updating button: isEnabled=$isEnabled")
            if (isEnabled) {
                text = "Enabled"
                setBackgroundColor(Color.parseColor("#4ade80"))
                setTextColor(Color.parseColor("#000000"))
            } else {
                text = "Disabled"
                setBackgroundColor(Color.parseColor("#3f3f46"))
                setTextColor(Color.parseColor("#a1a1aa"))
            }
        }
    }

    private fun updateStats() {
        val total = alerts.size
        val failed = alerts.count { !it.pinCorrect }
        val lockedOut = alerts.count { it.isLockedOut }
        
        statsText?.text = "Total: $total | Failed: $failed | Locked Out: $lockedOut"
    }

    override fun onStop() {
        super.onStop()
        dataChannelJob?.cancel()
        scope.cancel()
    }

    override fun getTitle(): String = context.getString(R.string.feature_applock)
    override fun getDescription(): String = context.getString(R.string.feature_applock_desc)

    data class AppLockAlert(
        val id: String,
        val timestamp: Long,
        val pinCorrect: Boolean,
        val failedAttempts: Int,
        val isLockedOut: Boolean,
        val selfie: String? = null
    ) {
        companion object {
            fun fromJson(json: JSONObject): AppLockAlert {
                return AppLockAlert(
                    id = json.optString("id", "alert-${System.currentTimeMillis()}"),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    pinCorrect = json.optBoolean("pinCorrect", false),
                    failedAttempts = json.optInt("failedAttempts", 0),
                    isLockedOut = json.optBoolean("isLockedOut", false),
                    selfie = json.optString("selfie", null)
                )
            }
        }
        
        fun getStatusText(): String {
            return when {
                isLockedOut -> "Device Locked Out"
                pinCorrect -> "Unlock Successful"
                else -> "Unlock Failed"
            }
        }
        
        fun formatTime(): String {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            return format.format(date)
        }
        
        fun getSelfiebitmap(): Bitmap? {
            return try {
                if (selfie.isNullOrEmpty()) return null
                val imageBytes = Base64.decode(selfie, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }
}
