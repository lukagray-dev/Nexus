package nexus.android.child.components.screen

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * MediaProjectionAutoClickHelper - Utility for auto-clicking MediaProjection permission dialog
 * 
 * SECURITY:
 * - Uses flag-based gating to prevent clicking on unrelated dialogs
 * - Only clicks when explicitly enabled via enableAutoClick()
 * - Auto-disables after 10 seconds to prevent stale state
 * - Thread-safe flag management
 * 
 * USAGE:
 * 1. TransparentMediaProjectionActivity calls enableAutoClick() before requesting permission
 * 2. ParentalAccessibilityService checks isAutoClickEnabled() before clicking
 * 3. After click or timeout, flag is automatically disabled
 * 
 * SUPPORTED LANGUAGES:
 * - English: "Start now", "Start"
 * - Chinese: "立即开始", "开始"
 * - Japanese: "今すぐ開始", "開始"
 * - Korean: "지금 시작", "시작"
 * - Spanish: "Iniciar ahora", "Iniciar"
 * - French: "Démarrer maintenant", "Démarrer"
 * - German: "Jetzt starten", "Starten"
 * - Portuguese: "Iniciar agora", "Iniciar"
 * - Russian: "Начать сейчас", "Начать"
 * - Hindi: "अभी शुरू करें", "शुरू करें"
 * 
 * OEM VARIATIONS:
 * - Samsung: "Start now", "Allow"
 * - Xiaomi: "Start", "Allow"
 * - Oppo/Vivo: "Start", "Confirm"
 * - OnePlus: "Start now"
 * - Huawei: "Start", "OK"
 */
object MediaProjectionAutoClickHelper {
    
    private const val TAG = "MediaProjAutoClick"
    
    // Gating mechanism to prevent clicking on unrelated dialogs
    @Volatile
    private var autoClickEnabled = false
    
    @Volatile
    private var enabledAtMs = 0L
    
    private const val AUTO_CLICK_TIMEOUT_MS = 5_000L // 5 seconds timeout (increased for slower devices)
    
    private val lock = Any()
    
    /**
     * Enable auto-click for MediaProjection dialog
     * 
     * MUST be called by TransparentMediaProjectionActivity before requesting permission
     * Auto-disables after 2 seconds to prevent stale state while keeping window tight
     */
    fun enableAutoClick() {
        synchronized(lock) {
            autoClickEnabled = true
            enabledAtMs = System.currentTimeMillis()
            Log.d(TAG, "✅ Auto-click ENABLED (will timeout in ${AUTO_CLICK_TIMEOUT_MS}ms)")
        }
    }
    
    /**
     * Disable auto-click (called after successful click or timeout)
     */
    fun disableAutoClick() {
        synchronized(lock) {
            if (autoClickEnabled) {
                autoClickEnabled = false
                enabledAtMs = 0L
                Log.d(TAG, "🛑 Auto-click DISABLED")
            }
        }
    }
    
    /**
     * Check if auto-click is currently enabled and not timed out
     * 
     * @return true if auto-click is enabled and within timeout window
     */
    fun isAutoClickEnabled(): Boolean {
        synchronized(lock) {
            if (!autoClickEnabled) {
                return false
            }
            
            // Check timeout
            val elapsed = System.currentTimeMillis() - enabledAtMs
            if (elapsed > AUTO_CLICK_TIMEOUT_MS) {
                Log.w(TAG, "⏱️ Auto-click timed out (${elapsed}ms elapsed)")
                autoClickEnabled = false
                enabledAtMs = 0L
                return false
            }
            
            return true
        }
    }
    
