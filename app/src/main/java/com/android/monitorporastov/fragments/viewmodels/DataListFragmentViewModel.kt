package com.android.monitorporastov.fragments.viewmodels

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.fragments.viewmodels.base.MapBaseViewModel
import com.android.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import org.osmdroid.util.GeoPoint
import retrofit2.Response


class DataListFragmentViewModel : MapBaseViewModel() {
    private val _damageDataList = MutableLiveData<MutableList<DamageData>>()
    val damageDataList: LiveData<MutableList<DamageData>> get() = _damageDataList

    private val _loadedUserData = MutableLiveData<Boolean>()
    val loadedUserData: LiveData<Boolean> = _loadedUserData

    companion object {
        private const val USER_DATA_TAG = "ObservingUserData"
    }

    private fun setDamageDataList(damageDataList: MutableList<DamageData>) {
        _damageDataList.postValue(damageDataList)
    }

    private suspend fun fetchUserData() {
        if (_loadedUserData.value == true) {
            return
        }
        val urlFilterValue: String = createURLFilterForUserData()
        if (urlFilterValue.isEmpty()) {
            informThatCredentialsWereNotLoaded()
            return
        }
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithGson() ?: return

        val resultOfCallToGeoserver: Pair<Boolean, Response<UsersData>?> =
            performCallToGeoserver { geoserverRetrofitAPI.getUserData(urlFilterValue) }
        processObtainedResponseForUserData(resultOfCallToGeoserver)
    }


    private fun processObtainedResponseForUserData(
        resultOfCallToGeoserver: Pair<Boolean,
                Response<UsersData>?>
    ) {
        val wasCallSuccessful = resultOfCallToGeoserver.first
        val response = resultOfCallToGeoserver.second
        setIfLoadedUserDataAsync(wasCallSuccessful)
        if (response == null) {
            setIfLoadedUserDataAsync(false)  // pre istotu
            Log.e(USER_DATA_TAG, "Error was occurred during attempt to get user data")
            return
        }
        if (wasCallSuccessful) {
            Log.d(USER_DATA_TAG, "User data have been successfully received")
            val res: UsersData = response.body() ?: return
            createDamageDataList(res)
        }

    }

    private fun setIfLoadedUserData(value: Boolean) {
        _loadedUserData.value = value
    }

    private fun setIfLoadedUserDataAsync(value: Boolean) {
        _loadedUserData.postValue(value)
    }

    private fun createURLFilterForUserData(): String {
        val geoserverUrlFilterUsernameParameter = "meno"
        if (usernameCharArray.value == null) {
            return ""
        }
        return "$geoserverUrlFilterUsernameParameter:${
            usernameCharArray.value?.let {
                String(it)
            }
        }"
    }

    private fun createDamageDataList(usersData: UsersData) {
        val newMutableDamageDataList = mutableListOf<DamageData>()
        var i = 0
        usersData.features.forEach {
            val listOfGeopoints = createListOfGeopoints(usersData, i)
            i++
            val damageData = it.properties
            listOfGeopoints.removeLast()
            damageData.coordinates = listOfGeopoints
            newMutableDamageDataList.add(damageData)
        }
        setDamageDataList(newMutableDamageDataList)
    }

    private fun createListOfGeopoints(usersData: UsersData, index: Int): MutableList<GeoPoint> {
        val listOfGeopoints = mutableListOf<GeoPoint>()
        usersData.features[index].geometry.coordinates.forEach {
            it.forEach { p ->
                listOfGeopoints.add(GeoPoint(p[1], p[0]))
            }
        }
        return listOfGeopoints
    }

    private fun checkIfShouldReloadUserData(): Boolean {
        val a = isNetworkAvailable.value
        val b = _loadedUserData.value
        return isNetworkAvailable.value == true && (_loadedUserData.value == false || _loadedUserData.value == null)
    }

    private suspend fun reloadUserData() {
        if (checkIfShouldReloadUserData()) {
            fetchUserData()
        }
    }

    override fun initViewModelMethods(
        sharedViewModel: MainSharedViewModelNew,
        viewLifecycleOwner: LifecycleOwner,
    ) {

        super.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
        //copyObservers()
        //setToViewModel()
        observeNetworkState { reloadUserData() }
    }

    override fun copyObservers() {
        super.copyObservers()
        viewLifecycleOwner?.let {
            loadedUserData.observe(it) {
                sharedViewModel?.setIfLoadedUserData(it)
            }
        }
    }

    override fun setToViewModel() {
        super.setToViewModel()
        sharedViewModel?.loadedUserData?.value?.let {
            setIfLoadedUserData(it)
        }
    }

}