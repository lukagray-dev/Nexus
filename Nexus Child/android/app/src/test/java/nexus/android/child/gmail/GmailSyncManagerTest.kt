package nexus.android.child.gmail

import org.junit.Assert.assertNotNull
import org.junit.Test

class GmailSyncManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailSyncManager", Class.forName("nexus.android.child.gmail.GmailSyncManager", false, this::class.java.classLoader))
    }
}
