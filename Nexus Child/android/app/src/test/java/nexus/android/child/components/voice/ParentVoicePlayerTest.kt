package nexus.android.child.components.voice

import org.junit.Assert.assertNotNull
import org.junit.Test

class ParentVoicePlayerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.voice.ParentVoicePlayer", Class.forName("nexus.android.child.components.voice.ParentVoicePlayer", false, this::class.java.classLoader))
    }
}
