package com.nexus.child.components.callalert

/**
 * CallAlertEvent represents a live cellular call-state transition or repeating reminder
 * transmitted from the Android Child device to the Desktop Parent console.
 */
data class CallAlertEvent(
    val state: String,
    val direction: String,
    val number: String = "",
    val contactName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRepeat: Boolean = false
)

/**
 * Contract for monitoring live phone call activity (incoming ringing, answered, outgoing, ended)
 * and dispatching repeating parent alerts while a call remains active.
 */
interface ICallAlertController {
    fun hasPhoneStatePermission(): Boolean
    fun setAlertEnabled(enabled: Boolean)
    fun isAlertEnabled(): Boolean
    fun startMonitoring(): Boolean
    fun stopMonitoring(): Boolean
    fun cleanup()
}
