package com.android.monitorporastov

import com.android.monitorporastov.model.UsersData
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*

interface RetroInterface {
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
//-------------------------
object RetroService {
    private const val BASE_URL = "http://services.skeagis.sk:7492/geoserver/"

    fun getServiceWithGsonFactory(okHttpClientInterceptor: OkHttpClient): RetroInterface {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroInterface::class.java)
    }

    fun getServiceWithScalarsFactory(okHttpClientInterceptor: OkHttpClient): RetroInterface {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClientInterceptor)
            .addConverterFactory(ScalarsConverterFactory.create())  // tu je rozdiel
            .build()
            .create(RetroInterface::class.java)
    }
}


//val okHttpClientInterceptor: OkHttpClient = OkHttpClient.Builder()
//    .addInterceptor(BasicAuthInterceptor("dano", "test"))
//    .build()

//-------------------------
data class Stat(
    @SerializedName("name")       /* -> */  val countryName: String?,
    @SerializedName("capital")    /* -> */  val capital: String?,
    @SerializedName("flagPNG")    /* -> */  val flag: String?,
    @SerializedName("latlng")     /* -> */  val latlng: Array<Float>?,
    @SerializedName("borders")    /* -> */  val borders: List<String>?,
    @SerializedName("alpha3Code") /* -> */  val code: String?,
    val demonym: String?,
    val population: Int?,
    val region: String?,
    val subregion: String?,
    val altSpellings: List<String>?,
    val area: Double?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Stat

        if (countryName != other.countryName) return false
        if (capital != other.capital) return false
        if (flag != other.flag) return false
        if (latlng != null) {
            if (other.latlng == null) return false
            if (!latlng.contentEquals(other.latlng)) return false
        } else if (other.latlng != null) return false
        if (borders != other.borders) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = countryName?.hashCode() ?: 0
        result = 31 * result + (capital?.hashCode() ?: 0)
        result = 31 * result + (flag?.hashCode() ?: 0)
        result = 31 * result + (latlng?.contentHashCode() ?: 0)
        result = 31 * result + (borders?.hashCode() ?: 0)
        result = 31 * result + (code?.hashCode() ?: 0)
        return result
    }
}
/*
  {
    "alpha2Code": "SK",
    "alpha3Code": "SVK",
    "altSpellings": [
      "SK",
      "Slovak Republic",
      "Slovensk\u00e1 republika"
    ],
    "area": 49037,
    "borders": [
      "AUT",
      "CZE",
      "HUN",
      "POL",
      "UKR"
    ],
    "callingCodes": [
      "421"
    ],
    "capital": "Bratislava",
    "currencies": [
      {
        "code": "EUR",
        "name": "Euro",
        "symbol": "\u20ac"
      }
    ],
    "demonym": "Slovak",
        "flagPNG": "https://dai.fmph.uniba.sk/courses/VMA/vlajky/svk.png",
    "gini": 26.0,
    "languages": [
      {
        "iso639_1": "sk",
        "iso639_2": "slk",
        "name": "Slovak",
        "nativeName": "sloven\u010dina"
      }
    ],
    "latlng": [
      48.66666666,
      19.5
    ],
    "name": "Slovakia",
    "nativeName": "Slovensko",
    "numericCode": "703",
    "population": 5426252,
    "region": "Europe",
    "regionalBlocs": [
      {
        "acronym": "EU",
        "name": "European Union"
      }
    ],
    "subregion": "Eastern Europe",
    "timezones": [
      "UTC+01:00"
    ],
    "topLevelDomain": [
      ".sk"
    ],
    "translations": {
      "br": "Eslov\u00e1quia",
      "de": "Slowakei",
      "es": "Rep\u00fablica Eslovaca",
      "fa": "\u0627\u0633\u0644\u0648\u0627\u06a9\u06cc",
      "fr": "Slovaquie",
      "hr": "Slova\u010dka",
      "it": "Slovacchia",
      "ja": "\u30b9\u30ed\u30d0\u30ad\u30a2",
      "nl": "Slowakije",
      "pt": "Eslov\u00e1quia"
    },
    "cioc": "SVK"
  },
 */