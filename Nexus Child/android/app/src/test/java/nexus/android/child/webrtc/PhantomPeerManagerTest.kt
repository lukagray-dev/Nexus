package nexus.android.child.webrtc

import org.junit.Assert.assertNotNull
import org.junit.Test

class PhantomPeerManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.webrtc.PhantomPeerManager", Class.forName("nexus.android.child.webrtc.PhantomPeerManager", false, this::class.java.classLoader))
    }
}
