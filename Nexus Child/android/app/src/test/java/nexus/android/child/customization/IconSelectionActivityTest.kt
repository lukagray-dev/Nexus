package nexus.android.child.customization

import org.junit.Assert.assertNotNull
import org.junit.Test

class IconSelectionActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.customization.IconSelectionActivity", Class.forName("nexus.android.child.customization.IconSelectionActivity", false, this::class.java.classLoader))
    }
}
