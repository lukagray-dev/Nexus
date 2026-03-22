package nexus.android.child.components.notification

import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.notification.NotificationController", Class.forName("nexus.android.child.components.notification.NotificationController", false, this::class.java.classLoader))
    }
}
