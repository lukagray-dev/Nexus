package nexus.android.parent.features.notifications

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
 * NotificationsFeature - Notification monitoring feature following desktop pattern
 */
class NotificationsFeature(context: Context) : BaseFeature(context) {

    private var searchInput: EditText? = null
    private var listContainer: LinearLayout? = null
    private var playPauseButton: FloatingActionButton? = null
    private var placeholder: LinearLayout? = null
    private var scrollView: ScrollView? = null
    
    private val notifications = mutableListOf<Notification>()
    private val filteredNotifications = mutableListOf<Notification>()
    
    private var isActive = false
    private var confirmationTimeout: Job? = null
    private var isRendering = false // Guard against infinite rendering
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // The panel layout is already inflated by PanelManager
        // Container is the panel root, find views from it
        searchInput = container.findViewById(R.id.notifications_search)
        listContainer = container.findViewById(R.id.notifications_list_container)
        playPauseButton = container.findViewById(R.id.notifications_play_pause_btn)
        placeholder = container.findViewById(R.id.notifications_placeholder)
        
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
            listenForNotifications()
        }
        
        // Re-render existing data when view is recreated
        filterNotifications(searchInput?.text.toString())
        updateButton(isActive)
        
        return container
    }

    private fun setupListeners() {
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNotifications(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        playPauseButton?.setOnClickListener {
            toggleMonitoring()
        }
    }

    private fun listenForNotifications() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "NOTIFICATION", "NOTIFICATION_BATCH" -> {
                            val notification = Notification.fromJson(json)
                            addNotification(notification)
                        }
                    }
                } catch (e: Exception) {
                    when (message) {
                        "NOTIFICATION_STARTED" -> handleConfirmation("NOTIFICATION_STARTED")
                        "NOTIFICATION_STOPPED" -> handleConfirmation("NOTIFICATION_STOPPED")
                    }
                }
            }
        }
    }

    private fun toggleMonitoring() {
        if (isActive) stopMonitoring() else startMonitoring()
    }

    private fun startMonitoring() {
        showLoading()
        ConnectionManager.sendCommand("NOTIFICATION_ON")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun stopMonitoring() {
        showLoading()
        ConnectionManager.sendCommand("NOTIFICATION_OFF")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun handleConfirmation(message: String) {
        confirmationTimeout?.cancel()
        when (message) {
            "NOTIFICATION_STARTED" -> {
                isActive = true
                updateButton(true)
                hideLoading()
            }
            "NOTIFICATION_STOPPED" -> {
                isActive = false
                updateButton(false)
                hideLoading()
            }
        }
    }

    private fun addNotification(notification: Notification) {
        notifications.add(0, notification)
        // Limit total notifications to 50
        if (notifications.size > 50) {
            notifications.removeAt(notifications.size - 1)
        }
        filterNotifications(searchInput?.text.toString())
    }

    private fun filterNotifications(query: String) {
        filteredNotifications.clear()
        if (query.isEmpty()) {
            filteredNotifications.addAll(notifications)
        } else {
            val lowerQuery = query.lowercase()
            filteredNotifications.addAll(notifications.filter {
                it.appName.lowercase().contains(lowerQuery) ||
                it.title.lowercase().contains(lowerQuery) ||
                it.text.lowercase().contains(lowerQuery)
            })
        }
        renderNotifications()
    }

    private fun renderNotifications() {
        if (isRendering) return
        isRendering = true
        
        // Clear existing notification items (but keep placeholder)
        val childCount = listContainer?.childCount ?: 0
        for (i in childCount - 1 downTo 0) {
            val child = listContainer?.getChildAt(i)
            if (child?.id != R.id.notifications_placeholder) {
                listContainer?.removeViewAt(i)
            }
        }
        
        if (filteredNotifications.isEmpty()) {
            placeholder?.visibility = View.VISIBLE
        } else {
            placeholder?.visibility = View.GONE
            // Only render first 20 items to prevent ANR
            val itemsToRender = filteredNotifications.take(20)
            itemsToRender.forEach { notif ->
                listContainer?.addView(createNotificationItem(notif), 0)
            }
        }
        
        isRendering = false
    }

    private fun createNotificationItem(notification: Notification): View {
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
            setImageResource(R.drawable.ic_notification)
            layoutParams = LinearLayout.LayoutParams(
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
        header.addView(icon)
        
        val appName = TextView(context).apply {
            text = notification.appName
            textSize = 13f
            setTextColor(context.getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(appName)
        
        val time = TextView(context).apply {
            text = notification.formatTime()
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        header.addView(time)
        
        item.addView(header)
        
        // Title
        val title = TextView(context).apply {
            text = notification.title
            textSize = 12f
            setPadding(0, 12, 0, 4)
            setTextColor(context.getColor(R.color.text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(title)
        
        // Text preview
        val text = TextView(context).apply {
            text = notification.text
            textSize = 12f
            setPadding(0, 0, 0, 0)
            setTextColor(context.getColor(R.color.text_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(text)
        
        // Click to show detail
        item.setOnClickListener {
            showDetailView(notification)
        }
        
        return item
    }

    private fun showDetailView(notification: Notification) {
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
            text = "Notification Details"
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
        
        // App Name field
        content.addView(createDetailField("App Name", notification.appName))
        
        // Date & Time field
        content.addView(createDetailField("Date & Time", notification.formatDateTime()))
        
        // Title section
        val titleSection = LinearLayout(context).apply {
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
        
        val titleLabel = TextView(context).apply {
            text = "TITLE"
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        titleSection.addView(titleLabel)
        
        val titleText = TextView(context).apply {
            text = notification.title
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(0, 24, 0, 0)
        }
        titleSection.addView(titleText)
        
        content.addView(titleSection)
        
        // Message section
        val messageSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF18181B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }
        
        val messageLabel = TextView(context).apply {
            text = "MESSAGE"
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        messageSection.addView(messageLabel)
        
        val messageText = TextView(context).apply {
            text = notification.text
            textSize = 14f
            setTextColor(context.getColor(R.color.text_primary))
            setPadding(0, 24, 0, 0)
        }
        messageSection.addView(messageText)
        
        content.addView(messageSection)
        
        scrollView.addView(content)
        detailView.addView(scrollView)
        
        // Add detail view to panel content
        panelContent.addView(detailView)
        
        // Fade in detail view
        detailView.animate().alpha(1f).setDuration(300).start()
        
        // Add floating badge on top of detail view with delay
        val badge = createFloatingBadge()
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

    private fun createFloatingBadge(): FrameLayout {
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
                setImageResource(R.drawable.ic_notification)
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
                text = "NOTIFICATION"
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
        updateButton(isActive)
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

    override fun getTitle(): String = context.getString(R.string.feature_notifications)
    override fun getDescription(): String = context.getString(R.string.feature_notifications_desc)

    data class Notification(
        val appName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    ) {
        companion object {
            fun fromJson(json: JSONObject): Notification {
                val body = json.optJSONObject("body")
                return Notification(
                    appName = body?.optString("appName", "Unknown App") ?: "Unknown App",
                    title = body?.optString("title", "") ?: "",
                    text = body?.optString("text", "") ?: "",
                    timestamp = json.optLong("ts", System.currentTimeMillis())
                )
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
