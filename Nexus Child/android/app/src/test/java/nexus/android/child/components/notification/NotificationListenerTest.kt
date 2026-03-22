package nexus.android.child.components.notification

import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationListenerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.notification.NotificationListener", Class.forName("nexus.android.child.components.notification.NotificationListener", false, this::class.java.classLoader))
    }
}
