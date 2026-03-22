package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class WhatsAppExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.WhatsAppExtractor", Class.forName("nexus.android.child.components.chat.WhatsAppExtractor", false, this::class.java.classLoader))
    }
}
