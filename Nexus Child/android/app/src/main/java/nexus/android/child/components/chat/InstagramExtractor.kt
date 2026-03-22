package nexus.android.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Production-grade Instagram DM extractor.
 * Handles Instagram versions 2023-2024 with robust fallbacks.
 * Instagram uses RecyclerView for messages with dynamic view IDs.
 */
class InstagramExtractor : ChatExtractor {
    
    companion object {
        // Instagram view IDs (these change frequently, so we use multiple strategies)
        private const val ACTION_BAR_TITLE = "com.instagram.android:id/action_bar_title"
        private const val THREAD_TITLE = "com.instagram.android:id/thread_title"
        private const val RECYCLER_VIEW = "com.instagram.android:id/recycler_view"
        private const val MESSAGE_CONTENT = "com.instagram.android:id/message_content"
        private const val ROW_THREAD_COMPOSER = "com.instagram.android:id/row_thread_composer_edittext"
    }
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "instagram:$name" else null
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        // Strategy 1: Try action bar title
        val titleNodes = safeFindByViewId(root, ACTION_BAR_TITLE)
        if (!titleNodes.isNullOrEmpty()) {
            val text = titleNodes[0].text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        
        // Strategy 2: Try thread title
        val threadNodes = safeFindByViewId(root, THREAD_TITLE)
        if (!threadNodes.isNullOrEmpty()) {
            val text = threadNodes[0].text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        
        // Strategy 3: Find toolbar/action bar in top 15% of screen
        return findToolbarTitle(root)
    }

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val seenIds = mutableSetOf<String>()
        
        // Strategy 1: Find RecyclerView
        val recyclerNodes = safeFindByViewId(root, RECYCLER_VIEW)
        
        if (!recyclerNodes.isNullOrEmpty()) {
            // Instagram typically has multiple RecyclerViews, find the messages one
            for (recyclerNode in recyclerNodes) {
                if (recyclerNode.childCount > 0) {
                    extractMessagesFromRecycler(recyclerNode, messages, seenIds)
                    if (messages.isNotEmpty()) break // Found the right RecyclerView
                }
            }
        }
        
        // Strategy 2: Fallback to generic extraction if no messages found
        if (messages.isEmpty()) {
            return GenericChatExtractor("com.instagram.android").extractVisibleMessages(root)
        }
        
        return messages
    }
    
    private fun extractMessagesFromRecycler(
        recycler: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>
    ) {
        for (i in 0 until recycler.childCount) {
            val messageContainer = recycler.getChild(i) ?: continue
            
            // Instagram message containers have specific characteristics
            if (isMessageContainer(messageContainer)) {
                extractMessageFromContainer(messageContainer, messages, seenIds, i)
            }
            
            messageContainer.recycle()
        }
    }
    
    private fun isMessageContainer(node: AccessibilityNodeInfo): Boolean {
        // Message containers typically have text content and reasonable bounds
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        
        // Must have reasonable height (not too small, not full screen)
        val height = rect.height()
        if (height < 50 || height > 500) return false
        
        // Must contain text
        return hasTextContent(node, 0, 5)
    }
    
