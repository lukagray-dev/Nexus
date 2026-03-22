package nexus.android.child.service

import org.junit.Assert.assertNotNull
import org.junit.Test

class PersistentJobServiceTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.service.PersistentJobService", Class.forName("nexus.android.child.service.PersistentJobService", false, this::class.java.classLoader))
    }
}
