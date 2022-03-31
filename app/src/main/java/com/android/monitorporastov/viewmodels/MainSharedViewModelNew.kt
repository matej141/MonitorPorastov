package com.android.monitorporastov.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.Utils
import com.android.monitorporastov.model.DamageData
import kotlinx.coroutines.*

// class MainSharedViewModelNew :ViewModel(), ViewModelInterface by ViewModelDelegate()

class MainSharedViewModelNew : BaseViewModel() {
    private val _loadedUserData = MutableLiveData<Boolean>()
    val loadedUserData: LiveData<Boolean> = _loadedUserData

    private val _damageDataList = MutableLiveData<List<DamageData>>()
    val damageDataList: LiveData<List<DamageData>> = _damageDataList

    private val _selectedDamageDataItem = MutableLiveData<DamageData?>()
    val selectedDamageDataItem: LiveData<DamageData?> = _selectedDamageDataItem

    private var mutableSelectedDamageDataItemFromMap: MutableLiveData<DamageData?> =
        MutableLiveData<DamageData?>()
    val selectedDamageDataItemFromMap: MutableLiveData<DamageData?>
        get() =
            mutableSelectedDamageDataItemFromMap

    private val _selectedDamageDataItemToShowInMap = MutableLiveData<DamageData?>()
    val selectedDamageDataItemToShowInMap: LiveData<DamageData?>
        get() =
            _selectedDamageDataItemToShowInMap

    companion object {
        private const val MAIN_VIEW_MODEL_TAG = "MainViewModel"

    }

    fun setIfLoadedUserData(value: Boolean) {
        _loadedUserData.postValue(value)
    }

    fun selectDamageData(item: DamageData) {
        _selectedDamageDataItem.value = item
    }

    fun selectDamageDataFromMap(damageData: DamageData) {
        mutableSelectedDamageDataItemFromMap.value = damageData
    }

    fun selectDamageDataToShowInMap(damageData: DamageData) {
        _selectedDamageDataItemToShowInMap.value = damageData
    }

    suspend fun deleteItem(damageData: DamageData?): Boolean {
        if (damageData == null) {
            return false
        }
        val deleteRecordString = createDeleteRecordTransactionString(damageData)
        val requestBody = Utils.createRequestBody(deleteRecordString)
        val deferredBoolean: Deferred<Boolean> = CoroutineScope(Dispatchers.Main).async<Boolean> {
            val deferred = async { postToGeoserver(requestBody) }
            deferred.await()
        }
        return deferredBoolean.await()
    }

    private fun createDeleteRecordTransactionString(damageData: DamageData): String {
        return "<Transaction xmlns=\"http://www.opengis.net/wfs\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://geoserver.org/geoserver_skeagis " +
                "http://services.skeagis.sk:7492/geoserver/wfs?SERVICE=WFS&amp;REQUEST=" +
                "DescribeFeatureType&amp;VERSION=1.0.0&amp;TYPENAME=geoserver_skeagis:porasty\" " +
                "xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\" service=\"WFS\" " +
                "xmlns:geoserver_skeagis=\"http://geoserver.org/geoserver_skeagis\">\n" +
                "    <Delete xmlns=\"http://www.opengis.net/wfs\" " +
                "typeName=\"geoserver_skeagis:porasty\">\n" +
                "        <Filter xmlns=\"http://www.opengis.net/ogc\">\n" +
                "            <PropertyIsEqualTo>" +
                "<PropertyName>id</PropertyName>" +
                "<Literal>${damageData.id}</Literal>" +
                "</PropertyIsEqualTo>\n" +
                "        </Filter>\n" +
                "    </Delete>\n" +
                "</Transaction>\n"
    }

}