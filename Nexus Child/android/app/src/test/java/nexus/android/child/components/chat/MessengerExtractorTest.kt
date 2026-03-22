package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class MessengerExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.MessengerExtractor", Class.forName("nexus.android.child.components.chat.MessengerExtractor", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.components.chat.FacebookExtractor", Class.forName("nexus.android.child.components.chat.FacebookExtractor", false, this::class.java.classLoader))
    }
}
