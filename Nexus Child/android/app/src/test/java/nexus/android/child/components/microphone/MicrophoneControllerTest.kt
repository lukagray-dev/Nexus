package nexus.android.child.components.microphone

import org.junit.Assert.assertNotNull
import org.junit.Test

class MicrophoneControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.microphone.MicrophoneController", Class.forName("nexus.android.child.components.microphone.MicrophoneController", false, this::class.java.classLoader))
    }
}
