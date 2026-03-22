package nexus.android.parent.configuration

import org.junit.Test
import org.junit.Assert.*

class SupabaseConfigTest {

    @Test
    fun testSupabaseConfigExistence() {
        val clazz = Class.forName(
            "nexus.android.parent.configuration.SupabaseConfig",
            false,
            this::class.java.classLoader
        )
        assertNotNull("SupabaseConfig class should be resolvable", clazz)
    }
}
