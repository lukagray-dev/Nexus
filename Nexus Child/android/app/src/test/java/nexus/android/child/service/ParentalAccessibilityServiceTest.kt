package nexus.android.child.service

import org.junit.Assert.assertNotNull
import org.junit.Test

class ParentalAccessibilityServiceTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.service.ParentalAccessibilityService", Class.forName("nexus.android.child.service.ParentalAccessibilityService", false, this::class.java.classLoader))
    }
}
