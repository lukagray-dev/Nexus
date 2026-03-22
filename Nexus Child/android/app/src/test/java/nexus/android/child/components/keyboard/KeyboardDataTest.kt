package nexus.android.child.components.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboardDataTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.KeyboardPayload", Class.forName("nexus.android.child.components.keyboard.KeyboardPayload", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.KeyboardData", Class.forName("nexus.android.child.components.keyboard.KeyboardData", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.KeyboardJson", Class.forName("nexus.android.child.components.keyboard.KeyboardJson", false, this::class.java.classLoader))
    }
}
