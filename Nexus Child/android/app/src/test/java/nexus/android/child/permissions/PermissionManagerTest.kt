package nexus.android.child.permissions

import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.permissions.PermissionManager", Class.forName("nexus.android.child.permissions.PermissionManager", false, this::class.java.classLoader))
    }
}
