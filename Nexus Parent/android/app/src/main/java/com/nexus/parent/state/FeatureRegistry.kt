package com.nexus.parent.state

/**
 * FeatureRegistry.kt - Canonical 18-feature sidebar taxonomy and input formatting helpers.
 *
 * Hey friend! This mirrors `nexusSections` in `Nexus Parent/desktop/src/ts/left-sidebar/sidebar.ts`
 * so Android and Desktop share the exact same 4 monitoring categories and 18 feature entries.
 */
data class SidebarSectionSpec(
    val id: SidebarSectionId,
    val title: String,
    val items: List<FeatureId>
)

object FeatureRegistry {

    val sections: List<SidebarSectionSpec> = listOf(
        SidebarSectionSpec(
            id = SidebarSectionId.LIVE,
            title = "Live Surveillance",
            items = listOf(
                FeatureId.CAMERA,
                FeatureId.MIC,
                FeatureId.SCREEN,
                FeatureId.LOCATION
            )
        ),
        SidebarSectionSpec(
            id = SidebarSectionId.COMMS,
            title = "Communications",
            items = listOf(
                FeatureId.SMS,
                FeatureId.CALLLOG,
                FeatureId.NOTIFICATION,
                FeatureId.KEYBOARD,
                FeatureId.STORAGE
            )
        ),
        SidebarSectionSpec(
            id = SidebarSectionId.APPS,
            title = "Social & AI Activity",
            items = listOf(
                FeatureId.WHATSAPP,
                FeatureId.TELEGRAM,
                FeatureId.CHATGPT,
                FeatureId.CLAUDE,
                FeatureId.GROK
            )
        ),
        SidebarSectionSpec(
            id = SidebarSectionId.CONTROLS,
            title = "Device Security & Controls",
            items = listOf(
                FeatureId.APPLOCK,
                FeatureId.VIBRATEFLASH,
                FeatureId.WALLPAPER,
                FeatureId.WELLBEING
            )
        )
    )

    /**
     * Strips any non-digit characters and caps Child ID input at 12 digits.
     */
    fun sanitizeChildIdDigits(rawInput: String): String {
        return rawInput.filter { it.isDigit() }.take(12)
    }

    /**
     * Formats a 12-digit Child ID into grouped 4-digit blocks for comfortable reading.
     */
    fun formatChildId(rawDigits: String): String {
        val clean = sanitizeChildIdDigits(rawDigits)
        return clean.chunked(4).joinToString(" ")
    }

    /**
     * Returns true when exactly 12 numeric digits are present.
     */
    fun isValidChildId(rawDigits: String): Boolean {
        return sanitizeChildIdDigits(rawDigits).length == 12
    }

    /**
     * Filters sections by search query; hides sections that have zero matching features.
     */
    fun filterSections(query: String): List<SidebarSectionSpec> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return sections
        return sections.mapNotNull { section ->
            val matching = section.items.filter { item ->
                item.sidebarLabel.lowercase().contains(q) ||
                    item.title.lowercase().contains(q)
            }
            if (matching.isEmpty()) null else section.copy(items = matching)
        }
    }
}
