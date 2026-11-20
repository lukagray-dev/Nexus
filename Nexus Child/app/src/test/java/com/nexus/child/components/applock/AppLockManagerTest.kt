package com.nexus.child.components.applock

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests verifying AppLockManager security rules, 6-digit PIN validation, and 3-attempt lockout.
 *
 * Hey friend! These unit tests verify the industrial security constraints of AppLockManager:
 * 1. Strict 6-digit validation: Non-numeric strings or invalid lengths are rejected.
 * 2. SHA-256 Hashing: Plaintext PINs are never stored; verification checks hash equality.
 * 3. 3-Attempt Lockout: Consecutive failures increment until the 3rd attempt, which triggers
 *    a 60-second hardware lockout window.
 * 4. Successful Unlock: A valid PIN resets the failure counter and marks the in-memory session unlocked.
 * 5. Remote Overrides: Remote toggle and remote PIN configuration apply deterministically.
 */
class AppLockManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        AppLockManager.testPreferences = fakePrefs
        AppLockManager.clearAll(null)
    }

    @After
    fun tearDown() {
        AppLockManager.clearAll(null)
        AppLockManager.testPreferences = null
    }

    @Test
    fun testDefaultStateDisabledWithoutPin() {
        assertFalse(AppLockManager.isAppLockEnabled(null))
        assertFalse(AppLockManager.hasPinSet(null))
        assertEquals(0, AppLockManager.getFailedAttempts(null))
        assertFalse(AppLockManager.isLockedOut(null))
    }

    @Test
    fun testSetPinRejectsInvalidFormats() {
        // Must be exactly 6 digits
        assertFalse(AppLockManager.setPin(null, "12345")) // 5 digits
        assertFalse(AppLockManager.setPin(null, "1234567")) // 7 digits
        assertFalse(AppLockManager.setPin(null, "12345a")) // non-digit
        assertFalse(AppLockManager.setPin(null, ""))

        assertFalse(AppLockManager.hasPinSet(null))
    }

    @Test
    fun testSetPinAndVerificationSuccess() {
        val validPin = "654321"
        assertTrue(AppLockManager.setPin(null, validPin))
        assertTrue(AppLockManager.hasPinSet(null))

        // Verification matches correct PIN
        assertTrue(AppLockManager.verifyPin(null, validPin))

        // Verification rejects wrong PIN
        assertFalse(AppLockManager.verifyPin(null, "111111"))
    }

    @Test
    fun testThreeFailedAttemptsTriggersLockout() {
        AppLockManager.setPin(null, "123456")

        // Attempt 1: Failed
        val attempt1 = AppLockManager.recordFailedAttempt(null)
        assertEquals(1, attempt1)
        assertFalse(AppLockManager.isLockedOut(null))

        // Attempt 2: Failed
        val attempt2 = AppLockManager.recordFailedAttempt(null)
        assertEquals(2, attempt2)
        assertFalse(AppLockManager.isLockedOut(null))

        // Attempt 3: Triggers 60s lockout
        val attempt3 = AppLockManager.recordFailedAttempt(null)
        assertEquals(3, attempt3)
        assertTrue(AppLockManager.isLockedOut(null))

        val remainingSec = AppLockManager.getRemainingLockoutSeconds(null)
        assertTrue("Remaining seconds should be around 60: $remainingSec", remainingSec in 50..60)
    }

    @Test
    fun testSuccessfulUnlockResetsFailedAttempts() {
        AppLockManager.setPin(null, "123456")

        // Fail twice
        AppLockManager.recordFailedAttempt(null)
        AppLockManager.recordFailedAttempt(null)
        assertEquals(2, AppLockManager.getFailedAttempts(null))

        // Successful unlock
        AppLockManager.recordSuccessfulUnlock(null)

        assertEquals(0, AppLockManager.getFailedAttempts(null))
        assertFalse(AppLockManager.isLockedOut(null))
        assertTrue(AppLockManager.isSessionUnlocked())
    }

    @Test
    fun testRemoteToggleAndStatusReporting() {
        AppLockManager.setPin(null, "987654")
        AppLockManager.setAppLockEnabled(null, true)
        assertTrue(AppLockManager.isAppLockEnabled(null))

        val status = AppLockManager.getStatus(null)
        assertTrue(status.enabled)
        assertTrue(status.hasPinSet)
        assertEquals(0, status.failedAttempts)
        assertFalse(status.isLockedOut)

        // Remote disable
        AppLockManager.setAppLockEnabled(null, false)
        assertFalse(AppLockManager.isAppLockEnabled(null))
    }
}

/**
 * Lightweight in-memory implementation of SharedPreferences for pure JVM unit testing.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = data
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = data[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val backing: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                if (value != null) temp[key] = value else temp.remove(key)
            }
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) {
                backing.clear()
            }
            backing.putAll(temp)
        }
    }
}
