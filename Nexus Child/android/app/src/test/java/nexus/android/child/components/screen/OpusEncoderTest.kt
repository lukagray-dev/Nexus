package nexus.android.child.components.screen

import org.junit.Assert.assertNotNull
import org.junit.Test

class OpusEncoderTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.screen.OpusEncoder", Class.forName("nexus.android.child.components.screen.OpusEncoder", false, this::class.java.classLoader))
    }
}
