package nexus.android.child.signaling

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DeviceStatusManager.
 * Tests device presence and heartbeat management.
 */
class DeviceStatusManagerTest {

    @Before
    fun setup() {
        // Intentionally empty: constructor triggers Firebase/Looper initialization
        // which belongs to integration tests, not JVM unit tests.
    }

    @Test
    fun testStatusConstants_haveCorrectValues() {
        // Verify all status constants are defined
        assertEquals("online", DeviceStatusManager.ONLINE)
        assertEquals("online-waiting", DeviceStatusManager.ONLINE_WAITING)
        assertEquals("online-background-waiting", DeviceStatusManager.ONLINE_BACKGROUND_WAITING)
        assertEquals("connecting", DeviceStatusManager.CONNECTING)
        assertEquals("connected", DeviceStatusManager.CONNECTED)
        assertEquals("disconnected", DeviceStatusManager.DISCONNECTED)
        assertEquals("offline", DeviceStatusManager.OFFLINE)
    }

    @Test
    fun testStatusConstants_areUnique() {
        // Verify all status constants are unique
        val statuses = setOf(
            DeviceStatusManager.ONLINE,
            DeviceStatusManager.ONLINE_WAITING,
            DeviceStatusManager.ONLINE_BACKGROUND_WAITING,
            DeviceStatusManager.CONNECTING,
            DeviceStatusManager.CONNECTED,
            DeviceStatusManager.DISCONNECTED,
            DeviceStatusManager.OFFLINE
        )
        assertEquals(7, statuses.size)
    }

    @Test
    fun testDeviceStatusManager_typeResolvable() {
        assertNotNull(
            Class.forName("nexus.android.child.signaling.DeviceStatusManager", false, this::class.java.classLoader)
        )
    }
}
