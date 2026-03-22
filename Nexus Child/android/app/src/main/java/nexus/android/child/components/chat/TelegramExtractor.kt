package nexus.android.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Production-grade Telegram extractor.
 * Handles Telegram versions 2023-2024 with robust message extraction.
 */
class TelegramExtractor : ChatExtractor {
    
    companion object {
        // Telegram view IDs (verified patterns)
        private const val ACTION_BAR_TITLE = "org.telegram.messenger:id/action_bar_title"
        private const val CHAT_LIST_VIEW = "org.telegram.messenger:id/chat_list_view"
        private const val CHAT_MESSAGE_TEXT = "org.telegram.messenger:id/chat_message_text"
        private const val CHAT_EDIT_TEXT = "org.telegram.messenger:id/chat_edit_text"
    }
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "telegram:$name" else null
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        // Try action bar title
        val titleNodes = safeFindByViewId(root, ACTION_BAR_TITLE)
        if (!titleNodes.isNullOrEmpty()) {
            val text = titleNodes[0].text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        
        // Fallback: find toolbar title in top area
        return findToolbarTitle(root)
    }

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val seenIds = mutableSetOf<String>()
        
        // Find chat list view (Telegram uses custom ListView)
        val listNodes = safeFindByViewId(root, CHAT_LIST_VIEW)
        
        if (!listNodes.isNullOrEmpty()) {
            val listNode = listNodes[0]
            extractMessagesFromList(listNode, messages, seenIds)
        } else {
            // Fallback: find any ListView or RecyclerView
            val listViews = findListViews(root)
            if (listViews.isNotEmpty()) {
                extractMessagesFromList(listViews[0], messages, seenIds)
            }
        }
        
        if (messages.isEmpty()) {
            return GenericChatExtractor("org.telegram.messenger").extractVisibleMessages(root)
        }
        
        return messages
    }
    
    private fun findListViews(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val lists = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 50) {
            val node = queue.removeFirst()
            val className = node.className?.toString() ?: ""
            
            if ((className.contains("ListView") || className.contains("RecyclerView")) && 
                node.childCount > 0) {
                lists.add(node)
                continue
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return lists
    }
    
    private fun extractMessagesFromList(
        listNode: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>
    ) {
        for (i in 0 until listNode.childCount) {
            val messageContainer = listNode.getChild(i) ?: continue
            extractMessageFromContainer(messageContainer, messages, seenIds, i)
            messageContainer.recycle()
        }
    }
    
    private fun extractMessageFromContainer(
        container: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>,
        position: Int
    ) {
        // Try to find message text by view ID
        val textNodes = safeFindByViewId(container, CHAT_MESSAGE_TEXT)
        var messageText: String? = null
        
        if (!textNodes.isNullOrEmpty()) {
            messageText = textNodes[0].text?.toString()
        }
        
        // Fallback: collect all text
        if (messageText.isNullOrBlank()) {
            val texts = mutableListOf<String>()
            collectTexts(container, texts, 0, 8)
            
            val filtered = texts.filter { text ->
                text.length > 1 && 
                !isUIElement(text) &&
                !isTimestamp(text)
            }
            
            messageText = filtered.joinToString(" ")
        }
        
        if (messageText.isNullOrBlank() || messageText.length < 1) return
        
        // Determine direction
        val direction = inferDirectionFromPosition(container)
        
        // Extract sender (for group chats, Telegram shows sender name)
        val sender = extractSenderName(container)
        
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
    
    private fun extractSenderName(container: AccessibilityNodeInfo): String? {
        // Telegram shows sender name in group chats as a colored text above message
        val texts = mutableListOf<String>()
        collectTexts(container, texts, 0, 5)
        
        // Sender name is typically short and appears first
        return texts.firstOrNull { it.length in 2..30 && !it.contains(" ") }
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
            "reply", "forward", "edit", "delete", "pin", "select",
            "read", "delivered", "sent", "edited", "views"
        )
        return uiElements.any { lower == it }
    }
    
    private fun isTimestamp(text: String): Boolean {
        val timestampPatterns = listOf(
            "\\d{1,2}:\\d{2}".toRegex(),
            "(today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)".toRegex(RegexOption.IGNORE_CASE)
        )
        return timestampPatterns.any { it.matches(text.trim()) } || (text.length < 8 && text.contains(":"))
    }
    
    private fun inferDirectionFromPosition(container: AccessibilityNodeInfo): String {
        // Strategy 1: Check class name and content description
        val className = container.className?.toString()?.lowercase() ?: ""
        val contentDesc = container.contentDescription?.toString()?.lowercase() ?: ""
        
        if (className.contains("outgoing") || className.contains("sent") || className.contains("out") ||
            contentDesc.contains("outgoing") || contentDesc.contains("you")) {
            return "OUT"
        }
        
        if (className.contains("incoming") || className.contains("received") || className.contains("in") ||
            contentDesc.contains("incoming") || contentDesc.contains("received")) {
            return "IN"
        }
        
        // Strategy 2: Check view ID
        val viewId = try {
            container.viewIdResourceName?.lowercase() ?: ""
        } catch (_: Throwable) {
            ""
        }
        
        if (viewId.contains("outgoing") || viewId.contains("sent") || viewId.contains("out")) {
            return "OUT"
        }
        
        if (viewId.contains("incoming") || viewId.contains("received") || viewId.contains("in")) {
            return "IN"
        }
        
        // Strategy 3: Check parent hierarchy
        var parent = container.parent
        var depth = 0
        while (parent != null && depth < 5) {
            val parentClass = parent.className?.toString()?.lowercase() ?: ""
            val parentViewId = try {
                parent.viewIdResourceName?.lowercase() ?: ""
            } catch (_: Throwable) {
                ""
            }
            
            if (parentClass.contains("outgoing") || parentViewId.contains("outgoing")) {
                return "OUT"
            }
            
            if (parentClass.contains("incoming") || parentViewId.contains("incoming")) {
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
        // Telegram shows "typing..." in action bar subtitle
        val typingNodes = root.findAccessibilityNodeInfosByText("typing")
        return !typingNodes.isNullOrEmpty()
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        // Check for message input field
        val editTextNodes = safeFindByViewId(root, CHAT_EDIT_TEXT)
        if (!editTextNodes.isNullOrEmpty()) return true
        
        // Check for chat list view
        val listNodes = safeFindByViewId(root, CHAT_LIST_VIEW)
        if (!listNodes.isNullOrEmpty() && listNodes[0].childCount > 0) return true
        
        // Fallback: look for EditText with message hint
        return hasMessageInput(root)
    }
    
    private fun hasMessageInput(root: AccessibilityNodeInfo): Boolean {
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
        val candidates = mutableListOf<Pair<String, Int>>()
        
        findTitleCandidates(root, topThreshold, candidates, 0, 30)
        
        return candidates
            .filter { it.first.length in 2..50 }
            .minByOrNull { it.second }
            ?.first
    }
    
    private fun findTitleCandidates(
        node: AccessibilityNodeInfo,
        topThreshold: Int,
        candidates: MutableList<Pair<String, Int>>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        
        if (rect.top < topThreshold) {
            val text = node.text?.toString()
            val className = node.className?.toString()
            
            if (!text.isNullOrBlank() && 
                (className?.contains("TextView") == true || 
                 className?.contains("Title") == true)) {
                candidates.add(text to depth)
            }
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findTitleCandidates(child, topThreshold, candidates, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }
    
    private fun generateMessageId(text: String, direction: String, position: Int): String {
        val input = "telegram:$text:$direction:$position"
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
