package com.skeagis.monitorporastov.fragments.viewmodels.base_view_models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlFilterParametersNames.urlNameOfIdParameter
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.model.UsersData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response

open class DamagePhotosBaseViewModel : MapBaseViewModel() {
    private val _loadedPhotos = MutableLiveData<Boolean>()
    val loadedPhotos: LiveData<Boolean> = _loadedPhotos

    var stringsOfPhotosList = mutableListOf<String>()

    companion object {
        private const val PHOTOS_TAG = "ObservingPhotos"
    }

    fun setIfLoadedPhotos(value: Boolean) {
        _loadedPhotos.postValue(value)
    }

    open fun setBitmaps(listOfBitmaps: MutableList<Bitmap>) {
        damageDataItem.value?.bitmaps = listOfBitmaps
    }

    private fun setIndexesOfPhotos(listOfIndexes: MutableList<Int>) {
        damageDataItem.value?.indexesOfPhotos = listOfIndexes
    }

    fun prepareToLoadPhotos() {
        if (damageDataItem.value == null) {
            return
        }
        if (checkIfPhotosHaveBeenLoaded()) {
            setUpPreviouslyLoadedPhotos()
        } else {
            observeNetworkState { loadDamagePhotos() }
        }
    }

    private fun checkIfShouldReloadPhotos(): Boolean {
        return isNetworkAvailable.value == true &&
                (_loadedPhotos.value == false || _loadedPhotos.value == null)
    }

    private suspend fun loadDamagePhotos() {
        if (checkIfShouldReloadPhotos()) {
            damageDataItem.value?.let {
                setLoading(true)
                fetchPhotos(it)
            }
        }
    }

    private suspend fun fetchPhotos(item: DamageData) {
        if (_loadedPhotos.value == true) {
            return
        }
        val urlFilterValue = createURLFilterForPhotos(item.unique_id)
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithGson() ?: return
        val resultOfCallToGeoserver: Pair<Boolean, Response<UsersData>?> =
            performCallToGeoserver { geoserverRetrofitAPI.getPhotos(urlFilterValue) }
        processObtainedResponseForPhotos(resultOfCallToGeoserver)
    }

    private fun processObtainedResponseForPhotos(
        resultOfCallToGeoserver: Pair<Boolean,
                Response<UsersData>?>,
    ) {
        val wasCallSuccessful = resultOfCallToGeoserver.first
        val response = resultOfCallToGeoserver.second
        setIfLoadedPhotos(wasCallSuccessful)
        if (response == null) {
            setIfLoadedPhotos(false)
            Log.e(PHOTOS_TAG, "Error was occurred during attempt to get photos")
            return
        }
        if (wasCallSuccessful) {
            Log.d(PHOTOS_TAG, "Photos have been successfully received")
            val usersData: UsersData = response.body() ?: return
            createListsNecessaryForPhotos(usersData)
            setNewlyLoadedPhotos()
        }
    }

    private fun checkIfPhotosHaveBeenLoaded(): Boolean {
        return damageDataItem.value != null && damageDataItem.value?.bitmapsLoaded == true
    }

    open fun setUpPreviouslyLoadedPhotos() {
        if (_loadedPhotos.value == true) {
            setLoading(false)
            return
        }
        if (checkIfPhotosHaveBeenLoaded()) {
            setBitmaps(damageDataItem.value?.bitmaps!!)

            Log.d(PHOTOS_TAG, "Previously loaded bitmaps where loaded")
        }
        setIfLoadedPhotos(true)
        setLoading(false)
    }

    open fun setNewlyLoadedPhotos() {
        if (stringsOfPhotosList.isNullOrEmpty()) {
            setLoading(false)
            setIfLoadedPhotos(true)
            updateDamageItemAsLoaded()
            Log.d(PHOTOS_TAG, "No photos here!")
            return
        }

        val bitmaps = mutableListOf<Bitmap>()
        CoroutineScope(Dispatchers.Main).launch {
            stringsOfPhotosList.forEach {
                val imageBytes: ByteArray = Base64.decode(it, 0)
                val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmaps.add(image)

            }
            setBitmaps(bitmaps)

            setLoading(false)
            setIfLoadedPhotos(true)
            updateDamageItemAsLoaded()
        }
    }

    private fun updateDamageItemAsLoaded() {
        damageDataItem.value?.bitmapsLoaded = true
    }

    private fun createListsNecessaryForPhotos(usersData: UsersData) {
        createStringsOfPhotosList(usersData)
        setIndexesOfPhotos(createIndexesOfPhotosList(usersData))
    }

    private fun createStringsOfPhotosList(usersData: UsersData) {
        val listOfStringsOfPhotos = mutableListOf<String>()
        usersData.features.forEach { listOfStringsOfPhotos.add(it.properties.foto) }
        stringsOfPhotosList = listOfStringsOfPhotos.toMutableList()
    }
}