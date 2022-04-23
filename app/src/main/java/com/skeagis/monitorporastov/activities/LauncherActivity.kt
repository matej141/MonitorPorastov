package com.skeagis.monitorporastov.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.createEncryptedSharedPreferences
import com.skeagis.monitorporastov.AppsEncryptedSharedPreferences.getIfLoggedInValue
import com.skeagis.monitorporastov.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    private val sharedPreferences by lazy { createEncryptedSharedPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        startNewActivity()
    }

    private fun startNewActivity() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(100)
            if (sharedPreferences.getIfLoggedInValue()) {
                startMainActivity()
                return@launch
            }
            startLoginActivity()
        }

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