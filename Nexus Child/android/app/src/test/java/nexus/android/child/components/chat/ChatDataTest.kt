package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatDataTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatPayload", Class.forName("nexus.android.child.components.chat.ChatPayload", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatPayloadData", Class.forName("nexus.android.child.components.chat.ChatPayloadData", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ConversationMeta", Class.forName("nexus.android.child.components.chat.ConversationMeta", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.MessageData", Class.forName("nexus.android.child.components.chat.MessageData", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatJson", Class.forName("nexus.android.child.components.chat.ChatJson", false, this::class.java.classLoader))
    }
}
