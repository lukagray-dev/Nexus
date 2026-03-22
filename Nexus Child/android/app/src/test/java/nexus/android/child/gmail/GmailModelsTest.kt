package nexus.android.child.gmail

import org.junit.Assert.assertNotNull
import org.junit.Test

class GmailModelsTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailEmail", Class.forName("nexus.android.child.gmail.GmailEmail", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailBatchPayload", Class.forName("nexus.android.child.gmail.GmailBatchPayload", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.gmail.GmailJson", Class.forName("nexus.android.child.gmail.GmailJson", false, this::class.java.classLoader))
    }
}
