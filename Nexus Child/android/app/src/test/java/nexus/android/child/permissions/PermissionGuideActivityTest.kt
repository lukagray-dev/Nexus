package nexus.android.child.permissions

import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionGuideActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.permissions.PermissionGuideActivity", Class.forName("nexus.android.child.permissions.PermissionGuideActivity", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.permissions.PermissionGuideType", Class.forName("nexus.android.child.permissions.PermissionGuideType", false, this::class.java.classLoader))
    }
}
