package com.nexus.child.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying StealthWidgetProvider class symbols, intent action constants,
 * and reflective component instantiation.
 */
class StealthWidgetProviderTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        val clazz = Class.forName("com.nexus.child.widget.StealthWidgetProvider", false, this::class.java.classLoader)
        assertNotNull("Class should resolve: com.nexus.child.widget.StealthWidgetProvider", clazz)
    }

    @Test
    fun actionConstants_shouldMatchExpectedBroadcastFilter() {
        assertEquals(
            "com.nexus.child.TOGGLE_STEALTH",
            StealthWidgetProvider.ACTION_TOGGLE_STEALTH
        )
    }
}
