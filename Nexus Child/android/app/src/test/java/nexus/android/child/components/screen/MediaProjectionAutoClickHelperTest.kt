package nexus.android.child.components.screen

import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaProjectionAutoClickHelperTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.screen.MediaProjectionAutoClickHelper", Class.forName("nexus.android.child.components.screen.MediaProjectionAutoClickHelper", false, this::class.java.classLoader))
    }
}
