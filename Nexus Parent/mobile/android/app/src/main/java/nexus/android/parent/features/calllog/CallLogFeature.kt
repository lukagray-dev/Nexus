package nexus.android.parent.features.calllog

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
 * CallLogFeature - Call log monitoring feature following desktop pattern
 */
class CallLogFeature(context: Context) : BaseFeature(context) {

    private var searchInput: EditText? = null
    private var listContainer: LinearLayout? = null
    private var playPauseButton: FloatingActionButton? = null
    private var placeholder: LinearLayout? = null
    private var scrollView: ScrollView? = null
    
    private val logs = mutableListOf<CallLog>()
    private val filteredLogs = mutableListOf<CallLog>()
    
    private var isMonitoring = false
    private var latestTimestamp = 0L
    private var confirmationTimeout: Job? = null
    private var isRendering = false // Guard against infinite rendering
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // The panel layout is already inflated by PanelManager
        // Container is the panel root, find views from it
        searchInput = container.findViewById(R.id.calllog_search)
        listContainer = container.findViewById(R.id.calllog_list_container)
        playPauseButton = container.findViewById(R.id.calllog_play_pause_btn)
        placeholder = container.findViewById(R.id.calllog_placeholder)
        
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
            listenForLogs()
        }
        
        // Re-render existing data when view is recreated
        filterLogs(searchInput?.text.toString())
        updateButton(isMonitoring)
        
        return container
    }

    private fun setupListeners() {
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLogs(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        playPauseButton?.setOnClickListener {
            toggleMonitoring()
        }
    }

    private fun listenForLogs() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "calllog" -> {
                            val callLog = CallLog.fromJson(json)
                            addLog(callLog)
                        }
                    }
                } catch (e: Exception) {
                    when (message) {
                        "CALLLOG_STARTED" -> handleConfirmation("CALLLOG_STARTED")
                        "CALLLOG_STOPPED" -> handleConfirmation("CALLLOG_STOPPED")
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
            put("cmd", "CALLLOG_ON")
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
        ConnectionManager.sendCommand("CALLLOG_OFF")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun handleConfirmation(message: String) {
        confirmationTimeout?.cancel()
        when (message) {
            "CALLLOG_STARTED" -> {
                isMonitoring = true
                updateButton(true)
                hideLoading()
            }
            "CALLLOG_STOPPED" -> {
                isMonitoring = false
                updateButton(false)
                hideLoading()
            }
        }
    }

    private fun addLog(log: CallLog) {
        logs.add(0, log)
        if (log.timestamp > latestTimestamp) {
            latestTimestamp = log.timestamp
        }
        // Limit total logs to 50
        if (logs.size > 50) {
            logs.removeAt(logs.size - 1)
        }
        filterLogs(searchInput?.text.toString())
    }

    private fun filterLogs(query: String) {
        filteredLogs.clear()
        if (query.isEmpty()) {
            filteredLogs.addAll(logs)
        } else {
            val lowerQuery = query.lowercase()
            filteredLogs.addAll(logs.filter {
                it.number.lowercase().contains(lowerQuery) ||
                (it.name?.lowercase()?.contains(lowerQuery) == true)
            })
        }
        renderLogs()
    }

    private fun renderLogs() {
        if (isRendering) return
        isRendering = true
        
        // Clear existing log items (but keep placeholder)
        val childCount = listContainer?.childCount ?: 0
        for (i in childCount - 1 downTo 0) {
            val child = listContainer?.getChildAt(i)
            if (child?.id != R.id.calllog_placeholder) {
                listContainer?.removeViewAt(i)
            }
        }
        
        if (filteredLogs.isEmpty()) {
            placeholder?.visibility = View.VISIBLE
        } else {
            placeholder?.visibility = View.GONE
            // Only render first 20 items to prevent ANR
            val itemsToRender = filteredLogs.take(20)
            itemsToRender.forEach { log ->
                listContainer?.addView(createLogItem(log), 0)
            }
        }
        
        isRendering = false
    }

    private fun createLogItem(log: CallLog): View {
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
            val iconRes = when (log.callType) {
                "incoming" -> R.drawable.ic_call_incoming
                "outgoing" -> R.drawable.ic_call_outgoing
                "missed" -> R.drawable.ic_call_missed
                else -> R.drawable.ic_call_incoming
            }
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
        header.addView(icon)
        
        val number = TextView(context).apply {
            text = log.name ?: log.number
            textSize = 13f
            setTextColor(context.getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(number)
        
        val time = TextView(context).apply {
            text = log.formatTime()
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        header.addView(time)
        
        item.addView(header)
        
        // Details preview
        val details = TextView(context).apply {
            text = "${log.getCallTypeLabel()} • ${log.formatDuration()}"
            textSize = 12f
            setPadding(0, 12, 0, 0)
            setTextColor(context.getColor(R.color.text_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(details)
        
        // Click to show detail
        item.setOnClickListener {
            showDetailView(log)
        }
        
        return item
    }

    private fun showDetailView(log: CallLog) {
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
            text = "Call Details"
            textSize = 13f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(24, 0, 0, 0)
        }
        header.addView(title)
        
        detailView.addView(header)
        
        // Scrollable content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 120)
        }
        
        // Contact/Number field
        content.addView(createDetailField(
            "Contact",
            log.name ?: log.number
        ))
        
        // Phone Number field (if name exists)
        if (log.name != null) {
            content.addView(createDetailField(
                "Phone Number",
                log.number
            ))
        }
        
        // Call Type field
        content.addView(createDetailField(
            "Call Type",
            log.getCallTypeLabel()
        ))
        
        // Duration field
        content.addView(createDetailField(
            "Duration",
            log.formatDuration()
        ))
        
        // Date & Time field
        content.addView(createDetailField(
            "Date & Time",
            log.formatDateTime()
        ))
        
        scrollView.addView(content)
        detailView.addView(scrollView)
        
        // Add detail view to panel content
        panelContent.addView(detailView)
        
        // Fade in detail view
        detailView.animate().alpha(1f).setDuration(300).start()
        
        // Add floating badge on top of detail view with delay
        val badge = createFloatingBadge(log.callType)
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

    private fun createFloatingBadge(callType: String): FrameLayout {
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
                val iconRes = when (callType) {
                    "incoming" -> R.drawable.ic_call_incoming
                    "outgoing" -> R.drawable.ic_call_outgoing
                    "missed" -> R.drawable.ic_call_missed
                    else -> R.drawable.ic_call_incoming
                }
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
                text = when (callType) {
                    "incoming" -> "INCOMING"
                    "outgoing" -> "OUTGOING"
                    "missed" -> "MISSED"
                    else -> callType.uppercase()
                }
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

    override fun getTitle(): String = context.getString(R.string.feature_calllog)
    override fun getDescription(): String = context.getString(R.string.feature_calllog_desc)

    data class CallLog(
        val number: String,
        val name: String?,
        val timestamp: Long,
        val duration: Int,
        val callType: String
    ) {
        companion object {
            fun fromJson(json: JSONObject): CallLog {
                return CallLog(
                    number = json.optString("number", "Unknown"),
                    name = json.optString("name"),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    duration = json.optInt("duration", 0),
                    callType = json.optString("call_type", "incoming")
                )
            }
        }
        
        fun getCallTypeLabel(): String {
            return when (callType) {
                "incoming" -> "Incoming"
                "outgoing" -> "Outgoing"
                "missed" -> "Missed"
                else -> callType
            }
        }
        
        fun formatDuration(): String {
            return if (duration < 60) {
                "${duration}s"
            } else {
                val minutes = duration / 60
                val seconds = duration % 60
                "${minutes}m ${seconds}s"
            }
        }
        
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
