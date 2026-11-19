package com.nexus.child.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the behavior of BatteryOptimizationManager across various device vendors.
 */
class BatteryOptimizationManagerTest {

    @Test
    fun testIsPopularOemWithAggressiveTaskKiller() {
        // Known aggressive OEM vendors should return true
        assertTrue("Xiaomi should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("Xiaomi"))
        assertTrue("Redmi should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("redmi"))
        assertTrue("Samsung should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("SAMSUNG"))
        assertTrue("Huawei should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("Huawei"))
        assertTrue("Oppo should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("OPPO"))
        assertTrue("Vivo should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("vivo"))
        assertTrue("OnePlus should be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("OnePlus"))

        // Stock Android / non-aggressive vendors should return false
        assertFalse("Google Pixel should not be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("Google"))
        assertFalse("Motorola should not be flagged as aggressive OEM", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller("Motorola"))
        assertFalse("Empty string should not be flagged", BatteryOptimizationManager.isPopularOemWithAggressiveTaskKiller(""))
    }
}
