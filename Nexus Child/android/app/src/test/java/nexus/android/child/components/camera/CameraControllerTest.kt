package nexus.android.child.components.camera

import org.junit.Assert.assertNotNull
import org.junit.Test

class CameraControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.camera.CameraController", Class.forName("nexus.android.child.components.camera.CameraController", false, this::class.java.classLoader))
    }
}
