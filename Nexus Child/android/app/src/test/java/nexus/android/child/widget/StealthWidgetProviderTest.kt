package nexus.android.child.widget

import org.junit.Assert.assertNotNull
import org.junit.Test

class StealthWidgetProviderTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.widget.StealthWidgetProvider", Class.forName("nexus.android.child.widget.StealthWidgetProvider", false, this::class.java.classLoader))
    }
}