    // Button text variations across languages and OEMs
    private val BUTTON_TEXTS = listOf(
        // English
        "Start now", "Start", "Allow", "OK", "Confirm",
        // Chinese (Simplified)
        "立即开始", "开始", "允许", "确定",
        // Chinese (Traditional)
        "立即開始", "開始", "允許", "確定",
        // Japanese
        "今すぐ開始", "開始", "許可", "OK",
        // Korean
        "지금 시작", "시작", "허용", "확인",
        // Spanish
        "Iniciar ahora", "Iniciar", "Permitir", "Aceptar",
        // French
        "Démarrer maintenant", "Démarrer", "Autoriser", "OK",
        // German
        "Jetzt starten", "Starten", "Zulassen", "OK",
        // Portuguese
        "Iniciar agora", "Iniciar", "Permitir", "OK",
        // Russian
        "Начать сейчас", "Начать", "Разрешить", "ОК",
        // Hindi
        "अभी शुरू करें", "शुरू करें", "अनुमति दें", "ठीक है",
        // Arabic
        "ابدأ الآن", "ابدأ", "سماح", "موافق",
        // Turkish
        "Şimdi başlat", "Başlat", "İzin ver", "Tamam",
        // Italian
        "Avvia ora", "Avvia", "Consenti", "OK",
        // Dutch
        "Nu starten", "Starten", "Toestaan", "OK",
        // Polish
        "Rozpocznij teraz", "Rozpocznij", "Zezwól", "OK",
        // Vietnamese
        "Bắt đầu ngay", "Bắt đầu", "Cho phép", "OK"
    )
    
