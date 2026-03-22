package nexus.android.child.components.screen

import org.junit.Assert.assertNotNull
import org.junit.Test

class SystemAudioCapturerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.screen.SystemAudioCapturer", Class.forName("nexus.android.child.components.screen.SystemAudioCapturer", false, this::class.java.classLoader))
    }
}
