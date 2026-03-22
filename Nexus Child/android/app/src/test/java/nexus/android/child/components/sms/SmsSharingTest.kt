package nexus.android.child.components.sms

import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsSharingTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.sms.SmsSharing", Class.forName("nexus.android.child.components.sms.SmsSharing", false, this::class.java.classLoader))
    }
}
