package com.nexus.child.service

import com.nexus.child.service.watchdog.WatchdogService
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying instantiation and contract of WatchdogService.
 */
class WatchdogServiceTest {

    @Test
    fun testWatchdogServiceInstantiation() {
        val service = WatchdogService()
        assertNotNull("WatchdogService must be instantiable", service)
    }

    @Test
    fun testWatchdogServiceBinder() {
        val service = WatchdogService()
        val binder = service.WatchdogBinder()
        assertNotNull("WatchdogBinder must return service instance", binder.getService())
    }
}
