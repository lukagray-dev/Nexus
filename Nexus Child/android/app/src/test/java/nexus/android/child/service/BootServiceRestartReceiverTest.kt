package nexus.android.child.service

import org.junit.Assert.assertNotNull
import org.junit.Test

class BootServiceRestartReceiverTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.service.BootServiceRestartReceiver", Class.forName("nexus.android.child.service.BootServiceRestartReceiver", false, this::class.java.classLoader))
    }
}
