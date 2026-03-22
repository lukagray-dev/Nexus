package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatControllerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatController", Class.forName("nexus.android.child.components.chat.ChatController", false, this::class.java.classLoader))
    }
}
