package com.skeagis.monitorporastov.fragments.viewmodels.base_view_models

import androidx.lifecycle.*
import com.skeagis.monitorporastov.Event
import com.skeagis.monitorporastov.Utils
import com.skeagis.monitorporastov.apps_view_models.BaseViewModel
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.apps_view_models.MainSharedViewModel
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataFilterStringsFactory
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataPostStringsFactory
import com.skeagis.monitorporastov.geoserver.factories.GeoserverPhotosPostStringsFactory
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitBuilder
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.model.UsersData
import kotlinx.coroutines.*

abstract class MapBaseViewModel : BaseViewModel() {
    var sharedViewModel: MainSharedViewModel? = null
    private val _damageDataItem = MutableLiveData<DamageData>()
    val damageDataItem: LiveData<DamageData> = _damageDataItem

    private val _deletingWasSuccessful = MutableLiveData<Event<Boolean>>()
    val deletingWasSuccessful: LiveData<Event<Boolean>> = _deletingWasSuccessful

    private val errorOccurredObserver = Observer<Boolean> {
        sharedViewModel?.setErrorOccurred(it)
    }
    private val errorMessageObserver = Observer<Event<String>> { errorMessage ->
        errorMessage.getContentIfNotHandled()?.let { errorMessageString ->
            sharedViewModel?.onError(errorMessageString)
        }
    }

    private val unauthorisedErrorIsOccurredObserver = Observer<Boolean> {
        sharedViewModel?.reportThatUnauthorisedErrorIsOccurred()
    }

    private var networkAvailabilityObserver = Observer<Boolean> {
    }

    fun setDamageDataItem(damageData: DamageData) {
        _damageDataItem.value = damageData
    }

    private fun initSharedViewModel(sharedViewModel: MainSharedViewModel) {
        this.sharedViewModel = sharedViewModel
    }

    open fun initViewModelMethods(
        sharedViewModel: MainSharedViewModel,
        viewLifecycleOwner: LifecycleOwner,
    ) {
        initSharedViewModel(sharedViewModel)
        setObservers()
        setToViewModel()
    }

    open fun setObservers() {
        errorOccurred.observeForever(errorOccurredObserver)
        errorMessage.observeForever(errorMessageObserver)
        unauthorisedErrorIsOccurred.observeForever(unauthorisedErrorIsOccurredObserver)
    }

    open fun setToViewModel() {
        sharedViewModel?.usernameCharArray?.value?.let { setUsernameCharArray(it) }
        sharedViewModel?.passwordCharArray?.value?.let { setPasswordCharArray(it) }
    }


    fun observeNetworkState(reloadFunction: suspend () -> Unit = { }) {
        networkAvailabilityObserver = Observer<Boolean> { isAvailable ->
            if (isAvailable) {
                launch {
                    reloadFunction()
                }
            }
            setNetworkAvailability(isAvailable)
        }
        sharedViewModel?.isNetworkAvailable?.observeForever(networkAvailabilityObserver)
    }

    fun getGeoserverServiceAPIWithGson(): GeoserverRetrofitAPI? {
        val geoserverRetrofitAPI: GeoserverRetrofitAPI? =
            usernameCharArray.value?.let { usernameChars ->
                passwordCharArray.value?.let { passwordChars ->
                    GeoserverRetrofitBuilder.getServiceWithGsonFactory(usernameChars,
                        passwordChars)
                }
            }
        if (geoserverRetrofitAPI == null) {
            informThatCredentialsWereNotLoaded()
        }
        return geoserverRetrofitAPI
    }

    suspend fun getDamageDataItemFromGeoServerByUniqueId(uniqueId: String): DamageData? {
        val filterString =
            GeoserverDataFilterStringsFactory.createFilterStringByUniqueId(uniqueId)
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithGson() ?: return null
        val response = geoserverRetrofitAPI.getDetail(filterString)
        val userData: UsersData = response.body() ?: return null
        val countOfFeatures: Int = userData.features.size
        if (countOfFeatures == 0) {
            return null
        }
        return userData.features[0].properties
    }

