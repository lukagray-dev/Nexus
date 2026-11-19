package com.nexus.child.components.stealth

/**
 * Interface defining stealth mode operations for Nexus Child.
 *
 * Hey friend! In Android, "Stealth Mode" means hiding the application icon
 * from the user's home screen launcher and app drawer so the monitoring agent
 * is unobtrusive while continuous background protection continues running.
 *
 * This interface defines operations to:
 * - Query current stealth status (hidden vs. visible).
 * - Enable or disable stealth mode via PackageManager.
 * - Toggle stealth mode on or off.
 * - Register callbacks to listen for state changes (e.g. triggered by widget or parent).
 */
interface IStealthController {

    /**
     * Checks if stealth mode is currently active (i.e. launcher component is disabled).
     *
     * @return true if the app is hidden from the launcher; false if visible.
     */
    fun isStealthMode(): Boolean

    /**
     * Activates or deactivates stealth mode by toggling launcher component visibility.
     *
     * @param enable true to hide the app from launcher; false to make it visible.
     * @return true if the component state was updated successfully; false otherwise.
     */
    fun setStealthMode(enable: Boolean): Boolean

    /**
     * Toggles the current stealth mode state.
     *
     * @return The new stealth state (true if now hidden; false if now visible).
     */
    fun toggleStealthMode(): Boolean

    /**
     * Registers a listener callback invoked whenever stealth mode state transitions.
     *
     * @param listener Callback receiving the updated boolean state (true = hidden, false = visible).
     */
    fun setOnStateChangeListener(listener: ((isStealth: Boolean) -> Unit)?)
}
