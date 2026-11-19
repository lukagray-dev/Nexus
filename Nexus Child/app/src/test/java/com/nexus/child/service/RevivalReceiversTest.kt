package com.nexus.child.service

import android.content.Intent
import com.nexus.child.service.receiver.BootReceiver
import com.nexus.child.service.receiver.ServiceRestartReceiver
import com.nexus.child.service.receiver.UnlockReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying contract actions for BootReceiver, UnlockReceiver, and ServiceRestartReceiver.
 */
class RevivalReceiversTest {

    @Test
    fun testServiceRestartReceiverAction() {
        assertEquals(
            "Service restart action must match contract",
            "com.nexus.child.action.RESTART_SERVICE",
            ServiceRestartReceiver.ACTION_RESTART
        )
    }

    @Test
    fun testBootReceiverInstantiation() {
        val receiver = BootReceiver()
        assertNotNull("BootReceiver should be instantiable", receiver)
    }

    @Test
    fun testUnlockReceiverInstantiation() {
        val receiver = UnlockReceiver()
        assertNotNull("UnlockReceiver should be instantiable", receiver)
    }

    @Test
    fun testServiceRestartReceiverInstantiation() {
        val receiver = ServiceRestartReceiver()
        assertNotNull("ServiceRestartReceiver should be instantiable", receiver)
    }
}