    suspend fun getIndexesOfPhotosFromGeoserver(uniqueId: String): MutableList<Int>? {
        val urlFilterValue = createURLFilterForPhotos(uniqueId)
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithGson() ?: return null
        val response = geoserverRetrofitAPI.getPhotos(urlFilterValue)
        val userData: UsersData = response.body() ?: return null
        return createIndexesOfPhotosList(userData)
    }

    fun createURLFilterForPhotos(uniqueId: String): String {
        return "${GeoserverPropertiesNames.UrlFilterParametersNames.urlNameOfIdParameter}:$uniqueId"
    }

    fun createIndexesOfPhotosList(usersData: UsersData): MutableList<Int> {
        val listOfIndexesOfPhotos = mutableListOf<Int>()
        usersData.features.forEach { listOfIndexesOfPhotos.add(it.properties.id) }
        return listOfIndexesOfPhotos
    }

    fun prepareToDelete(damageData: DamageData?) {
        if (damageData == null) {
            return
        }
        val id = damageData.unique_id
        observeNetworkState { performDeleting(id) }
    }

    private suspend fun performDeleting(uniqueId: String) {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred =
                listOf(
                    async { deleteItem(uniqueId) },
                    async {
                        deleteItemPhotosOfItem(uniqueId)
                    })
            if (checkIfDeleted(uniqueId)) {
                onSuccessfulDelete()
                sharedViewModel?.setIfDeletingWasSuccessful(true)
                return@launch
            }
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val resultOfDeleting = Utils.checkIfCallsWereSucceeded(resultsList)
            if (resultOfDeleting) {
                onSuccessfulDelete()
            }
            sharedViewModel?.setIfDeletingWasSuccessful(resultOfDeleting)
        }
    }

    private fun onSuccessfulDelete() {
        sharedViewModel?.setIfLoadedUserData(false)
        sharedViewModel?.setIfLoadedMapLayerWithUserDataAsync(false)
        clearSelectedDamageItemsAfterDelete()
        setLoading(false)
    }

    private fun clearSelectedDamageItemsAfterDelete() {
        if (sharedViewModel?.selectedDamageDataItem?.value?.id ==
            sharedViewModel?.selectedDamageDataItemFromMap?.value?.id
        ) {
            sharedViewModel?.clearSelectedDamageDataItemAsync()
            sharedViewModel?.clearSelectedDamageDataItemFromMapAsync()
        }
    }

    private suspend fun deleteItem(uniqueId: String): Boolean {
        if (getDamageDataItemFromGeoServerByUniqueId(uniqueId) == null) {
            return true
        }
        val deleteDamageDataString =
            GeoserverDataPostStringsFactory.createDeleteRecordTransactionString(uniqueId)
        if (deleteDamageDataString.isEmpty()) {
            return true
        }
        val requestBody = Utils.createRequestBody(deleteDamageDataString)
        return postToGeoserver(requestBody)
    }

    private suspend fun deleteItemPhotosOfItem(uniqueId: String): Boolean {
        if (getIndexesOfPhotosFromGeoserver(uniqueId) == null) {
            return true
        }
        val deletePhotosString =
            GeoserverPhotosPostStringsFactory.createDeletePhotosStringWithUniqueId(uniqueId)
        val requestBody = Utils.createRequestBody(deletePhotosString)
        return postToGeoserver(requestBody)
    }

    private suspend fun checkIfDeleted(uniqueId: String): Boolean {
        return getDamageDataItemFromGeoServerByUniqueId(uniqueId) == null &&
                getIndexesOfPhotosFromGeoserver(
                    uniqueId) == null
    }

    override fun onCleared() {
        super.onCleared()
        errorOccurred.removeObserver(errorOccurredObserver)
        errorMessage.removeObserver(errorMessageObserver)
        unauthorisedErrorIsOccurred.removeObserver(unauthorisedErrorIsOccurredObserver)
        sharedViewModel?.isNetworkAvailable?.removeObserver(networkAvailabilityObserver)
    }
}