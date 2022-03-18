package com.android.monitorporastov

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.converter.scalars.ScalarsConverterFactory


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
}
