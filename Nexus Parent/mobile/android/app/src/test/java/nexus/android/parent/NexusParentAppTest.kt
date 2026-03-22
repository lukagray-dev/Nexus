package nexus.android.parent

import org.junit.Test
import org.junit.Assert.*

class NexusParentAppTest {

    @Test
    fun testNexusParentAppExistence() {
        // Basic JVM assertion to verify the class is resolvable in the test classpath
        try {
            val clazz = Class.forName("nexus.android.parent.NexusParentApp")
            assertNotNull("Class should exist", clazz)
        } catch (e: Exception) {
            fail("Class resolution failed: ${e.message}")
        }
    }
}
