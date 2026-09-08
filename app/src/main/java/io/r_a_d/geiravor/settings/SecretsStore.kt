package io.r_a_d.geiravor.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** IRC secrets. Never log these values. */
class SecretsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "geiravor_secrets",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(key: String): String = prefs.getString(key, "") ?: ""

    fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
