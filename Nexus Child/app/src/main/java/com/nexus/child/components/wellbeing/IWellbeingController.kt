package com.nexus.child.components.wellbeing

/**
 * Contract for Digital Wellbeing monitoring controllers on Nexus Child.
 */
interface IWellbeingController {
    fun hasUsageAccess(): Boolean
    fun startMonitoring(range: WellbeingRange = getCurrentRange()): Boolean
    fun stopMonitoring(): Boolean
    fun refreshNow(range: WellbeingRange? = null): Boolean
    fun setRange(range: WellbeingRange)
    fun getCurrentRange(): WellbeingRange
    fun isMonitoringActive(): Boolean
    fun cleanup()
}
