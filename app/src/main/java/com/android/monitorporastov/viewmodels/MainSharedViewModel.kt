package com.android.monitorporastov.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.Event
import com.android.monitorporastov.Utils
import com.android.monitorporastov.geoserver.factories.GeoserverDataPostStringsFactory.createDeleteRecordTransactionString
import com.android.monitorporastov.model.DamageData
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
        _loadedUserData.postValue(value)
    }

    fun setIfLoadedMapLayerWithUserData(value: Boolean) {
        _loadedMapLayerWithUserData.value = value
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
        val id = damageData.id
        launch {
            performDeleting(id)
        }
    }

    private suspend fun performDeleting(id: Int) {
        setLoading(true)
        val deferredBoolean = CompletableDeferred<Boolean>()

        deferredBoolean.complete(deleteItem(id))

        val resultOfDeleting = deferredBoolean.await()
        if (resultOfDeleting) {
            setIfLoadedUserData(false)
        }
        setIfDeletingWasSuccessful(resultOfDeleting)
    }

    private suspend fun deleteItem(id: Int): Boolean {
        val updateDamageDataString = createDeleteRecordTransactionString(id)
        if (updateDamageDataString.isEmpty()) {
            return true
        }
        val requestBody = Utils.createRequestBody(updateDamageDataString)
        return postToGeoserver(requestBody)
    }



}