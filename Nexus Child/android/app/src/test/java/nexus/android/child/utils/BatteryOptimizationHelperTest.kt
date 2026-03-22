package nexus.android.child.utils

import org.junit.Assert.assertNotNull
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test
    fun moduleSymbols_shouldBeResolvableWithoutStaticInitialization() {
        assertNotNull("Class should resolve: nexus.android.child.utils.BatteryOptimizationHelper", Class.forName("nexus.android.child.utils.BatteryOptimizationHelper", false, this::class.java.classLoader))
    }
}
