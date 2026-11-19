package com.nexus.child.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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

    companion object {
        private const val TAG = "NexusAccessibility"

        @Volatile
        private var instance: NexusAccessibilityService? = null

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only process events when MediaProjection auto-click is actively armed
        if (!MediaProjectionAutoClickHelper.isAutoClickEnabled()) {
            return
        }

        try {
            val type = event.eventType
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val rootNode = event.source ?: rootInActiveWindow
                MediaProjectionAutoClickHelper.tryAutoClick(rootNode, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling accessibility event: ${e.message}", e)
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
