package nexus.android.child

import org.junit.Assert.assertNotNull
import org.junit.Test

class BackgroundServiceTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.BackgroundService", Class.forName("nexus.android.child.BackgroundService", false, this::class.java.classLoader))
    }
}
