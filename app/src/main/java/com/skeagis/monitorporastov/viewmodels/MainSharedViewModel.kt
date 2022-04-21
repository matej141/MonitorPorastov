package com.skeagis.monitorporastov.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.Event
import com.skeagis.monitorporastov.Utils
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataPostStringsFactory.createDeleteRecordTransactionString
import com.skeagis.monitorporastov.geoserver.factories.GeoserverPhotosPostStringsFactory.createDeletePhotosStringWithUniqueId
import com.skeagis.monitorporastov.model.DamageData
import kotlinx.coroutines.*

// class MainSharedViewModel :ViewModel(), ViewModelInterface by ViewModelDelegate()

class MainSharedViewModel : BaseViewModel() {
    private val _loadedUserData = MutableLiveData<Boolean>()
    val loadedUserData: LiveData<Boolean> = _loadedUserData

    private val _loadedMapLayerWithUserData = MutableLiveData<Boolean>()
    val loadedMapLayerWithUserData: LiveData<Boolean> = _loadedMapLayerWithUserData

    private val _selectedDamageDataItem = MutableLiveData<DamageData?>()
    val selectedDamageDataItem: LiveData<DamageData?> = _selectedDamageDataItem

    private val _selectedDamageDataItemFromMap: MutableLiveData<DamageData?> =
        MutableLiveData<DamageData?>()
    val selectedDamageDataItemFromMap: MutableLiveData<DamageData?>
        get() =
            _selectedDamageDataItemFromMap

    private val _selectedDamageDataItemToShowInMap = MutableLiveData<DamageData?>()
    val selectedDamageDataItemToShowInMap: LiveData<DamageData?>
        get() =
            _selectedDamageDataItemToShowInMap

    private val _deletingWasSuccessful = MutableLiveData<Event<Boolean>>()
    val deletingWasSuccessful: LiveData<Event<Boolean>> = _deletingWasSuccessful

    fun setIfLoadedUserData(value: Boolean) {
        _loadedUserData.postValue(value)
    }

    fun setIfLoadedMapLayerWithUserData(value: Boolean) {
        _loadedMapLayerWithUserData.value = value
    }

    fun setIfLoadedMapLayerWithUserDataAsync(value: Boolean) {
        _loadedMapLayerWithUserData.postValue(value)
    }

    fun selectDamageData(item: DamageData) {
        _selectedDamageDataItem.value = item
    }

    fun selectDamageDataFromMap(damageData: DamageData) {
        _selectedDamageDataItemFromMap.value = damageData
    }

    fun selectDamageDataToShowInMap(damageData: DamageData) {
        _selectedDamageDataItemToShowInMap.value = damageData
    }

    fun clearSelectedDamageDataItemFromMap() {
        _selectedDamageDataItemFromMap.value = null
    }

    fun clearSelectedDamageDataItemToShowInMap() {
        _selectedDamageDataItemToShowInMap.value = null
    }

    private fun setIfDeletingWasSuccessful(value: Boolean) {
        _deletingWasSuccessful.postValue(Event(value))
    }

    fun prepareToDelete(damageData: DamageData?) {
        if (damageData == null) {
            return
        }
        val id = damageData.unique_id
        launch {
            performDeleting(id)
        }
    }

    private suspend fun performDeleting(uniqueId: String) {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            val resultsListDeferred =
                listOf(
                    async { deleteItem(uniqueId) },
                    async { deleteItemPhotosOfItem(uniqueId)
                    })
            val resultsList: List<Boolean> = resultsListDeferred.awaitAll()
            val resultOfDeleting = Utils.checkIfCallsWereSucceeded(resultsList)
            if (resultOfDeleting) {
                setIfLoadedUserData(false)
                setIfLoadedMapLayerWithUserDataAsync(false)
            }
            setIfDeletingWasSuccessful(resultOfDeleting)
            setLoading(false)
        }
    }

    private suspend fun deleteItem(uniqueId: String): Boolean {
        val deleteDamageDataString = createDeleteRecordTransactionString(uniqueId)
        if (deleteDamageDataString.isEmpty()) {
            return true
        }
        val requestBody = Utils.createRequestBody(deleteDamageDataString)
        return postToGeoserver(requestBody)
    }

    private suspend fun deleteItemPhotosOfItem(uniqueId: String): Boolean {
        val deletePhotosString = createDeletePhotosStringWithUniqueId(uniqueId)
        val requestBody = Utils.createRequestBody(deletePhotosString)
        return postToGeoserver(requestBody)
    }



}