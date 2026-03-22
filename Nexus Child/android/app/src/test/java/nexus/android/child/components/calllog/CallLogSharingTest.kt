package nexus.android.child.components.calllog

import org.junit.Assert.assertNotNull
import org.junit.Test

class CallLogSharingTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.calllog.CallLogSharing", Class.forName("nexus.android.child.components.calllog.CallLogSharing", false, this::class.java.classLoader))
    }
}
