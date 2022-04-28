package com.skeagis.monitorporastov.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectionLiveData(context: Context) : LiveData<Boolean>() {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallbacks = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val networkCapability = connectivityManager.getNetworkCapabilities(network)
            val hasNetworkConnection =
                networkCapability?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ?: false
            if (hasNetworkConnection) {
                checkInternetAccess()
            }
        }

        private fun checkInternetAccess() {
            CoroutineScope(Dispatchers.IO).launch {
                if (InternetAvailability.check()) {
                    postValue(true)
                    return@launch
                }
                postValue(false)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            checkInternetAccess()
        }

        override fun onUnavailable() {
            super.onUnavailable()
            checkInternetAccess()
        }
    }

    private fun checkNetwork() {
        checkActiveNetwork()
        val requestBuilder = createRequestBuilder()
        connectivityManager.registerNetworkCallback(requestBuilder, networkCallbacks)
    }

    private fun checkActiveNetwork() {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            postValue(false)
        }
    }

    private fun createRequestBuilder(): NetworkRequest {
        val requestBuilder = NetworkRequest.Builder().apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.build()
        return requestBuilder
    }

    override fun onActive() {
        super.onActive()
        checkNetwork()
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(networkCallbacks)
    }
}