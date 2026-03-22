package nexus.android.child.components.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboardControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.KeyboardController", Class.forName("nexus.android.child.components.keyboard.KeyboardController", false, this::class.java.classLoader))
    }
}
