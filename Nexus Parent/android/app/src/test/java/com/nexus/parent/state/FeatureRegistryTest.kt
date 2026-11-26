package com.nexus.parent.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FeatureRegistryTest.kt - Verifies the 18-feature sidebar taxonomy and Child ID utilities.
 */
class FeatureRegistryTest {

    @Test
    fun `registry exposes exactly 4 sections and 18 unique features matching desktop`() {
        assertEquals(4, FeatureRegistry.sections.size)
        val allItems = FeatureRegistry.sections.flatMap { it.items }
        assertEquals(18, allItems.size)
        assertEquals(18, allItems.toSet().size)

        assertEquals(4, FeatureRegistry.sections[0].items.size) // Live Surveillance
        assertEquals(5, FeatureRegistry.sections[1].items.size) // Communications
        assertEquals(5, FeatureRegistry.sections[2].items.size) // Social & AI Activity
        assertEquals(4, FeatureRegistry.sections[3].items.size) // Device Security & Controls
    }

    @Test
    fun `child id sanitization and formatting enforces 12 numeric digits`() {
        assertEquals("482194021104", FeatureRegistry.sanitizeChildIdDigits("4821-9402-1104-999"))
        assertEquals("4821 9402 1104", FeatureRegistry.formatChildId("482194021104"))
        assertTrue(FeatureRegistry.isValidChildId("482194021104"))
        assertFalse(FeatureRegistry.isValidChildId("48219402110"))
        assertFalse(FeatureRegistry.isValidChildId("abc482194021"))
    }

    @Test
    fun `filterSections matches case-insensitively and omits empty sections`() {
        val camResult = FeatureRegistry.filterSections("camera")
        assertEquals(1, camResult.size)
        assertEquals(SidebarSectionId.LIVE, camResult.first().id)
        assertEquals(listOf(FeatureId.CAMERA), camResult.first().items)

        val aiResult = FeatureRegistry.filterSections("CLAUDE")
        assertEquals(1, aiResult.size)
        assertEquals(listOf(FeatureId.CLAUDE), aiResult.first().items)

        val noMatch = FeatureRegistry.filterSections("nonexistent-module-xyz")
        assertTrue(noMatch.isEmpty())
    }
}
