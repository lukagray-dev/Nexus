package com.nexus.child.service

import com.nexus.child.service.scheduler.PersistentJobService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying contract constants and behavior of PersistentJobService.
 */
class PersistentJobServiceTest {

    @Test
    fun testJobIdConstant() {
        assertEquals("Job ID must be 8802", 8802, PersistentJobService.JOB_ID)
    }

    @Test
    fun testPersistentJobServiceInstantiation() {
        val service = PersistentJobService()
        assertNotNull("PersistentJobService must be instantiable", service)
    }
}
