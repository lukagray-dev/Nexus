package nexus.android.child.components.keyboard

import org.junit.Assert.assertNotNull
import org.junit.Test

class DataChannelClientTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.keyboard.DataChannelClient", Class.forName("nexus.android.child.components.keyboard.DataChannelClient", false, this::class.java.classLoader))
    }
}
