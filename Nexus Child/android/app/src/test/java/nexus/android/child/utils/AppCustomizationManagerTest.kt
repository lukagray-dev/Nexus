package nexus.android.child.utils

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppCustomizationManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.utils.AppCustomizationManager", Class.forName("nexus.android.child.utils.AppCustomizationManager", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.utils.AppCustomizationManager\$IconType", Class.forName("nexus.android.child.utils.AppCustomizationManager\$IconType", false, this::class.java.classLoader))
    }
}
