package nexus.android.parent.features.chats

import android.content.Context
import android.graphics.Color
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
 * ChatsFeature - Chat message monitoring feature following desktop pattern
 */
class ChatsFeature(context: Context) : BaseFeature(context) {

    private var searchInput: EditText? = null
    private var listContainer: LinearLayout? = null
    private var playPauseButton: FloatingActionButton? = null
    private var placeholder: LinearLayout? = null
    private var scrollView: ScrollView? = null
    
    private val chats = mutableListOf<ChatMessage>()
    private val filteredChats = mutableListOf<ChatMessage>()
    
    private var isActive = false
    private var confirmationTimeout: Job? = null
    private var isRendering = false // Guard against infinite rendering
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // The panel layout is already inflated by PanelManager
        // Container is the panel root, find views from it
        searchInput = container.findViewById(R.id.chats_search)
        listContainer = container.findViewById(R.id.chats_list_container)
        playPauseButton = container.findViewById(R.id.chats_play_pause_btn)
        placeholder = container.findViewById(R.id.chats_placeholder)
        
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
            listenForChats()
        }
        
        // Re-render existing data when view is recreated
        filterChats(searchInput?.text.toString())
        updateButton(isActive)
        
        return container
    }

    private fun setupListeners() {
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChats(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        playPauseButton?.setOnClickListener {
            toggleMonitoring()
        }
    }

    private fun listenForChats() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "MESSAGE", "TYPING", "APP_FOREGROUND" -> {
                            val chat = ChatMessage.fromJson(json)
                            addChat(chat)
                        }
                    }
                } catch (e: Exception) {
                    when (message) {
                        "CHAT_STARTED" -> handleConfirmation("CHAT_STARTED")
                        "CHAT_STOPPED" -> handleConfirmation("CHAT_STOPPED")
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
        ConnectionManager.sendCommand("CHAT_ON")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun stopMonitoring() {
        showLoading()
        ConnectionManager.sendCommand("CHAT_OFF")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
        }
    }

    private fun handleConfirmation(message: String) {
        confirmationTimeout?.cancel()
        when (message) {
            "CHAT_STARTED" -> {
                isActive = true
                updateButton(true)
                hideLoading()
            }
            "CHAT_STOPPED" -> {
                isActive = false
                updateButton(false)
                hideLoading()
            }
        }
    }

    private fun addChat(chat: ChatMessage) {
        chats.add(0, chat)
        // Limit total chats to 50
        if (chats.size > 50) {
            chats.removeAt(chats.size - 1)
        }
        filterChats(searchInput?.text.toString())
    }

    private fun filterChats(query: String) {
        filteredChats.clear()
        if (query.isEmpty()) {
            filteredChats.addAll(chats)
        } else {
            val lowerQuery = query.lowercase()
            filteredChats.addAll(chats.filter {
                it.app.lowercase().contains(lowerQuery) ||
                it.contactName.lowercase().contains(lowerQuery) ||
                it.getLastMessage().lowercase().contains(lowerQuery)
            })
        }
        renderChats()
    }

    private fun renderChats() {
        if (isRendering) return
        isRendering = true
        
        // Clear existing chat items (but keep placeholder)
        val childCount = listContainer?.childCount ?: 0
        for (i in childCount - 1 downTo 0) {
            val child = listContainer?.getChildAt(i)
            if (child?.id != R.id.chats_placeholder) {
                listContainer?.removeViewAt(i)
            }
        }
        
        if (filteredChats.isEmpty()) {
            placeholder?.visibility = View.VISIBLE
        } else {
            placeholder?.visibility = View.GONE
            // Only render first 20 items to prevent ANR
            val itemsToRender = filteredChats.take(20)
            itemsToRender.forEach { chat ->
                listContainer?.addView(createChatItem(chat), 0)
            }
        }
        
        isRendering = false
    }

    private fun createChatItem(chat: ChatMessage): View {
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
            setImageResource(R.drawable.ic_chat)
            layoutParams = LinearLayout.LayoutParams(
                (20 * context.resources.displayMetrics.density).toInt(),
                (20 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = (12 * context.resources.displayMetrics.density).toInt()
            }
        }
        header.addView(icon)
        
        val appName = TextView(context).apply {
            text = chat.getAppName()
            textSize = 11f
            setTextColor(0xFFFBBF24.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(appName)
        
        val time = TextView(context).apply {
            text = chat.formatTime()
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        header.addView(time)
        
        item.addView(header)
        
        // Contact name
        val contactName = TextView(context).apply {
            text = chat.contactName
            textSize = 13f
            setPadding(0, 12, 0, 4)
            setTextColor(context.getColor(R.color.text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(contactName)
        
        // Last message preview
        val lastMessage = TextView(context).apply {
            text = chat.getLastMessage()
            textSize = 12f
            setPadding(0, 0, 0, 0)
            setTextColor(context.getColor(R.color.text_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        item.addView(lastMessage)
        
        // Click to show detail
        item.setOnClickListener {
            showDetailView(chat)
        }
        
        return item
    }

    private fun showDetailView(chat: ChatMessage) {
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
            text = "Chat Details"
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
        
        // App field
        content.addView(createDetailField("App", chat.getAppName()))
        
        // Contact field
        content.addView(createDetailField("Contact", chat.contactName))
        
        // Date & Time field
        content.addView(createDetailField("Date & Time", chat.formatDateTime()))
        
        // Messages section
        val messagesSection = LinearLayout(context).apply {
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
        
        val messagesLabel = TextView(context).apply {
            text = "MESSAGES"
            textSize = 11f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        messagesSection.addView(messagesLabel)
        
        // Display all messages
        chat.messages.forEach { msg ->
            val messageText = TextView(context).apply {
                text = msg.text
                textSize = 14f
                setTextColor(context.getColor(R.color.text_primary))
                setPadding(0, 24, 0, 0)
            }
            messagesSection.addView(messageText)
        }
        
        content.addView(messagesSection)
        
        scrollView.addView(content)
        detailView.addView(scrollView)
        
        // Add detail view to panel content
        panelContent.addView(detailView)
        
        // Fade in detail view
        detailView.animate().alpha(1f).setDuration(300).start()
        
        // Add floating badge on top of detail view with delay
        val badge = createFloatingBadge(chat.app)
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

    private fun createFloatingBadge(app: String): FrameLayout {
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
                setImageResource(R.drawable.ic_chat)
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
                text = when (app) {
                    "com.whatsapp" -> "WHATSAPP"
                    "com.instagram.android" -> "INSTAGRAM"
                    "org.telegram.messenger" -> "TELEGRAM"
                    "com.snapchat.android" -> "SNAPCHAT"
                    "com.facebook.orca" -> "MESSENGER"
                    "com.facebook.katana" -> "FACEBOOK"
                    else -> "CHAT"
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

    override fun getTitle(): String = context.getString(R.string.feature_chats)
    override fun getDescription(): String = context.getString(R.string.feature_chats_desc)

    data class ChatMessage(
        val app: String,
        val contactName: String,
        val messages: List<Message>,
        val timestamp: Long
    ) {
        data class Message(
            val text: String,
            val timestamp: Long
        )
        
        companion object {
            fun fromJson(json: JSONObject): ChatMessage {
                val body = json.optJSONObject("body")
                val messagesArray = body?.optJSONArray("messages")
                val messagesList = mutableListOf<Message>()
                
                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        val msgObj = messagesArray.optJSONObject(i)
                        if (msgObj != null) {
                            messagesList.add(
                                Message(
                                    text = msgObj.optString("text", ""),
                                    timestamp = msgObj.optLong("timestamp", 0)
                                )
                            )
                        }
                    }
                }
                
                return ChatMessage(
                    app = body?.optString("app", "Unknown App") ?: "Unknown App",
                    contactName = body?.optString("contactName", "Unknown Contact") ?: "Unknown Contact",
                    messages = messagesList,
                    timestamp = json.optLong("ts", System.currentTimeMillis())
                )
            }
        }
        
        fun getAppName(): String {
            return when (app) {
                "com.whatsapp" -> "WhatsApp"
                "com.instagram.android" -> "Instagram"
                "org.telegram.messenger" -> "Telegram"
                "com.snapchat.android" -> "Snapchat"
                "com.facebook.orca" -> "Messenger"
                "com.facebook.katana" -> "Facebook"
                else -> app
            }
        }
        
        fun getLastMessage(): String {
            return messages.lastOrNull()?.text ?: ""
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
