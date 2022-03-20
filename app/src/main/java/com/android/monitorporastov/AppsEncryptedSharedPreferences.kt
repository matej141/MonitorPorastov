package com.android.monitorporastov

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AppsEncryptedSharedPreferences {
    private const val ENCRYPTED_PREFS_FILE = "monitor_porastov_encrypted_storage.txt"

    const val PREFS_USERNAME_KEY = "UserNameKey"
    const val PREFS_PASSWORD_KEY = "PasswordKey"
    const val PREFS_REMEMBER_CREDENTIALS_KEY = "RememberCredentialsKey"
    const val PREFS_STAY_LOGGED_IN_KEY = "StayLoggedInKey"

    // https://medium.com/android-club/store-data-securely-encryptedsharedpreferences-bff71ac39a55
    fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun SharedPreferences.getSavedUsernameCharArray(): CharArray? {
        return getString(PREFS_USERNAME_KEY, "")?.toCharArray()
    }

    fun SharedPreferences.getSavedPasswordCharArray(): CharArray? {
        return getString(PREFS_PASSWORD_KEY, "")?.toCharArray()
    }

    fun SharedPreferences.getIfLoggedInValue(): Boolean {
        return getBoolean(PREFS_STAY_LOGGED_IN_KEY,
            false)
    }

    fun SharedPreferences.getIfRememberCredentialsValue(): Boolean {
        return getBoolean(PREFS_REMEMBER_CREDENTIALS_KEY,
            false)
    }

    fun SharedPreferences.setLoggedInValue(value: Boolean) {
        edit().putBoolean(PREFS_STAY_LOGGED_IN_KEY, value).apply()
    }

}

