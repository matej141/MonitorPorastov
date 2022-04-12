package com.android.monitorporastov.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.BasicAuthInterceptor
import com.android.monitorporastov.geoserver.retrofit.GeoserverRetrofitBuilder
import com.android.monitorporastov.Utils
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData

import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

open class MainSharedViewModel : BaseViewModel() {
    //private val service = GeoserverRetrofitBuilder.get()
   // val damageDataList = MutableLiveData<List<DamageData>>()
    private val mutableDamageDataItem = MutableLiveData<DamageData?>()
    private val mutableSelectedDamageDataItem = MutableLiveData<DamageData?>()

    val selectedDamageDataItem: LiveData<DamageData?> get() = mutableSelectedDamageDataItem
    private var mutableStringsOfPhotosList = MutableLiveData<List<String>?>()
    private val mutableIndexesOfPhotosList = MutableLiveData<MutableList<Int>>()
    val stringsOfPhotosList: LiveData<List<String>?> get() = mutableStringsOfPhotosList
    val indexesOfPhotosList: LiveData<MutableList<Int>> get() = mutableIndexesOfPhotosList

    private var mutableSelectedDamageDataItemFromMap: MutableLiveData<DamageData?> =
        MutableLiveData<DamageData?>()
    val selectedDamageDataItemFromMap: MutableLiveData<DamageData?>
        get() =
            mutableSelectedDamageDataItemFromMap

    private val _selectedDamageDataItemToShowInMap = MutableLiveData<DamageData?>()
    val selectedDamageDataItemToShowInMap: LiveData<DamageData?>
        get() =
            _selectedDamageDataItemToShowInMap

    //val isNetworkAvailable = MutableLiveData<Boolean>()
   // val errorMessage = MutableLiveData<String>()

    private val _usernameCharArray = MutableLiveData<CharArray>()
   // val usernameCharArray: LiveData<CharArray> get() = _usernameCharArray

    private val _passwordCharArray = MutableLiveData<CharArray>()
//    val passwordCharArray: LiveData<CharArray> get() = _passwordCharArray

    fun selectDamageDataFromMap(damageData: DamageData) {
        mutableSelectedDamageDataItemFromMap.value = damageData
    }

    fun selectDamageData(item: DamageData) {
        mutableSelectedDamageDataItem.value = item
    }

    fun selectDamageDataToShowInMap(damageData: DamageData) {
        _selectedDamageDataItemToShowInMap.value = damageData
    }

//    fun setUsernameCharArray(usernameArray: CharArray) {
//        _usernameCharArray.value = usernameArray
//    }
//
//    fun setPasswordCharArray(passwordArray: CharArray) {
//        _passwordCharArray.value = passwordArray
//    }

    fun clearStringsOfPhotosList() {
        mutableStringsOfPhotosList.value = null
    }

    fun clearSelectedDamageDataItemFromMap() {
        mutableSelectedDamageDataItemFromMap.value = null
    }

    fun clearSelectedDamageDataItemToShowInMap() {
        _selectedDamageDataItemToShowInMap.value = null
    }

    fun clearSelectedDamageDataItem() {
        mutableSelectedDamageDataItem.value = null
    }

    fun updateSelectedItems(damageData: DamageData) {
        if (checkIfItemsIdsAreEqual(damageData)) {
            mutableSelectedDamageDataItemFromMap.value?.let { selectDamageData(it) }
        }
    }

    private fun checkIfItemsIdsAreEqual(damageData: DamageData): Boolean {
        val selectedItemId = mutableSelectedDamageDataItem.value?.id
        val selectedItemFromMapId = mutableSelectedDamageDataItemFromMap.value?.id
        if (selectedItemId == selectedItemFromMapId) {
            return true
        }
        return false
    }

