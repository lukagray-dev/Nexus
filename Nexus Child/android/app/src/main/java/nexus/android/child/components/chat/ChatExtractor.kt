package nexus.android.child.components.chat

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

/**
 * Production-grade chat extractor interface.
 * Each app-specific extractor implements robust message extraction.
 */
interface ChatExtractor {
    /**
     * Extract unique conversation identifier (contact ID, group ID, etc.)
     */
    fun extractConversationId(root: AccessibilityNodeInfo): String?
    
    /**
     * Extract contact/group display name
     */
    fun extractContactName(root: AccessibilityNodeInfo): String?
    
    /**
     * Extract all visible messages with proper deduplication
     * Returns messages with unique IDs based on content + position
     */
    fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData>
    
    /**
     * Extract typing indicator state
     */
    fun extractTypingState(root: AccessibilityNodeInfo): Boolean
    
    /**
     * Check if current screen is a conversation view (not chat list)
     */
    fun isConversationView(root: AccessibilityNodeInfo): Boolean
}

/**
 * Generic fallback extractor with improved heuristics.
 * Uses spatial analysis and text patterns for better accuracy.
 */
class GenericChatExtractor(private val pkg: String) : ChatExtractor {
    
    override fun extractConversationId(root: AccessibilityNodeInfo): String? {
        val name = extractContactName(root)
        return if (name != null) "$pkg:$name" else null
    }
    
    override fun extractContactName(root: AccessibilityNodeInfo): String? {
        // Strategy: Find toolbar/action bar title in top 20% of screen
        val screenHeight = root.window?.let { 
            val rect = android.graphics.Rect()
            it.getBoundsInScreen(rect)
            rect.height()
        } ?: 2000
        
        val topThreshold = screenHeight * 0.2
        
        val candidates = mutableListOf<Pair<String, Int>>()
        traverseForTitle(root, topThreshold.toInt(), candidates, 0, 30)
        
        // Return first non-generic title
        return candidates
            .filter { !isGenericText(it.first) }
            .minByOrNull { it.second }
            ?.first
    }
    
    private fun traverseForTitle(
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
                traverseForTitle(child, topThreshold, candidates, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }
    
    override fun extractVisibleMessages(root: AccessibilityNodeInfo): List<MessageData> {
        val messages = mutableListOf<MessageData>()
        val seenTexts = mutableSetOf<String>()
        
        // Find RecyclerView or ListView (common for chat messages)
        val listNodes = findListContainers(root)
        
        if (listNodes.isNotEmpty()) {
            listNodes.forEach { listNode ->
                extractMessagesFromList(listNode, messages, seenTexts)
                listNode.recycle()
            }
        } else {
            // Fallback: scan entire tree
            extractMessagesGeneric(root, messages, seenTexts, 0, 150)
        }
        
        return messages
    }
    
    private fun findListContainers(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val lists = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        
        while (queue.isNotEmpty() && seen++ < 50) {
            val node = queue.removeFirst()
            val className = node.className?.toString() ?: ""
            
            if (className.contains("RecyclerView") || 
                className.contains("ListView") ||
                className.contains("ScrollView")) {
                lists.add(node)
                continue // Don't traverse children
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
        seenTexts: MutableSet<String>
    ) {
        for (i in 0 until listNode.childCount) {
            val child = listNode.getChild(i) ?: continue
            extractMessageFromBubble(child, messages, seenTexts, i)
            child.recycle()
        }
    }
    
    private fun extractMessageFromBubble(
        bubble: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenTexts: MutableSet<String>,
        position: Int
    ) {
        val texts = mutableListOf<String>()
        collectTexts(bubble, texts, 0, 10)
        
        val messageText = texts
            .filter { it.length >= 1 && !isGenericText(it) }
            .joinToString(" ")
            .trim()
        
        if (messageText.isNotEmpty() && !seenTexts.contains(messageText)) {
            seenTexts.add(messageText)
            
            val direction = inferDirection(bubble)
            val msgId = generateMessageId(messageText, direction, position)
            
            messages.add(MessageData(
                msgId = msgId,
                text = messageText,
                direction = direction,
                ts = System.currentTimeMillis(),
                metadata = mapOf("position" to position.toString())
            ))
        }
    }
    
    private fun extractMessagesGeneric(
        node: AccessibilityNodeInfo,
        messages: MutableList<MessageData>,
        seenTexts: MutableSet<String>,
        depth: Int,
        maxNodes: Int
    ) {
        if (messages.size >= maxNodes || depth > 20) return
        
        val text = node.text?.toString()
        val className = node.className?.toString()
        
        if (!text.isNullOrBlank() && 
            text.length >= 1 &&
            !isGenericText(text) &&
            !seenTexts.contains(text) &&
            (className?.contains("TextView") == true)) {
            
            seenTexts.add(text)
            val direction = inferDirection(node)
            val msgId = generateMessageId(text, direction, messages.size)
            
            messages.add(MessageData(
                msgId = msgId,
                text = text,
                direction = direction,
                ts = System.currentTimeMillis()
            ))
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractMessagesGeneric(child, messages, seenTexts, depth + 1, maxNodes)
                child.recycle()
            }
        }
    }
    
    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, texts, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }
    
    private fun inferDirection(node: AccessibilityNodeInfo): String {
        // Strategy 1: Check class name and content description
        val className = node.className?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
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
            node.viewIdResourceName?.lowercase() ?: ""
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
        var parent = node.parent
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
        node.getBoundsInScreen(rect)
        
        val screenWidth = node.window?.let {
            val r = android.graphics.Rect()
            it.getBoundsInScreen(r)
            r.width()
        } ?: 1080
        
        val centerX = rect.centerX()
        val relativePosition = centerX.toFloat() / screenWidth
        
        // Messages on right side are typically outgoing
        return if (relativePosition > 0.55) "OUT" else "IN"
    }
    
    private fun isGenericText(text: String): Boolean {
        val lower = text.lowercase()
        val generic = setOf("back", "send", "type a message", "search", "call", "video", 
                           "attach", "emoji", "sticker", "today", "yesterday", "online",
                           "typing", "seen", "delivered", "read")
        return generic.any { lower.contains(it) } || text.length > 500
    }
    
    private fun generateMessageId(text: String, direction: String, position: Int): String {
        val input = "$pkg:$text:$direction:$position"
        return sha256Hex(input).take(16)
    }
    
    override fun extractTypingState(root: AccessibilityNodeInfo): Boolean {
        val matches = root.findAccessibilityNodeInfosByText("typing")
        return !matches.isNullOrEmpty()
    }
    
    override fun isConversationView(root: AccessibilityNodeInfo): Boolean {
        // Heuristic: conversation views have message input fields
        val inputIndicators = listOf("type a message", "message", "send", "text message")
        return inputIndicators.any { keyword ->
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            !nodes.isNullOrEmpty()
        }
    }
    
    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
