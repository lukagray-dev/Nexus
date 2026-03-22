package nexus.android.parent.features.sms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject

/**
 * SmsFeature - SMS monitoring feature following desktop pattern
 */
class SmsFeature(context: Context) : BaseFeature(context) {

    private var searchInput: EditText? = null
    private var listContainer: LinearLayout? = null
    private var playPauseButton: FloatingActionButton? = null
    private var placeholder: LinearLayout? = null
    private var scrollView: ScrollView? = null
    
    private val messages = mutableListOf<SmsMessage>()
    private val filteredMessages = mutableListOf<SmsMessage>()
    
    private var isMonitoring = false
    private var latestTimestamp = 0L
    private var confirmationTimeout: Job? = null
    private var isRendering = false // Guard against infinite rendering
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // The panel layout is already inflated by PanelManager
        // Container is the panel root, find views from it
        searchInput = container.findViewById(R.id.sms_search)
        listContainer = container.findViewById(R.id.sms_list_container)
        playPauseButton = container.findViewById(R.id.sms_play_pause_btn)
        placeholder = container.findViewById(R.id.sms_placeholder)
        
        // Find the ScrollView and enable nested scrolling
        scrollView = listContainer?.parent as? ScrollView
        scrollView?.let { sv ->
            sv.isNestedScrollingEnabled = true
            // Request parent to not intercept touch events when scrolling
            sv.setOnTouchListener { v, event ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            listenForMessages()
        }
        
        // Re-render existing data when view is recreated
        filterMessages(searchInput?.text.toString())
        updateButton(isMonitoring)
        
        // Return the container itself since we're not creating new views
        return container
    }

