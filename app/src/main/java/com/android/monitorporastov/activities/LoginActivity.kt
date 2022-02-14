package com.android.monitorporastov.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.android.monitorporastov.R
import com.android.monitorporastov.UserData
import com.android.monitorporastov.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException


class LoginActivity : AppCompatActivity() {
    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!
    private val login = "android"
    private val password = "demo"
    private val userNameKey = "UserName"
    private val passwordKey = "PasswordKey"
    private val saveDataKey = "SaveDataKey"
    private var saveDataBoolean = false
    private lateinit var sharedPreferences: SharedPreferences
    private val unauthorisedCode = 401;

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginBinding.inflate(layoutInflater)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            applicationContext)
        setContentView(binding.root)
        setLoginListeners()
        loadData()
    }

    /**
     * Ak sú uložené prihlasovacie údaje, načítajú sa z pamäte a vypísu do textboxov.
     */
    private fun loadData() {
        val userNameText = sharedPreferences.getString(userNameKey, "")
        val passwordText = sharedPreferences.getString(passwordKey, "")
        val saveDataBooleanPrefs = sharedPreferences.getBoolean(saveDataKey, false)
        if (userNameText.isNullOrEmpty() || passwordText.isNullOrEmpty()) {
            return
        }
        binding.loginUsername.editText?.setText(userNameText)
        binding.loginPassword.editText?.setText(passwordText)
        saveDataBoolean = saveDataBooleanPrefs
        binding.loginRememberUserCredentials.isChecked = saveDataBoolean
    }

    /**
     * Uloženie prihlasovacích údajov do pamäte.
     */
    private fun saveData() {
        // https://stackoverflow.com/questions/34499395/sharedpreferences-not-working-saving-data-from-edittext-field
        var userNameText = ""
        var passwordText = ""
        if (saveDataBoolean) {
            userNameText = binding.loginUsername.editText?.text.toString()
            passwordText = binding.loginPassword.editText?.text.toString()
        }
        val editor = sharedPreferences.edit()
        editor.putString(userNameKey, userNameText)
        editor.putString(passwordKey, passwordText)
        editor.putBoolean(saveDataKey, saveDataBoolean)
        editor.apply()
    }

    /**
     * Nastavenie listnerov pre textboxy a buttony
     */
    private fun setLoginListeners() {
        applyTextChangeListener(binding.loginUsername)
        applyTextChangeListener(binding.loginPassword)
        binding.loginButton.setOnClickListener {
            if (checkFieldsAreNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                   performLogin()
                }
            }


        }
        binding.loginRememberUserCredentials
            .setOnCheckedChangeListener { _, isChecked ->
                saveDataBoolean = isChecked

            }
    }


    private fun loginRequest(): Request {
        val userNameText = binding.loginUsername.editText?.text.toString()
        val passwordText = CharArray(binding.loginPassword.editText?.text!!.length)
        binding.loginPassword.editText?.text!!.getChars(0,
            binding.loginPassword.editText?.text!!.length, passwordText, 0)
        val credential = Credentials.basic(userNameText, String(passwordText))
        return Request.Builder()
            .url("http://services.skeagis.sk:7492/geoserver/rest")
            .addHeader("Authorization", credential)
            .build()
    }

    private suspend fun performLogin() {
        val request = loginRequest()
        val client = OkHttpClient()
        val res = CompletableDeferred<Boolean>()

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.VISIBLE
            }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // runOnUiThread { showLoginWarningAD() }
                }

                override fun onResponse(call: Call, response: Response) {
                    CoroutineScope(Dispatchers.Main).launch {

                        binding.progressBar.visibility = View.GONE
                    }
                    response.body()?.close()
                    if (response.code() == unauthorisedCode) {

                        res.complete(false)
                        return
                    }

                    res.complete(true)
                }
            })


        }
        val ret = res.await()
        if (!ret) {
            showLoginWarningAD()
            return
        }
        saveData()
        val userNameText = binding.loginUsername.editText?.text.toString()
        val passwordText = CharArray(binding.loginPassword.editText?.text!!.length)
        binding.loginPassword.editText?.text!!.getChars(0,
            binding.loginPassword.editText?.text!!.length, passwordText, 0)
        UserData.username = userNameText
        UserData.password = passwordText
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun perform() {
        saveData()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Aplikuje pre textboxy TextChangeListener. Ak totiž používateľ nezadal meno alebo heslo,
     * textboxy sa podsvietia červenou farbou, errorom. Akonáhle opäť začne písať, textboxy sa
     * zobrazia v štandardnom formáte.
     */
    private fun applyTextChangeListener(textInputLayout: TextInputLayout) {
        textInputLayout.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (textInputLayout.isErrorEnabled) {
                    textInputLayout.isErrorEnabled = false
                }
            }
        })
    }

    // kontrola údajov

    /**
     * Kontrola, či údaje sú vyplnené.
     */
    private fun checkFieldsAreNotEmpty(): Boolean {
        val check1 = checkIfUserNameNotEmpty()
        val check2 = checkIfPasswordNotEmpty()
        return check1 && check2
    }

    /**
     * Kontrola, či používateľ vyplnil meno.
     */
    private fun checkIfUserNameNotEmpty(): Boolean {
        if (binding.loginUsername.editText?.length() ?: 0 == 0) {
            binding.loginUsername.error = "Nezadali ste prihlasovacie meno"
            return false
        }
        return true
    }

    /**
     * Kontrola, či používateľ vyplnil meno.
     */
    private fun checkIfPasswordNotEmpty(): Boolean {
        if (binding.loginPassword.editText?.length() ?: 0 == 0) {
            binding.loginPassword.error = "Nezadali ste heslo"
            return false
        }
        return true
    }


    private fun checkRest(urlString: String): Boolean {
        var success = true
//        try {
//
//
//
//            val c: HttpURLConnection = URL(urlString).openConnection() as HttpURLConnection
//            val userCredentials = "${binding.loginUsername.editText?.text.toString()}:" +
//                    binding.loginUsername.editText?.text.toString()}
//            val basicAuth =
//                "Basic " + String(Base64.getEncoder().encode(userCredentials.toByteArray()))
//            c.setRequestProperty("Authorization", basicAuth)
//            val inputStream: InputStream = c.inputStream
//            inputStream.close()
//            c.disconnect()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            success = false
//        }
        val client = OkHttpClient()
        val credential = Credentials.basic(binding.loginUsername.editText?.text.toString(),
            binding.loginUsername.editText?.text.toString())
        val request = Request.Builder()
            .url(urlString)
            .addHeader("Authorization", credential)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                success = false
            }

            override fun onResponse(call: Call, response: Response) {
                println(response.body()?.string())
                if (!response.isSuccessful) {
                    success = false
                }
            }
        })

        return success
    }


    private suspend fun checkLoginCredentials(): Boolean = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = View.VISIBLE
        }
        val checkRest = checkRest("http://212.5.204.126:7492/geoserver/rest")
        withContext(Dispatchers.Main) {
            binding.progressBar.visibility = View.GONE
        }
        checkRest
    }

    /**
     * Zobrazenie alert dialogu, ktorý oznamuje, že prihlásenie bolo neúspešné.
     */
    private fun showLoginWarningAD() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.login_ag_title))
            .setMessage(getString(R.string.login_ag_message))
            .setNegativeButton(getString(R.string.ok_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }


}


