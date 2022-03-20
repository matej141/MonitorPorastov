package com.android.monitorporastov

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit


//-------------------------
object RetroService {
    private const val BASE_URL = "http://services.skeagis.sk:7492/geoserver/"

    fun getServiceWithGsonFactory(okHttpClientInterceptor: OkHttpClient): GeoserverService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoserverService::class.java)
    }

    fun getServiceWithScalarsFactory(okHttpClientInterceptor: OkHttpClient): GeoserverService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(ScalarsConverterFactory.create())  // tu je rozdiel
            .build()
            .create(GeoserverService::class.java)
    }

    fun createOkHttpClient(username: String, password: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(username, password))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
}
