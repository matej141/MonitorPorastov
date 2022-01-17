package com.android.monitorporastov.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.android.monitorporastov.databinding.ActivityLoginBinding
import android.text.Editable

import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.android.monitorporastov.R


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
     * Nastavenie listnereov pre textboxy a buttony
     */
    private fun setLoginListeners() {
        applyTextChangeListener(binding.loginUsername)
        applyTextChangeListener(binding.loginPassword)
        binding.loginButton.setOnClickListener {
            checkLogin()
        }
        binding.loginRememberUserCredentials
            .setOnCheckedChangeListener { _, isChecked ->
                saveDataBoolean = isChecked

            }
    }

    /**
     * Kontrola správnosti prihlasovacích údajov.
     */
    private fun checkLogin() {
        if (!checkFieldsAreNotEmpty()) {
            return
        }
        if (!checkIfUserNameIsCorrect() || !checkIfPasswordIsCorrect()) {
            showLoginAD()
            return
        }
        // ked vsetko ok:
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

    /**
     * Kontrola, či používateľské meno je správne.
     */
    private fun checkIfUserNameIsCorrect(): Boolean {

        if (binding.loginUsername.editText?.text.toString() == login) {
            return true
        }
        return false
    }

    /**
     * Kontrola, či heslo je správne.
     */
    private fun checkIfPasswordIsCorrect(): Boolean {
        if (binding.loginPassword.editText?.text.toString() == password) {
            return true
        }
        return false
    }

    /**
     * Zobrazenie alert dialogu, ktorý oznamuje, že prihlásenie bolo neúspešné.
     */
    private fun showLoginAD() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.login_ag_title))
            .setMessage(getString(R.string.login_ag_message))
            .setNegativeButton(getString(R.string.ok_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }


}