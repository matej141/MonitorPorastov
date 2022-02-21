package com.android.monitorporastov

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData

import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

class ListViewModel: ViewModel() {
    //private val service = RetroService.get()
    lateinit var job: Job
    val damageData = MutableLiveData<List<DamageData>>()
    private val mutableSelectedDamageData = MutableLiveData<DamageData>()
    private val mutableNewItem = MutableLiveData<DamageData>()
    val selectedDamageDataItem: LiveData<DamageData> get() = mutableSelectedDamageData
    val newItem:LiveData<DamageData> get() = mutableNewItem
    val stringsOfPhotosList = MutableLiveData<List<String>>()

    fun selectDamageData(item: DamageData) {
        mutableSelectedDamageData.value = item
    }

    fun saveNewItem(item: DamageData) {
        mutableNewItem.value = item
    }

    fun deletePhotos() {
        stringsOfPhotosList.value = emptyList()
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

    fun fetchUserData() {
        val okHttpClient: OkHttpClient = Utils.createOkHttpClient()
        val service = RetroService.getServiceWithGsonFactory(okHttpClient)
        val filterString = createFilterString()
        job = CoroutineScope(Dispatchers.IO).launch {
            val response = service.getUserData("meno:dano")
            // val response = service.getUsingUrlFilter(filterString)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val res: UsersData? = response.body()
                    val list = mutableListOf<DamageData>()
                    res?.features?.forEach {list.add(it.properties)}
                    damageData.value = list
                }
                else
                    Log.d("MODEL", "Error: ${response.message()}")
            }
        }
    }

    fun fetchPhotos(item: DamageData) {
        val okHttpClient: OkHttpClient = createOkHttpClient()
        val service = RetroService.getServiceWithGsonFactory(okHttpClient)
        job = CoroutineScope(Dispatchers.IO).launch {
            val response = service.getPhotos("id:${item.unique_id}")
            // val response = service.getUsingUrlFilter(filterString)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val res: UsersData? = response.body()
                    val list = mutableListOf<String>()
                    res?.features?.forEach {list.add(it.properties.foto)}
                    stringsOfPhotosList.value = list
                }
                else
                    Log.d("MODELL", "Error: ${response.message()}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}