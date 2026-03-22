package nexus.android.child.components.screen

import org.junit.Assert.assertNotNull
import org.junit.Test

class TransparentMediaProjectionActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.screen.TransparentMediaProjectionActivity", Class.forName("nexus.android.child.components.screen.TransparentMediaProjectionActivity", false, this::class.java.classLoader))
    }
}
