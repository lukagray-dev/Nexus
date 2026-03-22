package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class DataChannelClientTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.DataChannelClient", Class.forName("nexus.android.child.components.chat.DataChannelClient", false, this::class.java.classLoader))
    }
}
