package nexus.android.parent

import org.junit.Test
import org.junit.Assert.*

class SplashActivityTest {

    @Test
    fun testSplashActivityExistence() {
        // Basic JVM assertion to verify the class is resolvable in the test classpath
        try {
            val clazz = Class.forName("nexus.android.parent.SplashActivity")
            assertNotNull("Class should exist", clazz)
        } catch (e: Exception) {
            fail("Class resolution failed: ${e.message}")
        }
    }
}
