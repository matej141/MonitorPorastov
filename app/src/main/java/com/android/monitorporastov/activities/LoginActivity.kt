package com.android.monitorporastov.activities

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.monitorporastov.AppsEncryptedSharedPreferences.PREFS_PASSWORD_KEY
import com.android.monitorporastov.AppsEncryptedSharedPreferences.PREFS_REMEMBER_CREDENTIALS_KEY
import com.android.monitorporastov.AppsEncryptedSharedPreferences.PREFS_STAY_LOGGED_IN_KEY
import com.android.monitorporastov.AppsEncryptedSharedPreferences.PREFS_USERNAME_KEY
import com.android.monitorporastov.AppsEncryptedSharedPreferences.createEncryptedSharedPreferences
import com.android.monitorporastov.AppsEncryptedSharedPreferences.getSavedPasswordCharArray
import com.android.monitorporastov.AppsEncryptedSharedPreferences.getSavedUsernameCharArray
import com.android.monitorporastov.ConnectionLiveData
import com.android.monitorporastov.R
import com.android.monitorporastov.Utils.afterTextChanged
import com.android.monitorporastov.Utils.createErrorMessageAD
import com.android.monitorporastov.Utils.noNetworkAvailable
import com.android.monitorporastov.activities.viewmodels.LoginActivityViewModel
import com.android.monitorporastov.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.net.InetAddress