    private fun setupListeners() {
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMessages(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        playPauseButton?.setOnClickListener {
            toggleMonitoring()
        }
    }

    private fun listenForMessages() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    // Try to parse as JSON
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "sms" -> {
                            // SMS message from child
                            val smsMessage = SmsMessage.fromJson(json)
                            addMessage(smsMessage)
                        }
                    }
                } catch (e: Exception) {
                    // Not JSON, might be confirmation message
                    when (message) {
                        "SMS_STARTED" -> {
                            handleConfirmation("SMS_STARTED")
                        }
                        "SMS_STOPPED" -> {
                            handleConfirmation("SMS_STOPPED")
                        }
                    }
                }
            }
        }
    }

    private fun toggleMonitoring() {
        if (isMonitoring) stopMonitoring() else startMonitoring()
    }

    private fun startMonitoring() {
        showLoading()
        val command = JSONObject().apply {
            put("cmd", "SMS_ON")
            put("since", latestTimestamp)
        }
        ConnectionManager.sendCommand(command.toString())
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun stopMonitoring() {
        showLoading()
        ConnectionManager.sendCommand("SMS_OFF")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun handleConfirmation(message: String) {
        confirmationTimeout?.cancel()
        when (message) {
            "SMS_STARTED" -> {
                isMonitoring = true
                updateButton(true)
                hideLoading()
            }
            "SMS_STOPPED" -> {
                isMonitoring = false
                updateButton(false)
                hideLoading()
            }
        }
    }

    private fun addMessage(message: SmsMessage) {
        messages.add(0, message)
        if (message.timestamp > latestTimestamp) {
            latestTimestamp = message.timestamp
        }
        filterMessages(searchInput?.text.toString())
    }

    private fun filterMessages(query: String) {
        filteredMessages.clear()
        if (query.isEmpty()) {
            filteredMessages.addAll(messages)
        } else {
            val lowerQuery = query.lowercase()
            filteredMessages.addAll(messages.filter {
                it.address.lowercase().contains(lowerQuery) ||
                it.body.lowercase().contains(lowerQuery)
            })
        }
        renderMessages()
    }

    private fun renderMessages() {
        if (isRendering) return
        isRendering = true
        
        // Limit to last 50 messages to prevent ANR
        val messagesToShow = filteredMessages.take(50)
        
        // Clear existing message items (but keep placeholder)
        val childCount = listContainer?.childCount ?: 0
        for (i in childCount - 1 downTo 0) {
            val child = listContainer?.getChildAt(i)
            if (child?.id != R.id.sms_placeholder) {
                listContainer?.removeViewAt(i)
            }
        }
        
        if (messagesToShow.isEmpty()) {
            placeholder?.visibility = View.VISIBLE
        } else {
            placeholder?.visibility = View.GONE
            // Add messages in batches to avoid ANR
            messagesToShow.forEachIndexed { index, msg ->
                if (index < 20) { // Only show first 20 immediately
                    listContainer?.addView(createMessageItem(msg), 0)
                }
            }
        }
        
        isRendering = false
    }

    private fun createMessageItem(message: SmsMessage): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        
        // Header row
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val icon = ImageView(context).apply {
            val iconRes = if (message.isReceived()) R.drawable.ic_message_received else R.drawable.ic_message_sent
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
        header.addView(icon)
        
        val address = TextView(context).apply {
            text = message.address
            textSize = 13f
            setTextColor(context.getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(address)
        
        val time = TextView(context).apply {
            text = message.formatTime()
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        header.addView(time)
        
        item.addView(header)
        
        // Message preview (first line only)
        val preview = TextView(context).apply {
            text = message.body
            textSize = 12f
            setPadding(0, 12, 0, 0)
            setTextColor(context.getColor(R.color.text_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(preview)
        
        // Click to show detail
        item.setOnClickListener {
            showDetailView(message)
        }
        
        return item
    }

    private fun showDetailView(message: SmsMessage) {
        // Find the panel_content FrameLayout
        val panelContent = scrollView?.parent as? FrameLayout ?: return
        
        // Hide list view and search with fade out
        scrollView?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            scrollView?.visibility = View.GONE
            scrollView?.alpha = 1f
        }
        searchInput?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            searchInput?.visibility = View.GONE
            searchInput?.alpha = 1f
        }
        playPauseButton?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            playPauseButton?.visibility = View.GONE
            playPauseButton?.alpha = 1f
        }
        
        // Create detail view
        val detailView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(context.getColor(android.R.color.black))
            id = View.generateViewId()
            alpha = 0f // Start invisible
        }
        
        // Back button header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 24, 32, 24)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF18181B.toInt())
        }
        
        val backBtn = TextView(context).apply {
            text = "← Back"
            textSize = 13f
            setTextColor(0xFFFBBF24.toInt())
            setPadding(24, 16, 24, 16)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        backBtn.setOnClickListener {
            // Fade out detail view and badge
            detailView.animate().alpha(0f).setDuration(200).withEndAction {
                panelContent.removeView(detailView)
                // Remove badge (last child)
                if (panelContent.childCount > 2) {
                    panelContent.removeViewAt(panelContent.childCount - 1)
                }
                // Fade in list view
                scrollView?.visibility = View.VISIBLE
                scrollView?.alpha = 0f
                scrollView?.animate()?.alpha(1f)?.setDuration(200)
                searchInput?.visibility = View.VISIBLE
                searchInput?.alpha = 0f
                searchInput?.animate()?.alpha(1f)?.setDuration(200)
                playPauseButton?.visibility = View.VISIBLE
                playPauseButton?.alpha = 0f
                playPauseButton?.animate()?.alpha(1f)?.setDuration(200)
            }
        }
        header.addView(backBtn)
        
        val title = TextView(context).apply {
            text = "Message Details"
            textSize = 13f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(24, 0, 0, 0)
        }
        header.addView(title)
        
        detailView.addView(header)
        
        // Scrollable content
        val detailScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 120) // Extra bottom padding for badge
        }
        
        // From/To field
        content.addView(createDetailField(
            if (message.isReceived()) "From" else "To",
            message.address
        ))
        
        // Date & Time field
        content.addView(createDetailField(
            "Date & Time",
            message.formatDateTime()
        ))
        
        // Message section
        val messageSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF18181B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 48
            }
        }
        
        val messageLabel = TextView(context).apply {
            text = "MESSAGE"
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        messageSection.addView(messageLabel)
        
        val messageText = TextView(context).apply {
            text = message.body
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(0, 24, 0, 0)
        }
        messageSection.addView(messageText)
        
        content.addView(messageSection)
        
        detailScrollView.addView(content)
        detailView.addView(detailScrollView)
        
        // Add detail view to panel content
        panelContent.addView(detailView)
        
        // Fade in detail view
        detailView.animate().alpha(1f).setDuration(300).start()
        
        // Add floating badge on top of detail view with delay
        val badge = createFloatingBadge(message.isReceived())
        badge.alpha = 0f
        panelContent.addView(badge)
        badge.animate().alpha(1f).setDuration(300).setStartDelay(150).start()
    }

    private fun createDetailField(label: String, value: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 48)
            
            val labelView = TextView(context).apply {
                text = label.uppercase()
                textSize = 11f
                setTextColor(context.getColor(R.color.text_secondary))
            }
            addView(labelView)
            
            val valueView = TextView(context).apply {
                text = value
                textSize = 14f
                setTextColor(context.getColor(R.color.text_primary))
                setPadding(0, 12, 0, 0)
            }
            addView(valueView)
        }
    }

    private fun createFloatingBadge(isReceived: Boolean): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 48, 48)
            }
            
            val badgeContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
            }
            
            // Icon with circular background
            val iconContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (56 * context.resources.displayMetrics.density).toInt(),
                    (56 * context.resources.displayMetrics.density).toInt()
                )
                setBackgroundResource(R.drawable.badge_circle)
                background.setTint(0xFFFBBF24.toInt())
                elevation = (8 * context.resources.displayMetrics.density)
            }
            
            val icon = ImageView(context).apply {
                val iconRes = if (isReceived) R.drawable.ic_message_received else R.drawable.ic_message_sent
                setImageResource(iconRes)
                layoutParams = FrameLayout.LayoutParams(
                    (28 * context.resources.displayMetrics.density).toInt(),
                    (28 * context.resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setColorFilter(0xFF000000.toInt())
            }
            iconContainer.addView(icon)
            badgeContainer.addView(iconContainer)
            
            val label = TextView(context).apply {
                text = if (isReceived) "RECEIVED" else "SENT"
                textSize = 11f
                setTextColor(context.getColor(R.color.text_secondary))
                setPadding(0, 12, 0, 0)
            }
            badgeContainer.addView(label)
            
            addView(badgeContainer)
        }
    }

    private fun showLoading() {
        playPauseButton?.setImageResource(R.drawable.loading_spinner)
        playPauseButton?.isEnabled = false
    }

    private fun hideLoading() {
        playPauseButton?.isEnabled = true
        updateButton(isMonitoring)
    }

    private fun updateButton(active: Boolean) {
        val iconRes = if (active) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        playPauseButton?.setImageResource(iconRes)
    }

    override fun onStop() {
        super.onStop()
        dataChannelJob?.cancel()
        confirmationTimeout?.cancel()
        scope.cancel()
    }

    override fun getTitle(): String = context.getString(R.string.feature_sms)
    override fun getDescription(): String = context.getString(R.string.feature_sms_desc)

    data class SmsMessage(
        val address: String,
        val body: String,
        val timestamp: Long,
        val smsType: String
    ) {
        companion object {
            fun fromJson(json: JSONObject): SmsMessage {
                return SmsMessage(
                    address = json.optString("address", "Unknown"),
                    body = json.optString("body", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    smsType = json.optString("sms_type", "inbox")
                )
            }
        }
        fun isReceived() = smsType == "inbox"
        
        fun formatTime(): String {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return format.format(date)
        }
        
        fun formatDateTime(): String {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
            return format.format(date)
        }
    }
}
