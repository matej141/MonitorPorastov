package com.android.monitorporastov.geoserver.retrofit

import com.android.monitorporastov.BasicAuthInterceptor
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.basicUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit


object GeoserverRetrofitBuilder {
    private const val BASE_URL = basicUrl

    fun createServiceWithGsonFactory(okHttpClientInterceptor: OkHttpClient): GeoserverRetrofitAPI {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoserverRetrofitAPI::class.java)
    }

    fun createServiceWithScalarsFactory(okHttpClientInterceptor: OkHttpClient): GeoserverRetrofitAPI {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(GeoserverRetrofitAPI::class.java)
    }

    private fun createOkHttpClient(username: String, password: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(username, password))
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    fun getServiceWithScalarsFactory(
        usernameCharArray: CharArray,
        passwordCharArray: CharArray,
    ): GeoserverRetrofitAPI {
        val okHttpClient = createOkHttpClient(String(usernameCharArray),
            String(passwordCharArray))
        return createServiceWithScalarsFactory(okHttpClient)
    }

    fun getServiceWithGsonFactory(
        usernameCharArray: CharArray,
        passwordCharArray: CharArray,
    ): GeoserverRetrofitAPI {
        val okHttpClient = createOkHttpClient(String(usernameCharArray),
            String(passwordCharArray))
        return createServiceWithGsonFactory(okHttpClient)
    }


}
