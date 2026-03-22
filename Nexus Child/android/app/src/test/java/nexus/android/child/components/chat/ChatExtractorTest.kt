package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatExtractor", Class.forName("nexus.android.child.components.chat.ChatExtractor", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.GenericChatExtractor", Class.forName("nexus.android.child.components.chat.GenericChatExtractor", false, this::class.java.classLoader))
    }
}
