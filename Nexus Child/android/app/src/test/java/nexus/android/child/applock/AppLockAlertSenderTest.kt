package nexus.android.child.applock

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppLockAlertSenderTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.applock.AppLockAlertSender", Class.forName("nexus.android.child.applock.AppLockAlertSender", false, this::class.java.classLoader))
    }
}
