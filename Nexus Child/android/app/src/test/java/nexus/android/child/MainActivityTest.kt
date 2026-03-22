package nexus.android.child

import org.junit.Assert.assertNotNull
import org.junit.Test

class MainActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.MainActivity", Class.forName("nexus.android.child.MainActivity", false, this::class.java.classLoader))
    }
}
