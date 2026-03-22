package nexus.android.child.components.screen

import org.junit.Assert.assertNotNull
import org.junit.Test

class ScreenRecordingControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.screen.ScreenRecordingController", Class.forName("nexus.android.child.components.screen.ScreenRecordingController", false, this::class.java.classLoader))
    }
}
