package nexus.android.child.customization

import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeUIActivityTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.customization.FakeUIActivity", Class.forName("nexus.android.child.customization.FakeUIActivity", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.customization.FakeUIActivity\$LongPressGestureListener", Class.forName("nexus.android.child.customization.FakeUIActivity\$LongPressGestureListener", false, this::class.java.classLoader))
    }
}
