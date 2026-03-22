package nexus.android.child.components.chat

import org.junit.Assert.assertNotNull
import org.junit.Test

class SnapchatExtractorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.chat.SnapchatExtractor", Class.forName("nexus.android.child.components.chat.SnapchatExtractor", false, this::class.java.classLoader))
    }
}
