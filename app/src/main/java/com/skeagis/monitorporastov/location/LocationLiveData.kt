package com.skeagis.monitorporastov.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.LiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

// https://proandroiddev.com/android-tutorial-on-location-update-with-livedata-774f8fcc9f15
class LocationLiveData(context: Context) : LiveData<Location>() {

    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val interval = 0L
        private const val fastestInterval = 0L
        val locationRequest: LocationRequest = LocationRequest.create()
            .setInterval(interval)
            .setFastestInterval(fastestInterval)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setSmallestDisplacement(1F)

    }

    @SuppressLint("MissingPermission")
    override fun onActive() {
        super.onActive()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.also {
                    setLocationData(it)
                }
            }
        startLocationUpdates()
    }

    override fun onInactive() {
        super.onInactive()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun setLocationData(location: Location) {
        value = location
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (locationResult.locations.isNotEmpty())
                setLocationData(locationResult.locations[0])
//            for (location in locationResult.locations) {
//
//                setLocationData(location)
//            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
}