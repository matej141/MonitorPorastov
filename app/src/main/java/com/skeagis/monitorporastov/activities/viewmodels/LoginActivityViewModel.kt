package com.skeagis.monitorporastov.activities.viewmodels

import android.text.Editable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.viewmodels.BaseViewModel
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitBuilder
import com.skeagis.monitorporastov.Utils.editableToCharArray

class LoginActivityViewModel : BaseViewModel() {
    private val _rememberCredentials = MutableLiveData<Boolean>()
    val rememberCredentials: LiveData<Boolean> get() = _rememberCredentials
    private val _stayLoggedIn = MutableLiveData<Boolean>()
    val stayLoggedIn: LiveData<Boolean> get() = _stayLoggedIn

    private val _usernameEditable = MutableLiveData<Editable>()
    val usernameEditable: LiveData<Editable> get() = _usernameEditable

    private val _passwordEditable = MutableLiveData<Editable>()
    val passwordEditable: LiveData<Editable> get() = _passwordEditable

    companion object {
        private const val LOGIN_TAG = "LOGIN"
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

    private fun getGeoserverServiceAPI(): GeoserverRetrofitAPI {
        return GeoserverRetrofitBuilder.getServiceWithScalarsFactory(editableToCharArray(_usernameEditable.value),
            editableToCharArray(_passwordEditable.value))
    }

    suspend fun doLogin(): Boolean {
        val resultOfCallToGeoserver =
            performCallToGeoserver { getGeoserverServiceAPI().login() }
        val wasCallSuccessful = resultOfCallToGeoserver.first
        val response = resultOfCallToGeoserver.second
        if (response == null) {
            Log.d(LOGIN_TAG, "Error was occurred during attempt to login")
            return wasCallSuccessful
        }
        when {
            response.code() == unauthorisedCode -> {
                Log.d(LOGIN_TAG, "Unauthorised error")
            }
            wasCallSuccessful -> {
                Log.d(LOGIN_TAG, "Login was successful")
            }
            else -> {
                Log.d(LOGIN_TAG, "Error: ${response.message()}")
            }
        }
        return wasCallSuccessful
    }
}