package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class TelegramExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.TelegramExtractor", Class.forName("nexus.android.child.components.chat.TelegramExtractor", false, this::class.java.classLoader))
    }
}
