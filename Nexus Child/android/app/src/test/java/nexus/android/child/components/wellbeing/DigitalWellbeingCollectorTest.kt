package nexus.android.child.components.wellbeing

import org.junit.Assert.assertNotNull
import org.junit.Test

class DigitalWellbeingCollectorTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.components.wellbeing.DigitalWellbeingCollector", Class.forName("nexus.android.child.components.wellbeing.DigitalWellbeingCollector", false, this::class.java.classLoader))
    }
}
