package com.android.monitorporastov

import com.android.monitorporastov.model.UsersData
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GeoserverService {
    //    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=porasty_pouzivatel_sql&outputFormat=application/json&filter=<Filter><And><PropertyIsEqualTo><PropertyName>pouzivatel</PropertyName><Literal>{user}</Literal></PropertyIsEqualTo></And></Filter>")
    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=porasty_pouzivatel_sql&outputFormat=application/json&sortBy=id")
    suspend fun getUserData(@Query("viewparams", encoded = true) user: String): Response<UsersData>

    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=fotografie_sql&outputFormat=application/json")
    suspend fun getPhotos(@Query("viewparams", encoded = true) uniqueId: String): Response<UsersData>

    @GET("wfs?service=WFS&version=2.0.0&request=GetFeature&typeNames=porasty_pouzivatel_sql&outputFormat=application/json&&srsName=urn:ogc:def:crs:EPSG::4326")
    suspend fun getDetail(@Query("bbox", encoded = true) bbox: String): Response<UsersData>

    @POST("wfs?service=WFS")
    suspend fun postToGeoserver(@Body body: RequestBody): Response<String>

    @GET("wfs?service=WFS&version=1.0.0&request=GetFeature&typeNames=porasty&outputFormat=application/json")
    suspend fun getUsingUrlFilter(@Query("filter", encoded = true) filter: String): Response<UsersData>

}