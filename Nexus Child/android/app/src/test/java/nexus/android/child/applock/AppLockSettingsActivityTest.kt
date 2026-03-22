package nexus.android.child.applock

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppLockSettingsActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.applock.AppLockSettingsActivity", Class.forName("nexus.android.child.applock.AppLockSettingsActivity", false, this::class.java.classLoader))
    }
}
