package com.android.monitorporastov.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.monitorporastov.AppsEncryptedSharedPreferences.createEncryptedSharedPreferences
import com.android.monitorporastov.AppsEncryptedSharedPreferences.getIfLoggedInValue
import com.android.monitorporastov.R

class LauncherActivity : AppCompatActivity() {

    private val sharedPreferences by lazy { createEncryptedSharedPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        startNewActivity()
    }

    private fun startNewActivity() {
        if (sharedPreferences.getIfLoggedInValue()) {
            startMainActivity()
            return
        }
        startLoginActivity()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}