package com.android.monitorporastov.activities.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.monitorporastov.GeoserverService
import com.android.monitorporastov.RetroService
import kotlinx.coroutines.*

class LoginActivityViewModel : ViewModel() {
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> get() = _loading
    private val _rememberCredentials = MutableLiveData<Boolean>()
    val rememberCredentials: LiveData<Boolean> get() = _rememberCredentials
    private val _stayLoggedIn = MutableLiveData<Boolean>()
    val stayLoggedIn: LiveData<Boolean> get() = _stayLoggedIn

    private val _usernameEditable = MutableLiveData<Editable>()
    val usernameEditable: LiveData<Editable> get() = _usernameEditable

    private val _passwordEditable = MutableLiveData<Editable>()
    val passwordEditable: LiveData<Editable> get() = _passwordEditable

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val _errorOccurred = MutableLiveData<Boolean>()
    val errorOccurred: LiveData<Boolean> get() = _errorOccurred
    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    val isNetworkAvailable: LiveData<Boolean> get() = _isNetworkAvailable

    companion object {
        private const val LOGIN_TAG = "LOGIN"
        private const val unauthorisedCode = 401
    }

    fun rememberCredentials(value: Boolean) {
        _rememberCredentials.value = value
    }

    fun stayLoggedIn(value: Boolean) {
        _stayLoggedIn.value = value
    }

    fun setUsernameEditable(editable: Editable) {
        _usernameEditable.value = editable
    }

    fun setPasswordEditable(editable: Editable) {
        _passwordEditable.value = editable
    }

    fun setNetworkAvailability(value: Boolean) {
        _isNetworkAvailable.value = value
    }

    private fun getService(): GeoserverService {
        val okHttpClient = RetroService.createOkHttpClient(_usernameEditable.value.toString(),
            _passwordEditable.value.toString())
        return RetroService.getServiceWithScalarsFactory(okHttpClient)
    }

    suspend fun doLogin(): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        setLoading(true)
        setErrorOccurred(false)
        val service = getService()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.login()
                when {
                    response.isSuccessful -> {
                        Log.d(LOGIN_TAG, "Login was successful")
                        deferredBoolean.complete(true)
                    }
                    response.code() == unauthorisedCode -> {
                        Log.d(LOGIN_TAG, "Unauthorised error")
                        deferredBoolean.complete(false)
                    }
                    else -> {
                        Log.d(LOGIN_TAG, "Error: ${response.message()}")
                        onError(response.message())
                        setErrorOccurred(true)
                        deferredBoolean.complete(false)
                    }
                }
            }
            catch (e: java.net.UnknownHostException) {
                setErrorOccurred(true)
                deferredBoolean.complete(false)
            }
            catch (e: Exception) {
                onError(e.toString())
                setErrorOccurred(true)
                deferredBoolean.complete(false)
            }
            finally {
                setLoading(false)
            }
        }
        return deferredBoolean.await()
    }

    private fun onError(message: String) {
        if (_isNetworkAvailable.value == true) {
            _errorMessage.postValue(message)
        }
    }

    private fun setErrorOccurred(value: Boolean) {
        _errorOccurred.postValue(value)
    }

    fun setLoading(value: Boolean) {
        _loading.postValue(value)

    }
}