    private fun createFilterString(): String {
        return "<Filter>" +
                "<PropertyIsEqualTo>" +
                "<PropertyName>pouzivatel</PropertyName>" +
                "<Literal>dano</Literal>" +
                "</PropertyIsEqualTo>" +
                "</Filter>"
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor("dano", "test"))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

//    fun fetchUserData() {
//        val okHttpClient: OkHttpClient = Utils.createOkHttpClient()
//        val service = GeoserverRetrofitBuilder.createServiceWithGsonFactory(okHttpClient)
//        val filterString = createFilterString()
//        job = CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val response = service.getUserData("meno:dano")
//                // val response = service.getUsingUrlFilter(filterString)
//
//                withContext(Dispatchers.Main) {
//                    if (response.isSuccessful) {
//                        val res: UsersData? = response.body()
//                        val list = mutableListOf<DamageData>()
//                        if (res != null) {
//                            var i = 0
//
//                            res.features.forEach {
//                                val listOfGeopoints = createListOfGeopoints(res, i)
//                                i++
//                                val damageData = it.properties
//                                listOfGeopoints.removeLast()
//                                damageData.coordinates = listOfGeopoints
//                                list.add(damageData)
//                            }
//                        }
//
//                        damageDataList.value = list
//                    } else {
//                        Log.d("MODEL", "Error: ${response.message()}")
//                        onError(response.message())
//                    }
//                }
//            } catch (e: Throwable) {
//                if (isNetworkAvailable.value == true) {
//                    onError(e.toString())
//                }
//            }
//        }
//    }



    private fun createListOfGeopoints(usersData: UsersData, index: Int): MutableList<GeoPoint> {
        val listOfGeopoints = mutableListOf<GeoPoint>()
        usersData.features[index].geometry.coordinates.forEach {
            it.forEach { p ->
                listOfGeopoints.add(GeoPoint(p[1], p[0]))
            }
        }
        return listOfGeopoints
    }

    fun fetchPhotos(item: DamageData) {
        val okHttpClient: OkHttpClient = createOkHttpClient()
        val service = GeoserverRetrofitBuilder.createServiceWithGsonFactory(okHttpClient)
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.getPhotos("id:${item.unique_id}")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val res: UsersData? = response.body()
                        val listOfStringsOfPhotos = mutableListOf<String>()
                        val listOfIndexes = mutableListOf<Int>()
                        res?.features?.forEach { listOfStringsOfPhotos.add(it.properties.foto) }
                        res?.features?.forEach { listOfIndexes.add(it.properties.id) }
                        // mutableStringsOfPhotosList = MutableLiveData<List<String>>()
                        mutableStringsOfPhotosList.value = listOfStringsOfPhotos
                        mutableIndexesOfPhotosList.value = listOfIndexes
                    } else {
                        Log.d("MODEL", "Error: ${response.message()}")
                        onError(response.message())
                    }
                }
            } catch (e: Throwable) {
                if (isNetworkAvailable.value == true) {
                    onError(e.toString())
                }
            }
        }
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

    suspend fun deleteItem(damageData: DamageData): Boolean {
        val deleteRecordString = createDeleteRecordTransactionString(damageData)
        val requestBody = Utils.createRequestBody(deleteRecordString)
        val deferredBoolean: Deferred<Boolean> = CoroutineScope(Dispatchers.Main).async<Boolean> {
            val deferred = async { postToGeoserver(requestBody) }
            deferred.await()
        }
        return deferredBoolean.await()
    }

//    override suspend fun postToGeoserver(requestBody: RequestBody): Boolean {
//        val deferredBoolean: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>()
//        val service = GeoserverRetrofitBuilder.createServiceWithScalarsFactory(Utils.createOkHttpClient())
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val response = service.postToGeoserver(requestBody)
//                withContext(Dispatchers.Main) {
//                    if (response.isSuccessful) {
//                        // binding.progressBar.visibility = View.GONE
//                        val r: String? = response.body()
//
//                        Log.d("MODELL", "Success!!!!!!!!!!!!!!!!!!!")
//                        if (r != null) {
//                            Log.d("MODELL", r)
//                        }
//                        if (r != null && r.contains("SUCCESS")) {
//                            Log.d("MODELL", "Fotky úspešné....")
//                        }
//                        deferredBoolean.complete(true)
//                    } else {
//                        Log.d("MODELL", "Error: ${response.message()}")
//                        deferredBoolean.complete(false)
//                    }
//                }
//            } catch (e: Throwable) {
//                if (isNetworkAvailable.value == true) {
//                    onError(e.toString())
//                }
//            }
//        }
//        return deferredBoolean.await()
//    }

    public override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}