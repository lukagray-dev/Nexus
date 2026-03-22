package nexus.android.child.components.location

import org.junit.Assert.assertNotNull
import org.junit.Test

class LocationControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.location.LocationController", Class.forName("nexus.android.child.components.location.LocationController", false, this::class.java.classLoader))
    }
}
