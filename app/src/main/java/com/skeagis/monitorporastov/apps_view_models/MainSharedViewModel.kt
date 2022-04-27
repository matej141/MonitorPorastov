package com.skeagis.monitorporastov.apps_view_models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.skeagis.monitorporastov.Event
import com.skeagis.monitorporastov.model.DamageData

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

    private val _uniqueIdOfDeletedDamageDataItem = MutableLiveData<Event<String>>()
    val uniqueIdOfDeletedDamageDataItem: LiveData<Event<String>> = _uniqueIdOfDeletedDamageDataItem


    fun setIfLoadedUserData(value: Boolean) {
        _loadedUserData.postValue(value)
    }

    fun setIfLoadedMapLayerWithUserData(value: Boolean) {
        _loadedMapLayerWithUserData.value = value
    }

    fun setIfLoadedMapLayerWithUserDataAsync(value: Boolean) {
        _loadedMapLayerWithUserData.postValue(value)
    }

    fun clearSelectedDamageDataItemAsync() {
        _selectedDamageDataItem.postValue(null)
    }

    fun clearSelectedDamageDataItemFromMapAsync() {
        _selectedDamageDataItemFromMap.postValue(null)
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

    fun setIfDeletingWasSuccessful(value: Boolean) {
        _deletingWasSuccessful.postValue(Event(value))
    }

    fun setUniqueIdOfDeletedDamageDataItem(uniqueId: String) {
        _uniqueIdOfDeletedDamageDataItem.value = Event(uniqueId)
    }

}