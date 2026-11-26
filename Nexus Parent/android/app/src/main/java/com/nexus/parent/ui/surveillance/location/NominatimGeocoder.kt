package com.nexus.parent.ui.surveillance.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * NominatimGeocoder.kt - Reverse geocoding helper matching Desktop `map-manager.ts`.
 *
 * Hey friend! Converts raw `(latitude, longitude)` coordinates into human-readable street/city
 * names using OpenStreetMap's free Nominatim endpoint with a 2-second rate-limit throttle and
 * 4-decimal grid caching (`~11m`) so repeated GPS fixes near the same building don't spam HTTP calls.
 */
class NominatimGeocoder(
    private val minIntervalMs: Long = 2_000L
) {
    private val cache = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastRequestAtMs: Long = 0L

    companion object {
        fun buildCacheKey(lat: Double, lng: Double): String {
            return String.format(Locale.US, "%.4f,%.4f", lat, lng)
        }

        fun formatAddressFromNominatimJson(rawJson: String): String? {
            return runCatching {
                val root = JSONObject(rawJson)
                val addr = root.optJSONObject("address") ?: return@runCatching null

                val parts = mutableListOf<String>()
                val poi = listOf("amenity", "shop", "building", "office")
                    .firstNotNullOfOrNull { key -> addr.optString(key).takeIf { it.isNotBlank() } }
                if (poi != null) parts.add(poi)

                val road = addr.optString("road").takeIf { it.isNotBlank() }
                if (road != null) parts.add(road)

                val suburb = addr.optString("suburb").takeIf { it.isNotBlank() }
                if (suburb != null && suburb !in parts) parts.add(suburb)

                val locality = listOf("city", "town", "village")
                    .firstNotNullOfOrNull { key -> addr.optString(key).takeIf { it.isNotBlank() } }
                if (locality != null) parts.add(locality)

                if (parts.isNotEmpty()) {
                    parts.joinToString(", ")
                } else {
                    val displayName = root.optString("display_name")
                    if (displayName.isNotBlank()) {
                        displayName.split(',').take(3).joinToString(",") { it.trim() }
                    } else {
                        "Known Location"
                    }
                }
            }.getOrNull()
        }
    }

    suspend fun resolveAddress(lat: Double, lng: Double): String? {
        val key = buildCacheKey(lat, lng)
        cache[key]?.let { return it }

        val now = System.currentTimeMillis()
        if (now - lastRequestAtMs < minIntervalMs) {
            return null
        }
        lastRequestAtMs = now

        return withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = String.format(
                    Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1",
                    lat,
                    lng
                )
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept-Language", "en")
                    setRequestProperty("User-Agent", "NexusParentAndroid/1.0")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val formatted = formatAddressFromNominatimJson(body) ?: return@runCatching null
                    cache[key] = formatted
                    formatted
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
}
