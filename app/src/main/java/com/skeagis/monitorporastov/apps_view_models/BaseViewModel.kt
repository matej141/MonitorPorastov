package com.skeagis.monitorporastov.apps_view_models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.skeagis.monitorporastov.CoroutineScopeDelegate
import com.skeagis.monitorporastov.CoroutineScopeInterface
import com.skeagis.monitorporastov.Event
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitBuilder
import kotlinx.coroutines.*
import okhttp3.RequestBody
import retrofit2.Response
import java.net.*

abstract class BaseViewModel : ViewModel(), CoroutineScopeInterface by CoroutineScopeDelegate() {
    override var job: Job = Job()
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _usernameCharArray = MutableLiveData<CharArray>()
    val usernameCharArray: LiveData<CharArray> = _usernameCharArray

    private val _passwordCharArray = MutableLiveData<CharArray>()
    val passwordCharArray: LiveData<CharArray> = _passwordCharArray

    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable

    private val _errorOccurred = MutableLiveData<Boolean>()
    val errorOccurred: LiveData<Boolean> = _errorOccurred

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    private val _unauthorisedErrorIsOccurred = MutableLiveData<Boolean>()
    val unauthorisedErrorIsOccurred: LiveData<Boolean> = _unauthorisedErrorIsOccurred

    var blockedClicking = false

    companion object {
        private const val CALL_TO_GEOSERVER_TAG = "CallToGeoserver"
        private const val UNLOAD_CREDENTIALS_TAG = "UnloadCredentials"
        const val unauthorisedCode = 401
    }

    fun setUsernameCharArray(usernameArray: CharArray) {
        _usernameCharArray.value = usernameArray
    }

    fun setPasswordCharArray(passwordArray: CharArray) {
        _passwordCharArray.value = passwordArray
    }

    fun setLoading(value: Boolean) {
        _loading.postValue(value)
        setIfClickingIsBlocked(value)
    }

    private fun setIfClickingIsBlocked(value: Boolean) {
        blockedClicking = value
    }

    fun onError(message: String) {
        if (_isNetworkAvailable.value == true) {
            _errorMessage.postValue(Event(message))
        }
    }

    fun setErrorOccurred(value: Boolean) {
        _errorOccurred.postValue(value)
    }

    fun setNetworkAvailability(value: Boolean) {
        _isNetworkAvailable.value = value
    }

    fun reportThatUnauthorisedErrorIsOccurred() {
        _unauthorisedErrorIsOccurred.postValue(true)
    }

    suspend fun <T : Any> performCallToGeoserver(serviceMethod: suspend () -> Response<T>):
            Pair<Boolean, Response<T>?> {
        val successResultDeferred = CompletableDeferred<Boolean>()
        val retrofitResponseDeferred = CompletableDeferred<Response<T>?>()

        setErrorOccurred(false)
        launch {
            try {
                val response = serviceMethod()
                retrofitResponseDeferred.complete(response)
                when {
                    response.isSuccessful -> {
                        successResultDeferred.complete(true)
                    }
                    response.code() == unauthorisedCode -> {
                        successResultDeferred.complete(false)
                        reportThatUnauthorisedErrorIsOccurred()
                    }
                    else -> {
                        onError(response.message())
                        setErrorOccurred(true)
                        successResultDeferred.complete(false)
                    }
                }

            }

            catch (e: Exception) {
                setErrorOccurred(true)
                successResultDeferred.complete(false)
                retrofitResponseDeferred.complete(null)
                handleExceptions(e)
            }
        }
        val res = successResultDeferred.await()
        Log.d(CALL_TO_GEOSERVER_TAG, "Successs??? -> $res")
        return Pair(successResultDeferred.await(), retrofitResponseDeferred.await())
    }

    private fun handleExceptions(e: Exception) {
        when {
            e is UnknownHostException -> {
                Log.e(CALL_TO_GEOSERVER_TAG, "UnknownHostException: $e")
            }
            e is SocketException && e.message == "Software caused connection abort" -> {
                Log.e(CALL_TO_GEOSERVER_TAG, "SocketException: $e")
            }
            e is ConnectException && e.message == "Failed to connect to services.skeagis.sk/212.5.204.126:7492" -> {
                Log.e(CALL_TO_GEOSERVER_TAG, "ConnectException: $e")
            }
            else -> {
                onError(e.toString())
            }
        }
    }

    open fun informThatCredentialsWereNotLoaded() {
        onError("Nepodarilo sa na????ta?? Va??e prihlasovacie ??daje")
        Log.d(UNLOAD_CREDENTIALS_TAG, "Error was occurred during attempt to load credentials")
    }

    fun getGeoserverServiceAPIWithScalars(): GeoserverRetrofitAPI? {
        val geoserverRetrofitAPI: GeoserverRetrofitAPI? =
            usernameCharArray.value?.let { usernameChars ->
                passwordCharArray.value?.let { passwordChars ->
                    GeoserverRetrofitBuilder.getServiceWithScalarsFactory(usernameChars,
                        passwordChars)
                }
            }
        if (geoserverRetrofitAPI == null) {
            informThatCredentialsWereNotLoaded()
        }
        return geoserverRetrofitAPI
    }

    suspend fun postToGeoserver(requestBody: RequestBody): Boolean {
        val deferredBoolean: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>()
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithScalars() ?: return false
        val resultOfCallToGeoserver: Pair<Boolean, Response<String>?> =
            performCallToGeoserver { geoserverRetrofitAPI.postToGeoserver(requestBody) }
        deferredBoolean.complete(processPostedResponse(resultOfCallToGeoserver.first,
            resultOfCallToGeoserver.second))
        return deferredBoolean.await()
    }

    private fun processPostedResponse(
        wasSuccessful: Boolean,
        response: Response<String>?,
    ): Boolean {
        val responseBody = response?.body() ?: return false
        if (wasSuccessful && responseBody.contains("SUCCESS")) {
            return wasSuccessful
        }
        return false
    }

    public override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}
