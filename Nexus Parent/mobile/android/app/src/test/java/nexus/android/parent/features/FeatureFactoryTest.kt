package nexus.android.parent.features

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import nexus.android.parent.managers.FeatureType

class FeatureFactoryTest {

    @Before
    fun setUp() {
        // Clear cache before each test
        FeatureFactory.clearCache()
    }

    @After
    fun tearDown() {
        FeatureFactory.clearCache()
    }

    @Test
    fun testFactorySingletonObjectResolves() {
        val clazz = Class.forName(
            "nexus.android.parent.features.FeatureFactory",
            false,
            this::class.java.classLoader
        )
        assertNotNull("FeatureFactory object should be resolvable", clazz)
    }

    @Test
    fun testGetFeatureReturnsNullForEmptyCache() {
        assertNull("Cache should be empty before feature creation", FeatureFactory.getFeature(FeatureType.LOCATION))
    }

    @Test
    fun testClearFeatureOnMissingEntryDoesNotThrow() {
        FeatureFactory.clearFeature(FeatureType.SCREEN)
        assertNull("Missing feature should remain absent", FeatureFactory.getFeature(FeatureType.SCREEN))
    }

    @Test
    fun testClearCacheOnEmptyStateDoesNotThrow() {
        FeatureFactory.clearCache()
        assertNull("Cache should remain empty after clear", FeatureFactory.getFeature(FeatureType.MIC))
    }
}
