package io.r_a_d.geiravor.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * NickServ password, bouncer PASS, SASL password, client PEM. Never log these values.
 */
class SecretsStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "geiravor_secrets",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun nickservPassword(): String = prefs.getString(NICKSERV, "").orEmpty()

    fun bouncerPass(): String = prefs.getString(BOUNCER_PASS, "").orEmpty()

    fun saslPassword(): String = prefs.getString(SASL_PASSWORD, "").orEmpty()

    fun clientCertPem(): String = prefs.getString(CLIENT_CERT, "").orEmpty()

    fun clientKeyPem(): String = prefs.getString(CLIENT_KEY, "").orEmpty()

    fun setNickservPassword(value: String) {
        prefs.edit().putString(NICKSERV, value).apply()
    }

    fun setBouncerPass(value: String) {
        prefs.edit().putString(BOUNCER_PASS, value).apply()
    }

    fun setSaslPassword(value: String) {
        prefs.edit().putString(SASL_PASSWORD, value).apply()
    }

    fun setClientCertPem(value: String) {
        prefs.edit().putString(CLIENT_CERT, value).apply()
    }

    fun setClientKeyPem(value: String) {
        prefs.edit().putString(CLIENT_KEY, value).apply()
    }

    companion object {
        private const val NICKSERV = "nickserv_password"
        private const val BOUNCER_PASS = "bouncer_pass"
        private const val SASL_PASSWORD = "sasl_password"
        private const val CLIENT_CERT = "client_cert_pem"
        private const val CLIENT_KEY = "client_key_pem"
    }
}
