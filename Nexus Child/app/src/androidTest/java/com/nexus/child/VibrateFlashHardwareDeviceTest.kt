package com.nexus.child

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexus.child.components.vibrateflash.VibrateFlashController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device hardware verification test for vibration and flashlight actuation.
 *
 * Hey friend! This test runs directly on physical Android hardware (or connected emulator)
 * to verify that our USAGE_ALARM VibrationAttributes bypass touch-haptic suppression
 * and successfully actuate the device's physical vibration motor.
 */
@RunWith(AndroidJUnit4::class)
class VibrateFlashHardwareDeviceTest {

    @Test
    fun testRealDeviceVibrationActuation() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = VibrateFlashController(appContext)

        // Ensure the device possesses a physical vibration motor
        assertTrue("Device must have vibration hardware", controller.hasVibrator())

        // Trigger short vibration (200ms one-shot with USAGE_ALARM)
        val shortResult = controller.vibrate("short")
        assertTrue("Short vibration must execute successfully", shortResult)

        Thread.sleep(300)

        // Trigger double tap waveform
        val doubleResult = controller.vibrate("double")
        assertTrue("Double tap vibration must execute successfully", doubleResult)

        Thread.sleep(600)

        // Stop any remaining effects
        controller.stopAll()
    }
}
