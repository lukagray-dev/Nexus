package nexus.android.child.gmail

import org.junit.Assert.assertNotNull
import org.junit.Test

class GmailRepositoryTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailRepository", Class.forName("nexus.android.child.gmail.GmailRepository", false, this::class.java.classLoader))
    }
}
