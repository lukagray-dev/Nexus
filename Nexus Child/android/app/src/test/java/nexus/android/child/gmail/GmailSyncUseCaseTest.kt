package nexus.android.child.gmail

import org.junit.Assert.assertNotNull
import org.junit.Test

class GmailSyncUseCaseTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailSyncUseCase", Class.forName("nexus.android.child.gmail.GmailSyncUseCase", false, this::class.java.classLoader))
    }
}
