package com.nexus.child.components.keyboard

/**
 * Domain interface for the Keyboard Monitoring & Keystroke Auditing controller.
 *
 * Hey friend! This interface decouples the WebRTC Command Handler from the concrete
 * Android Accessibility event processing logic, facilitating straightforward unit testing
 * with mocked implementations.
 */
interface IKeyboardController {

    /**
     * Checks whether system Accessibility permission is currently granted to Nexus Child.
     */
    fun hasAccessibilityPermission(): Boolean

    /**
     * Returns true if real-time keyboard auditing is actively running.
     */
    fun isMonitoring(): Boolean

    /**
     * Starts listening for keyboard input events, debounces keystrokes, and invokes [onKeystroke]
     * whenever an input sequence or search query is completed.
     */
    fun startMonitoring(onKeystroke: (KeyboardRecord) -> Unit): Boolean

    /**
     * Halts keyboard auditing and detaches from the accessibility service.
     */
    fun stopMonitoring(): Boolean

    /**
     * Returns recent cached keystroke records from the in-memory ring buffer.
     */
    fun getRecentRecords(): List<KeyboardRecord>

    /**
     * Performs complete cleanup of active coroutines, cached buffers, and listeners.
     */
    fun cleanup()
}
