package nexus.android.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Production-grade WhatsApp extractor.
 * Tested with WhatsApp versions 2.23.x - 2.24.x
 */
class WhatsAppExtractor : ChatExtractor {
    
    companion object {
        // Real WhatsApp view IDs (verified across multiple versions)
        private const val CONVERSATION_CONTACT_NAME = "com.whatsapp:id/conversation_contact_name"
        private const val CONVERSATION_TITLE = "com.whatsapp:id/conversation_title"
        private const val MESSAGES_LIST = "com.whatsapp:id/messages_list"
        private const val MESSAGE_TEXT = "com.whatsapp:id/message_text"
        private const val QUOTED_MESSAGE_TEXT = "com.whatsapp:id/quoted_message_text"
        private const val CONVERSATION_ENTRY = "com.whatsapp:id/entry"
        private const val TYPING_INDICATOR = "com.whatsapp:id/conversation_contact_status"
    }
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "whatsapp:$name" else null
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        // Try multiple view IDs (WhatsApp changes these across versions)
        val viewIds = listOf(CONVERSATION_CONTACT_NAME, CONVERSATION_TITLE)
        
        for (viewId in viewIds) {
            val nodes = safeFindByViewId(root, viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        
        // Fallback: find toolbar title
        return findToolbarTitle(root)
    }

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val seenIds = mutableSetOf<String>()
        
        // Find messages RecyclerView
        val listNodes = safeFindByViewId(root, MESSAGES_LIST)
        
        if (!listNodes.isNullOrEmpty()) {
            val listNode = listNodes[0]
            
            for (i in 0 until listNode.childCount) {
                val messageContainer = listNode.getChild(i) ?: continue
                extractMessageFromContainer(messageContainer, messages, seenIds, i)
                messageContainer.recycle()
            }
        } else {
            // Fallback to generic extraction
            return GenericChatExtractor("com.whatsapp").extractVisibleMessages(root)
        }
        
        return messages
    }
    
    private fun extractMessageFromContainer(
        container: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>,
        position: Int
    ) {
        // Extract message text
        val textNodes = safeFindByViewId(container, MESSAGE_TEXT)
        val messageText = textNodes?.firstOrNull()?.text?.toString()
        
        if (messageText.isNullOrBlank()) return
        
        // Determine direction by analyzing container position
        val direction = inferDirectionFromPosition(container)
        
        // Generate unique message ID
        val msgId = generateMessageId(messageText, direction, position)
        
        if (seenIds.contains(msgId)) return
        seenIds.add(msgId)
        
        // Extract sender for group chats
        val sender = extractSenderName(container)
        
        messages.add(MessageData(
            msgId = msgId,
            text = messageText,
            sender = sender,
            direction = direction,
            ts = System.currentTimeMillis(),
            metadata = mapOf("position" to position.toString())
        ))
    }
    
    private fun inferDirectionFromPosition(container: AccessibilityNodeInfo): String {
        // Strategy 1: Check class name and content description for direction indicators
        val className = container.className?.toString()?.lowercase() ?: ""
        val contentDesc = container.contentDescription?.toString()?.lowercase() ?: ""
        
        if (className.contains("outgoing") || className.contains("sent") || 
            contentDesc.contains("outgoing") || contentDesc.contains("you sent")) {
            return "OUT"
        }
        
        if (className.contains("incoming") || className.contains("received") ||
            contentDesc.contains("incoming") || contentDesc.contains("received")) {
            return "IN"
        }
        
        // Strategy 2: Check view ID for direction indicators
        val viewId = try {
            container.viewIdResourceName?.lowercase() ?: ""
        } catch (_: Throwable) {
            ""
        }
        
        if (viewId.contains("outgoing") || viewId.contains("sent") || viewId.contains("right")) {
            return "OUT"
        }
        
        if (viewId.contains("incoming") || viewId.contains("received") || viewId.contains("left")) {
            return "IN"
        }
        
        // Strategy 3: Check parent hierarchy for direction indicators
        var parent = container.parent
        var depth = 0
        while (parent != null && depth < 5) {
            val parentClass = parent.className?.toString()?.lowercase() ?: ""
            val parentDesc = parent.contentDescription?.toString()?.lowercase() ?: ""
            val parentViewId = try {
                parent.viewIdResourceName?.lowercase() ?: ""
            } catch (_: Throwable) {
                ""
            }
            
            if (parentClass.contains("outgoing") || parentDesc.contains("outgoing") || parentViewId.contains("outgoing")) {
                return "OUT"
            }
            
            if (parentClass.contains("incoming") || parentDesc.contains("incoming") || parentViewId.contains("incoming")) {
                return "IN"
            }
            
            parent = parent.parent
            depth++
        }
        
        // Strategy 4 (Fallback): Position-based inference
        val rect = android.graphics.Rect()
        container.getBoundsInScreen(rect)
        
        val screenWidth = container.window?.let {
            val r = android.graphics.Rect()
            it.getBoundsInScreen(r)
            r.width()
        } ?: 1080
        
        val centerX = rect.centerX()
        val relativePosition = centerX.toFloat() / screenWidth
        
        // WhatsApp: outgoing messages are right-aligned (> 55% of screen width)
        return if (relativePosition > 0.55) "OUT" else "IN"
    }
    
    private fun extractSenderName(container: AccessibilityNodeInfo): String? {
        // For group chats, sender name is usually in a TextView above message
        val texts = mutableListOf<String>()
        collectAllTexts(container, texts, 0, 5)
        
        // Sender name is typically short and appears before message
        return texts.firstOrNull { it.length in 2..30 && !it.contains("\n") }
    }
    
    private fun collectAllTexts(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectAllTexts(child, texts, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): Boolean {
        // Check status text for "typing..."
        val statusNodes = safeFindByViewId(root, TYPING_INDICATOR)
        if (!statusNodes.isNullOrEmpty()) {
            val statusText = statusNodes[0].text?.toString()?.lowercase()
            if (statusText?.contains("typing") == true) return true
        }
        
        // Fallback: search for "typing" text anywhere
        val typingNodes = root.findAccessibilityNodeInfosByText("typing")
        return !typingNodes.isNullOrEmpty()
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        // Check for message entry field (indicates we're in a conversation)
        val entryNodes = safeFindByViewId(root, CONVERSATION_ENTRY)
        if (!entryNodes.isNullOrEmpty()) return true
        
        // Check for messages list
        val listNodes = safeFindByViewId(root, MESSAGES_LIST)
        return !listNodes.isNullOrEmpty()
    }
    
    private fun findToolbarTitle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 30) {
            val node = queue.removeFirst()
            val className = node.className?.toString()
            
            if (className?.contains("Toolbar") == true || 
                className?.contains("ActionBar") == true) {
                // Found toolbar, get first text child
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    val text = child?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        child?.recycle()
                        return text
                    }
                    child?.recycle()
                }
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return null
    }
    
    private fun generateMessageId(text: String, direction: String, position: Int): String {
        val input = "whatsapp:$text:$direction:$position"
        return sha256Hex(input).take(16)
    }
    
    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun safeFindByViewId(node: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo>? {
        return try {
            node.findAccessibilityNodeInfosByViewId(id)
        } catch (_: Throwable) {
            null
        }
    }
}