    /**
     * Attempt to auto-click MediaProjection permission dialog button
     * 
     * SECURITY: Only clicks if auto-click is explicitly enabled via enableAutoClick()
     * 
     * ANDROID 14 WORKAROUND:
     * Android 14 hides the "Start now" button from rootInActiveWindow.
     * Solution: Use getWindows() in accessibility service to get dialog window root.
     * 
     * @param rootNode Root accessibility node (from dialog window)
     * @param service Accessibility service instance (for gesture dispatch fallback)
     * @return true if button found and clicked, false otherwise
     */
    fun tryAutoClick(rootNode: AccessibilityNodeInfo?, service: android.accessibilityservice.AccessibilityService?): Boolean {
        // CRITICAL: Check if auto-click is enabled (gating mechanism)
        if (!isAutoClickEnabled()) {
            Log.d(TAG, "🚫 Auto-click not enabled, ignoring dialog")
            return false
        }
        
        if (rootNode == null) {
            Log.w(TAG, "⚠️ Root node is null")
            return false
        }
        
        try {
            // Log ALL nodes (including non-clickable) to see button structure
            Log.d(TAG, "📋 Scanning all nodes in dialog...")
            
            if (rootNode.childCount == 0) {
                Log.w(TAG, "⚠️ Root node has NO children!")
                Log.d(TAG, "  Root: class=${rootNode.className}, text='${rootNode.text}', clickable=${rootNode.isClickable}")
                
                // Try to get bounds to see if node is valid
                val bounds = android.graphics.Rect()
                rootNode.getBoundsInScreen(bounds)
                Log.d(TAG, "  Root bounds: $bounds")
            } else {
                Log.d(TAG, "✅ Root node has ${rootNode.childCount} children")
                logAllNodes(rootNode, 0)
            }
            
            // Search for button with matching text
            val buttonNode = findButtonByText(rootNode, BUTTON_TEXTS)
            
            if (buttonNode != null) {
                val buttonText = buttonNode.text?.toString() ?: buttonNode.contentDescription?.toString() ?: "unknown"
                Log.d(TAG, "🎯 Found button: '$buttonText'")
                
                // Perform click action
                val clicked = buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                if (clicked) {
                    Log.d(TAG, "✅ Auto-clicked MediaProjection permission button")
                    disableAutoClick()
                    return true
                } else {
                    Log.w(TAG, "⚠️ Click action failed")
                }
            } else {
                Log.d(TAG, "🔍 'Start now' button not found, trying parent container click...")
                
                // ANDROID 14 WORKAROUND: "Start now" button is completely hidden
                // Strategy: Find Cancel button's parent (button container) and click it
                // The parent should handle the click and trigger the positive action
                val cancelButton = findCancelButton(rootNode)
                
                if (cancelButton != null) {
                    Log.d(TAG, "✅ Found Cancel button")
                    
                    val parent = cancelButton.parent
                    if (parent != null) {
                        Log.d(TAG, "✅ Found parent container")
                        
                        val parentBounds = android.graphics.Rect()
                        parent.getBoundsInScreen(parentBounds)
                        
                        Log.d(TAG, "  Parent bounds: $parentBounds")
                        Log.d(TAG, "  Parent class: ${parent.className}")
                        Log.d(TAG, "  Parent clickable: ${parent.isClickable}")
                        Log.d(TAG, "  Parent childCount: ${parent.childCount}")
                        
                        // ANDROID 14 WORKAROUND: Try multiple strategies
                        
                        // Strategy 1: Find and focus the positive button (if it exists but hidden)
                        // Then use performAction(ACTION_CLICK) which might be more legitimate
                        Log.d(TAG, "🎯 Strategy 1: Searching for hidden positive button in parent")
                        val positiveButton = findPositiveButtonInParent(parent)
                        
                        if (positiveButton != null) {
                            Log.d(TAG, "✅ Found positive button, trying focus + click")
                            
                            // Try to focus the button first
                            positiveButton.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            
                            // Small delay for focus to take effect
                            Thread.sleep(50)
                            
                            // Now click
                            val clicked = positiveButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            
                            if (clicked) {
                                Log.d(TAG, "✅ Clicked positive button via focus")
                                disableAutoClick()
                                return true
                            } else {
                                Log.w(TAG, "⚠️ Positive button click failed")
                            }
                        }
                        
                        // Strategy 2: Try clicking parent container directly
                        if (parent.isClickable) {
                            Log.d(TAG, "🎯 Strategy 2: Trying ACTION_CLICK on parent")
                            val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            
                            if (clicked) {
                                Log.d(TAG, "✅ Clicked parent container")
                                disableAutoClick()
                                return true
                            } else {
                                Log.w(TAG, "⚠️ Parent click failed")
                            }
                        }
                        
                        // Strategy 3: Calculate position relative to Cancel button
                        // "Start now" is typically to the right of Cancel
                        if (service != null) {
                            val cancelBounds = android.graphics.Rect()
                            cancelButton.getBoundsInScreen(cancelBounds)
                            
                            // Click to the right of Cancel button (where Start now should be)
                            val buttonWidth = cancelBounds.width()
                            val clickX = cancelBounds.right + (buttonWidth / 2) // Center of next button
                            val clickY = cancelBounds.centerY()
                            
                            Log.d(TAG, "🎯 Strategy 3: Gesture relative to Cancel button ($clickX, $clickY)")
                            Log.d(TAG, "  Cancel bounds: $cancelBounds")
                            Log.d(TAG, "  Calculated click: right + ${buttonWidth / 2}")
                            
                            val clicked = dispatchGestureClick(service, clickX, clickY)
                            
                            if (clicked) {
                                Log.d(TAG, "✅ Gesture dispatched relative to Cancel")
                                disableAutoClick()
                                return true
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ Cancel button has no parent")
                    }
                } else {
                    Log.w(TAG, "⚠️ Cancel button not found")
                }
                
                Log.d(TAG, "❌ All click attempts failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during auto-click", e)
        }
        
        return false
    }
    
    /**
     * Log all nodes for debugging (including non-clickable)
     */
    private fun logAllNodes(node: AccessibilityNodeInfo, depth: Int) {
        try {
            val indent = "  ".repeat(depth)
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val className = node.className?.toString()
            val clickable = node.isClickable
            
            if (text != null || desc != null || className?.contains("Button") == true) {
                Log.d(TAG, "$indent➤ class=$className, text='$text', desc='$desc', clickable=$clickable")
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                logAllNodes(child, depth + 1)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Find positive button (Start now, Allow, etc.) in parent container
     * This button might be hidden from normal tree traversal but still accessible
     */
    private fun findPositiveButtonInParent(parent: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // Search all children for positive button
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                
                val childText = child.text?.toString()
                val childDesc = child.contentDescription?.toString()
                
                // Check if this is a positive button
                if (childText != null && BUTTON_TEXTS.any { it.equals(childText, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found positive button by text: '$childText'")
                    return child
                }
                if (childDesc != null && BUTTON_TEXTS.any { it.equals(childDesc, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found positive button by desc: '$childDesc'")
                    return child
                }
                
                // Recursively search in child
                val result = findPositiveButtonInParent(child)
                if (result != null) {
                    return result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error finding positive button", e)
        }
        
        return null
    }
    
    /**
     * Find Cancel button in node tree
     */
    private fun findCancelButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val nodeText = node.text?.toString()
            val nodeContentDesc = node.contentDescription?.toString()
            
            if (nodeText != null && nodeText.equals("Cancel", ignoreCase = true)) {
                return node
            }
            if (nodeContentDesc != null && nodeContentDesc.equals("Cancel", ignoreCase = true)) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findCancelButton(child)
                if (result != null) {
                    return result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error finding Cancel button", e)
        }
        
        return null
    }
    
    /**
     * Dispatch gesture click at specific coordinates
     */
    private fun dispatchGestureClick(
        service: android.accessibilityservice.AccessibilityService,
        x: Int,
        y: Int
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            return false
        }
        
        try {
            val path = android.graphics.Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            gestureBuilder.addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path,
                    0,
                    150 // Longer duration (150ms) to ensure click is registered
                )
            )
            
            var gestureCompleted = false
            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                    super.onCompleted(gestureDescription)
                    gestureCompleted = true
                    Log.d(TAG, "✅ Gesture completed at ($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "⚠️ Gesture cancelled at ($x, $y)")
                }
            }
            
            val dispatched = service.dispatchGesture(gestureBuilder.build(), callback, null)
            
            if (!dispatched) {
                Log.w(TAG, "⚠️ Failed to dispatch gesture")
            }
            
            return dispatched
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error dispatching gesture", e)
            return false
        }
    }
    
    /**
     * Recursively search for button node with matching text
     */
    private fun findButtonByText(
        node: AccessibilityNodeInfo,
        texts: List<String>
    ): AccessibilityNodeInfo? {
        try {
            // Check if current node matches
            val nodeText = node.text?.toString()
            val nodeContentDesc = node.contentDescription?.toString()
            val nodeClassName = node.className?.toString()
            
            // Log node details for debugging
            if (nodeText != null || nodeContentDesc != null) {
                Log.d(TAG, "🔍 Node: class=$nodeClassName, text='$nodeText', desc='$nodeContentDesc', clickable=${node.isClickable}")
            }
            
            // Check if node is clickable and has matching text
            if (node.isClickable) {
                if (nodeText != null && texts.any { it.equals(nodeText, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found matching clickable node by text: '$nodeText'")
                    return node
                }
                if (nodeContentDesc != null && texts.any { it.equals(nodeContentDesc, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found matching clickable node by contentDesc: '$nodeContentDesc'")
                    return node
                }
            }
            
            // Also check Button class name (even if not marked as clickable)
            if (nodeClassName?.contains("Button") == true) {
                if (nodeText != null && texts.any { it.equals(nodeText, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found matching Button by text: '$nodeText'")
                    return node
                }
                if (nodeContentDesc != null && texts.any { it.equals(nodeContentDesc, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Found matching Button by contentDesc: '$nodeContentDesc'")
                    return node
                }
            }
            
            // Recursively search children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findButtonByText(child, texts)
                if (result != null) {
                    return result
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error searching node", e)
        }
        
        return null
    }
    
    /**
     * Check if current window is MediaProjection permission dialog
     */
    fun isMediaProjectionDialog(packageName: String?, windowTitle: String?): Boolean {
        Log.d(TAG, "🔍 Checking dialog - package: $packageName, title: $windowTitle")
        
        // System UI package hosts the permission dialog
        if (packageName != "com.android.systemui") {
            // On some devices, it might be under different package
            if (packageName != "android") {
                Log.d(TAG, "❌ Not system dialog (package: $packageName)")
                return false
            }
        }
        
        // Check window title for MediaProjection indicators
        val title = windowTitle?.lowercase() ?: ""
        val isMediaProjection = title.contains("screen") || 
               title.contains("capture") || 
               title.contains("projection") ||
               title.contains("record") ||
               title.contains("cast") // Android 14 uses "casting"
        
        if (isMediaProjection) {
            Log.d(TAG, "✅ MediaProjection dialog detected")
        } else {
            Log.d(TAG, "❌ Not MediaProjection dialog (title: $windowTitle)")
        }
        
        return isMediaProjection
    }
}
