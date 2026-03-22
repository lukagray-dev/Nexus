package nexus.android.child.signaling

import org.junit.Assert.assertNotNull
import org.junit.Test

class SignalingClientTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.signaling.SignalingClient", Class.forName("nexus.android.child.signaling.SignalingClient", false, this::class.java.classLoader))
    }
}
