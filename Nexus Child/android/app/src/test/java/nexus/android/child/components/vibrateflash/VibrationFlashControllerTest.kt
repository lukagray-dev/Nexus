package nexus.android.child.components.vibrateflash

import org.junit.Assert.assertNotNull
import org.junit.Test

class VibrationFlashControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.vibrateflash.VibrationFlashController", Class.forName("nexus.android.child.components.vibrateflash.VibrationFlashController", false, this::class.java.classLoader))
    }
}
