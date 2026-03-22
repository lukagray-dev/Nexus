package nexus.android.parent.auth

import org.junit.Assert.assertNotNull
import org.junit.Test

class AuthManagerTest {

    @Test
    fun testAuthManagerClassResolvesWithoutInitialization() {
        val clazz = Class.forName(
            "nexus.android.parent.auth.AuthManager",
            false,
            this::class.java.classLoader
        )
        assertNotNull("AuthManager class should be resolvable", clazz)
    }
}
