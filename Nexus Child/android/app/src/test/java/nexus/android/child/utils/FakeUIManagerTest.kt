package nexus.android.child.utils

import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeUIManagerTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.utils.FakeUIManager", Class.forName("nexus.android.child.utils.FakeUIManager", false, this::class.java.classLoader))
    }
}
