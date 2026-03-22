package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class InstagramExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.InstagramExtractor", Class.forName("nexus.android.child.components.chat.InstagramExtractor", false, this::class.java.classLoader))
    }
}
