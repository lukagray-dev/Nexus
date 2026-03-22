package nexus.android.child.utils

import org.junit.Assert.assertNotNull
import org.junit.Test

class AccessibilityStateTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.utils.AccessibilityState", Class.forName("nexus.android.child.utils.AccessibilityState", false, this::class.java.classLoader))
    }
}