    private fun hasTextContent(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): Boolean {
        if (depth > maxDepth) return false
        
        if (!node.text.isNullOrBlank()) return true
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val hasText = hasTextContent(child, depth + 1, maxDepth)
                child.recycle()
                if (hasText) return true
            }
        }
        
        return false
    }
    
    private fun extractMessageFromContainer(
        container: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>,
        position: Int
    ) {
        // Collect all text from container
        val texts = mutableListOf<String>()
        collectTexts(container, texts, 0, 8)
        
        if (texts.isEmpty()) return
        
        // Filter out UI elements and timestamps
        val messageTexts = texts.filter { text ->
            text.length > 1 && 
            !isUIElement(text) &&
            !isTimestamp(text)
        }
        
        if (messageTexts.isEmpty()) return
        
        // Instagram often has sender name + message text
        val messageText = if (messageTexts.size > 1 && messageTexts[0].length < 30) {
            // First text might be sender name, combine with message
            messageTexts.drop(1).joinToString(" ")
        } else {
            messageTexts.joinToString(" ")
        }
        
        if (messageText.isBlank()) return
        
        // Determine direction by position
        val direction = inferDirectionFromPosition(container)
        
        // Extract sender for group chats (short first text)
        val sender = if (messageTexts.size > 1 && messageTexts[0].length in 2..30) {
            messageTexts[0]
        } else null
        
        val msgId = generateMessageId(messageText, direction, position)
        
        if (seenIds.contains(msgId)) return
        seenIds.add(msgId)
        
        messages.add(MessageData(
            msgId = msgId,
            text = messageText,
            sender = sender,
            direction = direction,
            ts = System.currentTimeMillis(),
            metadata = mapOf("position" to position.toString())
        ))
    }
    
    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        
        node.text?.toString()?.let { 
            if (it.isNotBlank() && it.length < 1000) texts.add(it) 
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, texts, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }
    
    private fun isUIElement(text: String): Boolean {
        val lower = text.lowercase()
        val uiElements = setOf(
            "like", "reply", "react", "seen", "delivered", "sent",
            "typing", "active", "online", "message", "photo", "video",
            "audio", "sticker", "gif", "unsend", "forward"
        )
        return uiElements.any { lower == it || lower.contains("$it ") }
    }
    
    private fun isTimestamp(text: String): Boolean {
        // Match patterns like "2:30 PM", "Yesterday", "Monday", "Jan 15"
        val timestampPatterns = listOf(
            "\\d{1,2}:\\d{2}\\s*[AP]M".toRegex(RegexOption.IGNORE_CASE),
            "\\d{1,2}:\\d{2}".toRegex(),
            "(today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)".toRegex(RegexOption.IGNORE_CASE),
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+\\d{1,2}".toRegex(RegexOption.IGNORE_CASE)
        )
        return timestampPatterns.any { it.matches(text.trim()) } || text.length < 10 && text.contains(":")
    }
    
    private fun inferDirectionFromPosition(container: AccessibilityNodeInfo): String {
        // Strategy 1: Check class name and content description
        val className = container.className?.toString()?.lowercase() ?: ""
        val contentDesc = container.contentDescription?.toString()?.lowercase() ?: ""
        
        if (className.contains("outgoing") || className.contains("sent") || className.contains("right") ||
            contentDesc.contains("you sent") || contentDesc.contains("outgoing")) {
            return "OUT"
        }
        
        if (className.contains("incoming") || className.contains("received") || className.contains("left") ||
            contentDesc.contains("received") || contentDesc.contains("incoming")) {
            return "IN"
        }
        
        // Strategy 2: Check view ID
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
        
        // Strategy 3: Check parent hierarchy
        var parent = container.parent
        var depth = 0
        while (parent != null && depth < 5) {
            val parentClass = parent.className?.toString()?.lowercase() ?: ""
            val parentDesc = parent.contentDescription?.toString()?.lowercase() ?: ""
            
            if (parentClass.contains("outgoing") || parentDesc.contains("outgoing")) {
                return "OUT"
            }
            
            if (parentClass.contains("incoming") || parentDesc.contains("incoming")) {
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
        
        return if (relativePosition > 0.55) "OUT" else "IN"
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): Boolean {
        // Look for "typing..." text
        val typingNodes = root.findAccessibilityNodeInfosByText("typing")
        if (!typingNodes.isNullOrEmpty()) return true
        
        // Check for typing indicator animation (usually has specific class names)
        return findTypingIndicator(root, 0, 30)
    }
    
    private fun findTypingIndicator(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): Boolean {
        if (depth > maxDepth) return false
        
        val className = node.className?.toString()?.lowercase()
        if (className?.contains("typing") == true || className?.contains("indicator") == true) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findTypingIndicator(child, depth + 1, maxDepth)
                child.recycle()
                if (found) return true
            }
        }
        
        return false
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        // Check for message composer (text input field)
        val composerNodes = safeFindByViewId(root, ROW_THREAD_COMPOSER)
        if (!composerNodes.isNullOrEmpty()) return true
        
        // Check for RecyclerView with messages
        val recyclerNodes = safeFindByViewId(root, RECYCLER_VIEW)
        if (!recyclerNodes.isNullOrEmpty() && recyclerNodes[0].childCount > 0) return true
        
        // Fallback: look for message-like content
        return hasMessageLikeContent(root)
    }
    
    private fun hasMessageLikeContent(root: AccessibilityNodeInfo): Boolean {
        // Look for EditText with hints like "Message", "Type a message"
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 50) {
            val node = queue.removeFirst()
            val className = node.className?.toString()
            
            if (className?.contains("EditText") == true) {
                val hint = node.text?.toString()?.lowercase()
                if (hint?.contains("message") == true) return true
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return false
    }
    
    private fun findToolbarTitle(root: AccessibilityNodeInfo): String? {
        val screenHeight = root.window?.let {
            val rect = android.graphics.Rect()
            it.getBoundsInScreen(rect)
            rect.height()
        } ?: 2000
        
        val topThreshold = (screenHeight * 0.15).toInt()
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 40) {
            val node = queue.removeFirst()
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            
            if (rect.top < topThreshold) {
                val text = node.text?.toString()
                val className = node.className?.toString()
                
                if (!text.isNullOrBlank() && 
                    text.length in 2..50 &&
                    (className?.contains("TextView") == true || 
                     className?.contains("Title") == true)) {
                    return text
                }
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return null
    }
    
    private fun generateMessageId(text: String, direction: String, position: Int): String {
        val input = "instagram:$text:$direction:$position"
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
