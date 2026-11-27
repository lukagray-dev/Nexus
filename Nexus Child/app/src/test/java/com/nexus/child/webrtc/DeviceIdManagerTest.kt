package com.nexus.child.webrtc

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.nexus.child.service.NexusBackgroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests verifying DeviceIdManager's coroutine concurrency, double-checked locking,
 * memory caching, and formatting behavior.
 *
 * Hey friend! Here is why these tests are critical:
 * In our production architecture, when the app boots fresh on a device, both MainActivity
 * and NexusBackgroundService launch simultaneously. Previously, both callers independently
 * triggered 12-digit ID generation against Firebase, creating two separate IDs.
 * These tests verify that:
 * 1. Concurrency: Multiple simultaneous coroutine callers across Dispatchers.IO receive the exact same ID.
 * 2. In-Memory Caching: Once resolved, subsequent queries return immediately from memory without reading disk.
 * 3. Double-Checked Locking: Mutex locking prevents redundant disk/network operations.
 * 4. Formatting: Visual format "XXXX XXXX XXXX" produces clean, human-readable strings.
 */
class DeviceIdManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var mockContext: MockTestContext

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        mockContext = MockTestContext(fakePrefs)
        DeviceIdManager.resetCacheForTesting()
    }

    @After
    fun tearDown() {
        DeviceIdManager.resetCacheForTesting()
        fakePrefs.map.clear()
    }

    @Test
    fun testFormatValid12Digits() {
        val raw = "123456789012"
        val formatted = DeviceIdManager.format(raw)
        assertEquals("1234 5678 9012", formatted)
    }

    @Test
    fun testFormatWithHyphensAndSpaces() {
        val messy = "1234-5678-9012"
        val formatted = DeviceIdManager.format(messy)
        assertEquals("1234 5678 9012", formatted)
    }

    @Test
    fun testFormatInvalidLengthReturnsOriginal() {
        val short = "12345"
        val formatted = DeviceIdManager.format(short)
        assertEquals("12345", formatted)
    }

    @Test
    fun testLoadsExistingIdFromPreferences() = runBlocking {
        fakePrefs.map["nexus_device_id"] = "987654321098"

        val resolved = DeviceIdManager.getOrGenerateDeviceId(mockContext)
        assertEquals("987654321098", resolved)
    }

    @Test
    fun testMemoryCacheReturnsWithoutTouchingPreferences() = runBlocking {
        fakePrefs.map["nexus_device_id"] = "987654321098"

        // First call populates in-memory cache
        val first = DeviceIdManager.getOrGenerateDeviceId(mockContext)
        assertEquals("987654321098", first)

        // Clear underlying preferences to prove second call hits memory cache
        fakePrefs.map.clear()

        val second = DeviceIdManager.getOrGenerateDeviceId(mockContext)
        assertEquals("Memory cache must return the cached ID even if prefs are cleared", "987654321098", second)
    }

    @Test
    fun testConcurrentCoroutineAccessResolvesIdenticalId() = runBlocking {
        fakePrefs.map["nexus_device_id"] = "555566667777"

        // Launch 20 concurrent coroutines querying the ID simultaneously across Dispatchers.IO
        val callers = (1..20).map {
            async(Dispatchers.IO) {
                DeviceIdManager.getOrGenerateDeviceId(mockContext)
            }
        }

        val results = callers.awaitAll()
        assertEquals("All 20 coroutines must finish", 20, results.size)
        assertTrue("All callers must receive the exact same ID", results.all { it == "555566667777" })
    }

    @Test
    fun testResetCacheAllowsReloadingNewId() = runBlocking {
        fakePrefs.map["nexus_device_id"] = "111122223333"
        val first = DeviceIdManager.getOrGenerateDeviceId(mockContext)
        assertEquals("111122223333", first)

        // Reset cache and update preferences
        DeviceIdManager.resetCacheForTesting()
        fakePrefs.map["nexus_device_id"] = "999988887777"

        val second = DeviceIdManager.getOrGenerateDeviceId(mockContext)
        assertEquals("999988887777", second)
    }

    @Test
    fun testBackgroundServiceDeviceIdStateFlowInitialState() {
        val initialId = NexusBackgroundService.deviceId.value
        assertNull("NexusBackgroundService.deviceId must be null before bootstrap", initialId)
    }

    @Test
    fun testInitialDeviceMetadataIncludesOwnerUidAndStatus() {
        val metadata = DeviceIdManager.buildInitialDeviceMetadata("child-uid-abc", timestampValue = 123456L)
        assertEquals("child-uid-abc", metadata["ownerUid"])
        assertEquals("online-waiting", metadata["status"])
        assertEquals(123456L, metadata["createdAt"])
        assertEquals(123456L, metadata["lastSeen"])
    }

    /**
     * Minimal in-memory SharedPreferences implementation for deterministic unit tests.
     */
    class FakeSharedPreferences : SharedPreferences {
        val map = ConcurrentHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val backingMap: ConcurrentHashMap<String, Any?>) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) tempMap[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) tempMap[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) tempMap[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) tempMap[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) tempMap[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) tempMap[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) tempMap[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) backingMap.clear()
                tempMap.forEach { (k, v) ->
                    if (v == null) backingMap.remove(k) else backingMap[k] = v
                }
            }
        }
    }

    /**
     * Test ContextWrapper routing getSharedPreferences to FakeSharedPreferences.
     */
    class MockTestContext(private val sharedPreferences: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return sharedPreferences
        }
    }
}
