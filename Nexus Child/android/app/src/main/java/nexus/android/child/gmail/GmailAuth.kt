@file:Suppress("DEPRECATION")

package nexus.android.child.gmail

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

private const val PREF_NAME = "gmail_secure_prefs"
private const val KEY_CONNECTED = "gmail_connected"
private const val KEY_ACCOUNT_EMAIL = "gmail_account_email"
private const val KEY_LAST_INTERNAL_DATE = "gmail_last_internal_date"

object GmailSecurePrefs {
    private fun createPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun prefs(context: Context): SharedPreferences = createPrefs(context)
}

data class GmailAuthState(
    val isConnected: Boolean,
    val accountEmail: String?,
)

object GmailAuthRepository {
    fun getState(context: Context): GmailAuthState {
        val p = GmailSecurePrefs.prefs(context)
        val connected = p.getBoolean(KEY_CONNECTED, false)
        val email = p.getString(KEY_ACCOUNT_EMAIL, null)
        return GmailAuthState(connected && !email.isNullOrBlank(), email)
    }

    fun isConnected(context: Context): Boolean = getState(context).isConnected

    fun getAccountEmail(context: Context): String? = getState(context).accountEmail

    fun setConnected(context: Context, accountEmail: String) {
        try {
            GmailSecurePrefs.prefs(context).edit {
                putBoolean(KEY_CONNECTED, true)
                    .putString(KEY_ACCOUNT_EMAIL, accountEmail)
            }
            Log.d("GmailAuth", "Gmail connected for account: $accountEmail")
        } catch (e: Exception) {
            Log.e("GmailAuth", "Failed to persist Gmail auth state", e)
        }
    }

    fun clear(context: Context) {
        try {
            GmailSecurePrefs.prefs(context).edit {
                putBoolean(KEY_CONNECTED, false)
                    .remove(KEY_ACCOUNT_EMAIL)
                    .remove(KEY_LAST_INTERNAL_DATE)
            }
            Log.d("GmailAuth", "Cleared Gmail auth state")
        } catch (e: Exception) {
            Log.e("GmailAuth", "Failed to clear Gmail auth state", e)
        }
    }
}

object GmailSyncStateStore {
    fun getLastInternalDate(context: Context): Long {
        return try {
            GmailSecurePrefs.prefs(context).getLong(KEY_LAST_INTERNAL_DATE, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun updateLastInternalDate(context: Context, value: Long) {
        if (value <= 0L) return
        try {
            GmailSecurePrefs.prefs(context).edit {
                putLong(KEY_LAST_INTERNAL_DATE, value)
            }
        } catch (e: Exception) {
            Log.e("GmailSyncState", "Failed to persist lastInternalDate", e)
        }
    }

    fun reset(context: Context) {
        try {
            GmailSecurePrefs.prefs(context).edit {
                remove(KEY_LAST_INTERNAL_DATE)
            }
        } catch (e: Exception) {
            Log.e("GmailSyncState", "Failed to reset lastInternalDate", e)
        }
    }
}
