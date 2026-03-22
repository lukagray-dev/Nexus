package nexus.android.child.ui.views

import org.junit.Assert.assertNotNull
import org.junit.Test

class ParticleBackgroundViewTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.ui.views.Particle", Class.forName("nexus.android.child.ui.views.Particle", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.ui.views.ParticleBackgroundView", Class.forName("nexus.android.child.ui.views.ParticleBackgroundView", false, this::class.java.classLoader))
    }
}
