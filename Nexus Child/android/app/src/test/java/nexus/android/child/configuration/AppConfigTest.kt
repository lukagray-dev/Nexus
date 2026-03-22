package nexus.android.child.configuration

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppConfigTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.configuration.AppConfig", Class.forName("nexus.android.child.configuration.AppConfig", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.configuration.AppConfig\$Firebase", Class.forName("nexus.android.child.configuration.AppConfig\$Firebase", false, this::class.java.classLoader))
        assertNotNull("Class should resolve: nexus.android.child.configuration.AppConfig\$WebRTC", Class.forName("nexus.android.child.configuration.AppConfig\$WebRTC", false, this::class.java.classLoader))
    }
}
