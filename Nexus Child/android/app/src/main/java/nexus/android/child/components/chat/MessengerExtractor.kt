package nexus.android.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Production-grade Facebook Messenger extractor.
 * Handles Messenger 2023-2024 with robust extraction patterns.
 */
class MessengerExtractor : ChatExtractor {
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "messenger:$name" else null
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        return findToolbarTitle(root)
    }

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val seenIds = mutableSetOf<String>()
        
        val recyclerViews = findRecyclerViews(root)
        
        if (recyclerViews.isNotEmpty()) {
            val messagesRecycler = recyclerViews.maxByOrNull { it.childCount }
            if (messagesRecycler != null && messagesRecycler.childCount > 0) {
                extractMessagesFromRecycler(messagesRecycler, messages, seenIds)
            }
        }
        
        if (messages.isEmpty()) {
            return GenericChatExtractor("com.facebook.orca").extractVisibleMessages(root)
        }
        
        return messages
    }
    
    private fun findRecyclerViews(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val recyclers = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 60) {
            val node = queue.removeFirst()
            val className = node.className?.toString() ?: ""
            
            if (className.contains("RecyclerView") && node.childCount > 0) {
                recyclers.add(node)
                continue
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return recyclers
    }
    
    private fun extractMessagesFromRecycler(
        recycler: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenIds: MutableSet<String>
    ) {
        for (i in 0 until recycler.childCount) {
            val messageContainer = recycler.getChild(i) ?: continue
            
            if (isMessageContainer(messageContainer)) {
                extractMessageFromContainer(messageContainer, messages, seenIds, i)
            }
            
            messageContainer.recycle()
        }
    }
    
    private fun isMessageContainer(node: AccessibilityNodeInfo): Boolean {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        
        val height = rect.height()
        if (height < 40 || height > 600) return false
        
        return hasTextContent(node, 0, 6)
    }
    
    private fun hasTextContent(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): Boolean {
        if (depth > maxDepth) return false
        
        if (!node.text.isNullOrBlank() && node.text.toString().length > 1) return true
        
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
        val texts = mutableListOf<String>()
        collectTexts(container, texts, 0, 8)
        
        if (texts.isEmpty()) return
        
        val messageTexts = texts.filter { text ->
            text.length > 1 && 
            !isUIElement(text) &&
            !isTimestamp(text)
        }
        
        if (messageTexts.isEmpty()) return
        
        val messageText = messageTexts.joinToString(" ")
        if (messageText.isBlank()) return
        
        val direction = inferDirectionFromPosition(container)
        val msgId = generateMessageId(messageText, direction, position)
        
        if (seenIds.contains(msgId)) return
        seenIds.add(msgId)
        
        messages.add(MessageData(
            msgId = msgId,
            text = messageText,
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
            "active", "message", "photo", "video", "sticker"
        )
        return uiElements.any { lower == it }
    }
    
    private fun isTimestamp(text: String): Boolean {
        val timestampPatterns = listOf(
            "\\d{1,2}:\\d{2}\\s*[AP]M".toRegex(RegexOption.IGNORE_CASE),
            "\\d{1,2}:\\d{2}".toRegex(),
            "(today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)".toRegex(RegexOption.IGNORE_CASE)
        )
        return timestampPatterns.any { it.matches(text.trim()) } || (text.length < 10 && text.contains(":"))
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
        val typingNodes = root.findAccessibilityNodeInfosByText("typing")
        return !typingNodes.isNullOrEmpty()
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        return hasMessageInput(root) || hasMessageList(root)
    }
    
    private fun hasMessageInput(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 50) {
            val node = queue.removeFirst()
            val className = node.className?.toString()
            
            if (className?.contains("EditText") == true) return true
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        
        return false
    }
    
    private fun hasMessageList(root: AccessibilityNodeInfo): Boolean {
        val recyclers = findRecyclerViews(root)
        return recyclers.any { it.childCount > 2 }
    }
    
    private fun findToolbarTitle(root: AccessibilityNodeInfo): String? {
        val screenHeight = root.window?.let {
            val rect = android.graphics.Rect()
            it.getBoundsInScreen(rect)
            rect.height()
        } ?: 2000
        
        val topThreshold = (screenHeight * 0.15).toInt()
        val candidates = mutableListOf<Pair<String, Int>>()
        
        findTitleCandidates(root, topThreshold, candidates, 0, 35)
        
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
            if (!text.isNullOrBlank()) {
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
        val input = "messenger:$text:$direction:$position"
        return sha256Hex(input).take(16)
    }
    
    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Facebook app chat extractor (for Facebook app's messaging feature).
 */
class FacebookExtractor : ChatExtractor {
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "facebook:$name" else null
    }

    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        return GenericChatExtractor("com.facebook.katana").extractContactName(root)
    }

    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        return GenericChatExtractor("com.facebook.katana").extractVisibleMessages(root)
    }

    override fun extractTypingState(root: AccessibilityNodeInfo): Boolean {
        val typingNodes = root.findAccessibilityNodeInfosByText("is typing")
        return !typingNodes.isNullOrEmpty()
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        return GenericChatExtractor("com.facebook.katana").isConversationView(root)
    }
}
