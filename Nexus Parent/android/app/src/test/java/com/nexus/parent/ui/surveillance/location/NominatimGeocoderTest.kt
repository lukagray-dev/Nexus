package com.nexus.parent.ui.surveillance.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * NominatimGeocoderTest.kt - Verifies reverse geocoding cache key rounding and JSON address formatting.
 */
class NominatimGeocoderTest {

    @Test
    fun `formats structured Nominatim address and quantized cache keys matching Desktop`() {
        assertEquals("22.5726,88.3639", NominatimGeocoder.buildCacheKey(22.572645, 88.363892))

        val richJson = """
            {
              "display_name": "Victoria Memorial, Queen's Way, Maidan, Kolkata, West Bengal",
              "address": {
                "building": "Victoria Memorial",
                "road": "Queen's Way",
                "suburb": "Maidan",
                "city": "Kolkata"
              }
            }
        """.trimIndent()
        assertEquals(
            "Victoria Memorial, Queen's Way, Maidan, Kolkata",
            NominatimGeocoder.formatAddressFromNominatimJson(richJson)
        )

        val fallbackJson = """
            {
              "display_name": "Salt Lake Sector V, Bidhannagar, North 24 Parganas, West Bengal",
              "address": {}
            }
        """.trimIndent()
        assertEquals(
            "Salt Lake Sector V,Bidhannagar,North 24 Parganas",
            NominatimGeocoder.formatAddressFromNominatimJson(fallbackJson)
        )

        assertNull(NominatimGeocoder.formatAddressFromNominatimJson("invalid json"))
    }
}
