package nexus.android.child.applock

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppLockManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.applock.AppLockManager", Class.forName("nexus.android.child.applock.AppLockManager", false, this::class.java.classLoader))
    }
}
