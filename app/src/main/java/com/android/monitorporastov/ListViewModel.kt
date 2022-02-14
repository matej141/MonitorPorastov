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
    val staty = MutableLiveData<List<DamageData>>()
    private val mutableSelectedStat = MutableLiveData<DamageData>()
    private val mutableNewItem = MutableLiveData<DamageData>()
    val selectedItem: LiveData<DamageData> get() = mutableSelectedStat
    val newItem:LiveData<DamageData> get() = mutableNewItem

    fun selectStat(item: DamageData) {
        mutableSelectedStat.value = item
    }

    fun saveNewItem(item: DamageData) {
        mutableNewItem.value = item
    }

    fun fetch() {
        val okHttpClientInterceptor: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor("dano", "test"))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
        val service = RetroService.getServiceWithGsonFactory(okHttpClientInterceptor)
        job = CoroutineScope(Dispatchers.IO).launch {
            val response = service.get("meno:dano")
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val res: UsersData? = response.body()
                    val list = mutableListOf<DamageData>()
                    res?.features?.forEach {list.add(it.properties)}
                    staty.value = list
                }
                else
                    Log.d("MODEL", "Error: ${response.message()}")
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}