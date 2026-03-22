package nexus.android.child.applock

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppLockActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.applock.AppLockActivity", Class.forName("nexus.android.child.applock.AppLockActivity", false, this::class.java.classLoader))
    }
}
