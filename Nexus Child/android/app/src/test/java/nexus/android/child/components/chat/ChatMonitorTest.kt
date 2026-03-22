package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatMonitorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatMonitor", Class.forName("nexus.android.child.components.chat.ChatMonitor", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.ChatMonitor\$ConversationState", Class.forName("nexus.android.child.components.chat.ChatMonitor\$ConversationState", false, this::class.java.classLoader))
    }
}
