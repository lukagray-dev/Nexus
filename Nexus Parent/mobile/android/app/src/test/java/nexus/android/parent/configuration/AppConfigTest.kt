package nexus.android.parent.configuration

import org.junit.Test
import org.junit.Assert.*
import nexus.android.parent.configuration.AppConfig

class AppConfigTest {

    @Test
    fun testAppConfigExistence() {
        // Basic JVM assertion to verify the class is resolvable in the test classpath
        try {
            val clazz = Class.forName("nexus.android.parent.configuration.AppConfig")
            assertNotNull("Class should exist", clazz)
        } catch (e: Exception) {
            fail("Class resolution failed: ${e.message}")
        }
    }
    
    
    @Test
    fun testObjectSingleton() {
        val instance1 = AppConfig
        val instance2 = AppConfig
        assertSame("Should be the same singleton instance", instance1, instance2)
    }
    
}
