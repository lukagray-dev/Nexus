package nexus.android.child.components.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboardMonitorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.KeyboardMonitor", Class.forName("nexus.android.child.components.keyboard.KeyboardMonitor", false, this::class.java.classLoader))
    }
}
