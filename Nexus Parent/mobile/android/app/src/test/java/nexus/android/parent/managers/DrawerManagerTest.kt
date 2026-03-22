package nexus.android.parent.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerManagerTest {

    @Test
    fun testFeatureTypeEnumsMapCorrectly() {
        assertEquals("CAMERA", FeatureType.CAMERA.name)

        val allTypes = FeatureType.values()
        assertTrue("Contains all expected features", allTypes.size >= 23)
        assertTrue(allTypes.contains(FeatureType.LOCATION))
        assertTrue(allTypes.contains(FeatureType.WHATSAPP))
    }
}
