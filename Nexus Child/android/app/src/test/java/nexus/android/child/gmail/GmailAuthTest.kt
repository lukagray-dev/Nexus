package nexus.android.child.gmail

import org.junit.Assert.assertNotNull
import org.junit.Test

class GmailAuthTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailSecurePrefs", Class.forName("nexus.android.child.gmail.GmailSecurePrefs", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailAuthState", Class.forName("nexus.android.child.gmail.GmailAuthState", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailAuthRepository", Class.forName("nexus.android.child.gmail.GmailAuthRepository", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailSyncStateStore", Class.forName("nexus.android.child.gmail.GmailSyncStateStore", false, this::class.java.classLoader))
    }
}
