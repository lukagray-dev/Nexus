package com.nexus.child.components.telephony

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SimCardManager verifying SIM slot normalization and carrier resolution.
 *
 * Hey friend! These tests ensure that raw database identifiers (like sub_id = 5 or PHONE_ACCOUNT_ID = 2)
 * are properly transformed into clean physical SIM slot labels like "SIM 1" or "SIM 2",
 * completely eliminating cryptic values like "SIM 5" or bare "2" from reaching the parent UI!
 */
class SimCardManagerTest {

    @Test
    fun testNullOrBlankReturnsEmpty() {
        val dummyContext = ContextWrapper(null)
        val manager = SimCardManager(dummyContext)

        assertEquals("", manager.resolveSimLabel(null))
        assertEquals("", manager.resolveSimLabel(""))
        assertEquals("", manager.resolveSimLabel("   "))
    }

    @Test
    fun testNumericFallbackMappingNeverReturnsBareNumber() {
        val dummyContext = ContextWrapper(null)
        val manager = SimCardManager(dummyContext)

        // Raw numbers from old CallLog or SMS must map to clean SIM slots
        assertEquals("SIM 1", manager.resolveSimLabel("0"))
        assertEquals("SIM 1", manager.resolveSimLabel("1"))
        assertEquals("SIM 2", manager.resolveSimLabel("2"))
        // Legacy subId 5 should map to a valid SIM slot, never "SIM 5"
        val resolved5 = manager.resolveSimLabel("5")
        assertTrue(
            "Expected SIM 1 or SIM 2 for subId 5, but got: $resolved5",
            resolved5 == "SIM 1" || resolved5 == "SIM 2"
        )
    }

    @Test
    fun testSimStringNormalization() {
        val dummyContext = ContextWrapper(null)
        val manager = SimCardManager(dummyContext)

        assertEquals("SIM 2", manager.resolveSimLabel("SIM 2"))
        assertEquals("SIM 1", manager.resolveSimLabel("SIM 1"))
    }
}
