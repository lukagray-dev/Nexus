package com.nexus.child.components.vibrateflash

/**
 * Interface defining hardware actuation capabilities for vibration and camera torch/flash.
 *
 * Hey friend! This interface abstracts all device hardware actions so our command handler
 * does not have to worry about low-level Android vibrator services or camera managers directly.
 * It also makes unit testing super easy because we can mock or fake this interface!
 */
interface IVibrateFlashController {

    /**
     * Executes a named vibration waveform pattern.
     *
     * @param pattern Pattern identifier (e.g., "short", "double", "pulse", "sos")
     * @return true if vibration was triggered successfully on device hardware; false otherwise.
     */
    fun vibrate(pattern: String): Boolean

    /**
     * Executes a named camera flashlight action or strobe effect.
     *
     * @param action Flashlight action (e.g., "on", "off", "strobe", "beacon")
     * @return true if camera torch command was dispatched successfully; false otherwise.
     */
    fun flash(action: String): Boolean

    /**
     * Executes a synchronized combined vibration and flashlight pattern.
     *
     * @param pattern Combined pattern identifier (e.g., "alert", "sos", "pulse")
     * @return true if both vibration and flash were triggered successfully; false otherwise.
     */
    fun vibrateAndFlash(pattern: String): Boolean

    /**
     * Immediately halts any ongoing vibration waveforms and turns off the camera flashlight.
     */
    fun stopAll()

    /**
     * Checks if the device has an operational vibration motor.
     */
    fun hasVibrator(): Boolean

    /**
     * Checks if the device camera has an operational flash/torch unit.
     */
    fun hasFlash(): Boolean

    /**
     * Releases active coroutine jobs and system hardware listeners.
     */
    fun cleanup()
}
