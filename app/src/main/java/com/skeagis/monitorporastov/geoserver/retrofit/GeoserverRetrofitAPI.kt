package com.skeagis.monitorporastov.geoserver.retrofit

import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.DatabasePropertiesName.propertyNameOfDatetime
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.damagesLayer
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.userDamagesLayer
import com.skeagis.monitorporastov.model.UsersData
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GeoserverRetrofitAPI {

    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=$userDamagesLayer&outputFormat=application/json&sortBy=$propertyNameOfDatetime D")
    suspend fun getUserData(@Query("viewparams", encoded = true) user: String): Response<UsersData>

    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=fotografie_sql&outputFormat=application/json")
    suspend fun getPhotos(@Query("viewparams", encoded = true) uniqueId: String): Response<UsersData>

    @POST("wfs?service=WFS")
    suspend fun postToGeoserver(@Body body: RequestBody): Response<String>

    @GET("wfs?service=WFS&version=1.1.0&request=GetFeature&typeNames=$damagesLayer&outputFormat=application/json&sortBy=$propertyNameOfDatetime D")
    suspend fun getDetail(@Query("filter", encoded = true) filter: String): Response<UsersData>

    @GET(".")
    suspend fun login(): Response<String>
}




// pri login() bola chyba: https://stackoverflow.com/questions/40062564/retrofit-error-missing-either-get-url-or-url-parameter