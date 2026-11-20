package com.nexus.child.components.applock

/**
 * Interface contract for App Lock monitoring and telemetry management.
 *
 * Hey friend! This interface isolates the domain logic of App Lock telemetry
 * from the underlying transport layer (WebRTC DataChannel), making unit testing
 * and mock simulation simple and clean!
 */
interface IAppLockController {

    /**
     * Activates live monitoring session and flushes historical offline attempts.
     */
    fun startMonitoring()

    /**
     * Deactivates the active live monitoring session.
     */
    fun stopMonitoring()

    /**
     * Returns whether the controller is actively monitoring for the parent.
     */
    fun isMonitoringActive(): Boolean

    /**
     * Records an unlock attempt. Persists to repository and sends live if monitoring is active.
     */
    fun recordAttempt(record: AppLockRecord)

    /**
     * Retrieves all recorded unlock attempts from the repository.
     */
    fun getAllRecords(): List<AppLockRecord>

    /**
     * Wipes all recorded unlock attempts and cached selfies.
     */
    fun clearRecords()

    /**
     * Retrieves the current operational status of the lock system.
     */
    fun getStatus(): AppLockStatus

    /**
     * Cleans up resources when the background service terminates.
     */
    fun cleanup()
}