class LoginActivity : AppCompatActivity() {
    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginActivityViewModel by viewModels()
    private val encryptedSharedPreferences by lazy {
        createEncryptedSharedPreferences(applicationContext)
    }
    private lateinit var connectionLiveData: ConnectionLiveData


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpConnectionLiveData()
        setUpObservers()
        setLoginListeners()
        loadData()
        setExistingValuesFromViewModel()
        setEditableValuesFromViewModel()
    }

    private fun setUpObservers() {
        observeLoadingValue()
        observeErrorMessage()
        observeNetworkStatus()
    }

    private fun observeLoadingValue() {
        viewModel.loading.observe(this) { loadingValue ->
            binding.progressBar.visibility = if (loadingValue) View.VISIBLE else View.GONE
        }
    }

    private fun observeErrorMessage() {
        viewModel.errorMessage.observe(this) { errorMessage ->
            //connectionLiveData.checkInternet()
            if (isOnline(this)) {
                if (errorMessage != null) {
                    createErrorMessageAD(this, errorMessage)
                    viewModel.clearErrorMessage()
                }
            }
            else {
                showNoInternetToastMessage()
            }
        }
    }

    // https://stackoverflow.com/questions/51141970/check-internet-connectivity-android-in-kotlin

    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    return true
                }
            }
        }
        return false
    }



    private fun waitAWhile() {
        runBlocking {
            launch {
                delay(1000L)
            }
        }
    }

    private fun observeNetworkStatus() {
        viewModel.isNetworkAvailable.observe(this) { isAvailable ->
            if (!isAvailable) {
                noNetworkAvailable(this)
            }
        }
    }

    /**
     * Ak sú uložené prihlasovacie údaje, načítajú sa z pamäte a vypíšu do textboxov.
     */
    private fun loadData() {
        if (!checkIfRememberedCredentials()) {
            return
        }
        val userNameCharArray =
            encryptedSharedPreferences.getSavedUsernameCharArray()
        val passwordCharArray =
            encryptedSharedPreferences.getSavedPasswordCharArray()
        if (userNameCharArray != null && passwordCharArray != null) {
            binding.loginUsername.editText?.setText(userNameCharArray, 0, userNameCharArray.size)
            binding.loginPassword.editText?.setText(passwordCharArray, 0, passwordCharArray.size)
        }
        binding.loginRememberUserCredentials.isChecked = true
    }

    private fun checkIfRememberedCredentials(): Boolean {
        return encryptedSharedPreferences.getBoolean(PREFS_REMEMBER_CREDENTIALS_KEY, false)
    }

    /**
     * Nastavenie listnerov pre textboxy a buttony
     */
    private fun setLoginListeners() {
        setTextInputLayoutListeners()
        setSwitchersListeners()
        setLoginButtonListener()
    }

    private fun setTextInputLayoutListeners() {
        setTextChangeListeners()
        setErrorTextChaneListeners()
    }

    private fun setTextChangeListeners() {
        binding.loginUsername.editText?.apply {
            afterTextChanged {
                viewModel.setUsernameEditable(it)
            }
        }

        binding.loginPassword.editText?.apply {
            afterTextChanged {
                viewModel.setPasswordEditable(it)
            }
        }
    }

    private fun setErrorTextChaneListeners() {
        applyErrorTextChangeListener(binding.loginUsername)
        applyErrorTextChangeListener(binding.loginPassword)
    }

    private fun setSwitchersListeners() {
        setLoginRememberUserCredentialsListener()
        setLoginStayLoggedListener()
    }

    private fun setLoginRememberUserCredentialsListener() {
        binding.loginRememberUserCredentials
            .setOnCheckedChangeListener { _, isChecked ->
                viewModel.rememberCredentials(isChecked)
            }
    }

    private fun setLoginStayLoggedListener() {
        binding.loginStayLoggedIn
            .setOnCheckedChangeListener { _, isChecked ->
                viewModel.stayLoggedIn(isChecked)
            }
    }

    private fun setLoginButtonListener() {
        binding.loginButton.setOnClickListener {
            prepareForLogin()
        }
    }

    private fun prepareForLogin() {
        if (!checkIfNetworkAvailable()) {
            showNoInternetToastMessage()
            return
        }
        if (!checkFieldsAreNotEmpty()) {
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            doLogin()
        }

    }

    private fun checkIfNetworkAvailable(): Boolean {
        return viewModel.isNetworkAvailable.value == true
    }

    private fun showNoInternetToastMessage() {
        Toast.makeText(this,
            getString(R.string.must_be_password_before_login),
            Toast.LENGTH_SHORT).show()
    }

    private fun doLogin() {
        CoroutineScope(Dispatchers.Main).launch {
            val successfulLogin = viewModel.doLogin()
            val errorOccurred = viewModel.errorOccurred.value
            if (errorOccurred == true) {
                return@launch
            }
            if (successfulLogin) onSuccessfulLogin() else showUnsuccessfulLoginAD()
        }
    }

    private fun onSuccessfulLogin() {
        viewModel.setLoading(true)
        saveData()
        startMainActivity()
    }

    /**
     * Uloženie prihlasovacích údajov do pamäte.
     */
    private fun saveData() {
        encryptedSharedPreferences.edit().apply {
            putString(PREFS_USERNAME_KEY, getUsernameEditText().toString())
            putString(PREFS_PASSWORD_KEY, getPasswordEditText().toString())
            viewModel.rememberCredentials.value?.let {
                putBoolean(PREFS_REMEMBER_CREDENTIALS_KEY,
                    it)
            }
            viewModel.stayLoggedIn.value?.let { putBoolean(PREFS_STAY_LOGGED_IN_KEY, it) }
        }.apply()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun getUsernameEditText(): Editable? = binding.loginUsername.editText?.text

    private fun getPasswordEditText(): Editable? = binding.loginPassword.editText?.text

    /**
     * Aplikuje pre textboxy TextChangeListener. Ak totiž používateľ nezadal meno alebo heslo,
     * textboxy sa podsvietia červenou farbou, errorom. Akonáhle opäť začne písať, textboxy sa
     * zobrazia v štandardnom formáte.
     */
    private fun applyErrorTextChangeListener(textInputLayout: TextInputLayout) {
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
            binding.loginUsername.error = getString(R.string.no_username_added)
            return false
        }
        return true
    }

    /**
     * Kontrola, či používateľ vyplnil meno.
     */
    private fun checkIfPasswordNotEmpty(): Boolean {
        if (binding.loginPassword.editText?.length() ?: 0 == 0) {
            binding.loginPassword.error = getString(R.string.no_password_added)
            return false
        }
        return true
    }

    /**
     * Zobrazenie alert dialogu, ktorý oznamuje, že prihlásenie bolo neúspešné.
     */
    private fun showUnsuccessfulLoginAD() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.login_ag_title))
            .setMessage(getString(R.string.login_ag_message))
            .setNegativeButton(getString(R.string.ok_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun setUpConnectionLiveData() {
        connectionLiveData = ConnectionLiveData(this)
        setUpConnectionStatusReceiver()
    }

    private fun setUpConnectionStatusReceiver() {
        connectionLiveData.observe(this) {
            viewModel.setNetworkAvailability(it)
        }
    }

    private fun setExistingValuesFromViewModel() {
        setEditableValuesFromViewModel()
        setBooleanValuesFromViewModel()
    }

    private fun setEditableValuesFromViewModel() {
        binding.loginUsername.editText?.text = viewModel.usernameEditable.value
        binding.loginPassword.editText?.text = viewModel.passwordEditable.value
    }

    private fun setBooleanValuesFromViewModel() {
        binding.loginRememberUserCredentials.isChecked =
            viewModel.rememberCredentials.value == true
        binding.loginStayLoggedIn.isChecked = viewModel.stayLoggedIn.value == true
    }

}