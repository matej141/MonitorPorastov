package com.android.monitorporastov.fragments.viewmodels

import android.location.Location
import android.util.Log
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.monitorporastov.BuildConfig
import com.android.monitorporastov.Event
import com.android.monitorporastov.R
import com.android.monitorporastov.WMSTileSourceRepaired
import com.android.monitorporastov.fragments.viewmodels.base.MapBaseViewModel
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.BPEJLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.C_parcelLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.E_parcelLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.JPRLLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.LPISLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.defaultLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.ortofotoLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.userDamagesLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice10mLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice50mLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.watercourseLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.getCapabilitiesUrl
import kotlinx.coroutines.*
import okhttp3.Credentials
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.wms.WMSEndpoint
import org.osmdroid.wms.WMSParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MapFragmentViewModel : MapBaseViewModel() {

    private val _lastLocation = MutableLiveData<Location>()
    val lastLocation: LiveData<Location> = _lastLocation

    private val _displayDefaultLayer = MutableLiveData<Event<Boolean>>()
    val displayDefaultLayer: LiveData<Event<Boolean>> = _displayDefaultLayer

    private val _wmsTileSourceForBaseLayer = MutableLiveData<Event<WMSTileSourceRepaired?>>()
    val wmsTileSourceForBaseLayer: LiveData<Event<WMSTileSourceRepaired?>> = _wmsTileSourceForBaseLayer

    private val _wmsTileSourceForMapLayer = MutableLiveData<Event<Pair<String, WMSTileSourceRepaired?>>>()
    val wmsTileSourceForMapLayer: LiveData<Event<Pair<String, WMSTileSourceRepaired?>>> =
        _wmsTileSourceForMapLayer

    private val _wmsTileSourceForUserData = MutableLiveData<Event<WMSTileSourceRepaired?>>()
    val wmsTileSourceForUserData: LiveData<Event<WMSTileSourceRepaired?>> =
        _wmsTileSourceForUserData

    private val _layerToDeleteString = MutableLiveData<Event<String>>()
    val layerToDeleteString: LiveData<Event<String>> = _layerToDeleteString

    private val _noInternetForSelectLayers = MutableLiveData<Boolean>()
    val noInternetForSelectLayers: LiveData<Boolean> = _noInternetForSelectLayers

    private fun setNoInternetForSelectLayers() {
        _noInternetForSelectLayers.postValue(true)
    }

    var mapOrientation: Float = 0.0F
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var zoomLevel: Double = 0.0
    var mapIsInitialised = false

    var baseMapLayerName = ""
    var selectedBaseLayer: WMSTileSourceRepaired? = null
    var mapLayersList = mutableListOf<Pair<String, WMSTileSourceRepaired>>()
    var loadedBaseLayer = false
    var loadedMapLayer = false
    var selectedLayersList = mutableListOf<String>()

    fun setLastLocation(location: Location) {
        _lastLocation.value = location
    }

    fun getLastLocation(): Location? {
        return _lastLocation.value
    }

    fun setThatMapWasInitialised() {
        mapIsInitialised = true
    }

    private fun setThatShouldDisplayDefaultLayer() {
        _displayDefaultLayer.value = Event(true)
    }

    private fun setWMSTileSourceForBaseLayer(wmsTileSource: WMSTileSourceRepaired?) {
        _wmsTileSourceForBaseLayer.postValue(Event(wmsTileSource))
    }

    private fun setWMSTileSourceForMapLayer(
        layerName: String,
        wmsTileSource: WMSTileSourceRepaired?,
    ) {
        _wmsTileSourceForMapLayer.postValue(Event(Pair(layerName, wmsTileSource)))
    }

    private fun setWMSTileSourceForUserData(wmsTileSource: WMSTileSourceRepaired?) {
        _wmsTileSourceForUserData.postValue(Event(wmsTileSource))
    }

    private fun setLayerToDeleteString(layerToDeleteString: String) {
        _layerToDeleteString.value = Event(layerToDeleteString)
    }

    fun saveStateOfMap(mapView: MapView) {
        mapOrientation = mapView.mapOrientation
        latitude = mapView.mapCenter.latitude
        longitude = mapView.mapCenter.longitude
        zoomLevel = mapView.zoomLevelDouble
    }

    fun restartStateOfMap(mMap: MapView) {
        mMap.controller.setZoom(zoomLevel)
        mMap.setMapOrientation(mapOrientation, false)
        mMap.setExpectedCenter(GeoPoint(latitude, longitude))
    }

    fun setConfiguration() {
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        setAuthorizationInConfiguration()
    }

    private fun setAuthorizationInConfiguration() {
        if (usernameCharArray.value == null || passwordCharArray.value == null) {
            reportThatUnauthorisedErrorIsOccurred()
            return
        }
        val credential =
            Credentials.basic(String(usernameCharArray.value!!), String(passwordCharArray.value!!))
        Configuration.getInstance().additionalHttpRequestProperties["Authorization"] =
            credential
    }

    suspend fun getWMSTileSourceForBaseLayer(layerName: String) {
        loadedBaseLayer = false
        val wmsTileSource = getWmsTileSource(layerName)
        setWMSTileSourceForBaseLayer(wmsTileSource)
        if (wmsTileSource != null) {
            selectedBaseLayer = wmsTileSource
            loadedBaseLayer = true
            selectedLayersList.add(layerName)
            setLoading(false)
        }
    }

    suspend fun getWMSTileSourceForMapLayer(layerName: String) {
        loadedMapLayer = false
        val wmsTileSource = getWmsTileSource(layerName)
        setWMSTileSourceForMapLayer(layerName, wmsTileSource)
        if (wmsTileSource != null) {
            mapLayersList.add(Pair(layerName, wmsTileSource))
            loadedMapLayer = true
            selectedLayersList.add(layerName)
            setLoading(false)
        }
    }

    suspend fun getWMSTileSourceForUserData(layerName: String) {
        val wmsTileSource = getWmsTileSourceOnRepeat(layerName)
        setWMSTileSourceForUserData(wmsTileSource)
        if (wmsTileSource != null) {
            setLoading(false)
        }

    }

    private suspend fun getWmsTileSourceOnRepeat(layerName: String): WMSTileSourceRepaired? {
        val deferredSource = CompletableDeferred<WMSTileSourceRepaired?>()
        withContext(Dispatchers.Main) {
            observeNetworkState {
                setLoading(true)
                val wmsTileSource = createWMSTileSource(layerName)
                deferredSource.complete(wmsTileSource)
            }
        }
        return deferredSource.await()
    }

    private suspend fun getWmsTileSource(layerName: String): WMSTileSourceRepaired? {
        val deferredSource = CompletableDeferred<WMSTileSourceRepaired?>()
        if (isNetworkAvailable.value == false) {
            setNoInternetForSelectLayers()
            return null
        }
        val wmsTileSource = createWMSTileSource(layerName)
        deferredSource.complete(wmsTileSource)
        return deferredSource.await()
    }

    private suspend fun createWMSTileSource(layerName: String): WMSTileSourceRepaired? {
        val deferredSource = CompletableDeferred<WMSTileSourceRepaired?>()
        val getCapabilitiesUrl = getCapabilitiesUrl
        launch {
            setLoading(true)
            val wmsEndpoint: WMSEndpoint? = loadWmsEndpoint(getCapabilitiesUrl)

            if (wmsEndpoint != null) {
                val wmsTileSource = getSource(wmsEndpoint, layerName)
                deferredSource.complete(wmsTileSource)
            } else {
                deferredSource.complete(null)
            }
        }

        return deferredSource.await()
    }

    private fun getSource(wmsEndpoint: WMSEndpoint, layerName: String): WMSTileSourceRepaired? {
        return try {
            val layer = wmsEndpoint.layers.filter { it.name == layerName }[0]
            if (usernameCharArray.value == null) {
                reportThatUnauthorisedErrorIsOccurred()
                return null
            }
            val source =
                WMSTileSourceRepaired.createLayer(wmsEndpoint,
                    layer,
                    String(usernameCharArray.value!!))
            source
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    private fun loadWmsEndpoint(urlString: String): WMSEndpoint? {
        var wmsEndpoint: WMSEndpoint? = null
        Log.d("AAAAAAAAAA", urlString)
        try {
            val c: HttpURLConnection = URL(urlString).openConnection() as HttpURLConnection
            val credential = Credentials.basic(String(usernameCharArray.value!!),
                String(passwordCharArray.value!!))
            c.setRequestProperty("Authorization", credential)
            val inputStream: InputStream = c.inputStream
            wmsEndpoint = WMSParser.parse(inputStream)
            inputStream.close()
            c.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            e.message?.let { onError(it) }
        }

        return wmsEndpoint
    }

    fun loadLayer(item: MenuItem) {
        loadBaseLayer(item)
        loadMapLayer(item)
    }

    private fun loadBaseLayer(item: MenuItem) {
        val id = item.itemId

        if (id == R.id.menu_default_map && baseMapLayerName != defaultLayerName) {
            setThatShouldDisplayDefaultLayer()
            item.isChecked = true
            baseMapLayerName = defaultLayerName
            clearBaseLayer()
            return
        }
        if (id == R.id.menu_ortofoto && baseMapLayerName != ortofotoLayerName) {

            launch {
                getWMSTileSourceForBaseLayer(ortofotoLayerName)
                withContext(Dispatchers.Main) {
                    checkIfCreatingBaseLayerWasSuccessful(item)
                }
            }
        }
    }

    private fun loadMapLayer(item: MenuItem) {
        val id = item.itemId
        val layerName = menuIdToLayerName(id)

        if (layerName.isEmpty()) {
            return
        }

        if (item.isChecked) {
            item.isChecked = false
            setLayerToDeleteString(layerName)
            mapLayersList.removeAll { it.first == layerName }
            return
        }

        launch {
            getWMSTileSourceForMapLayer(layerName)
            withContext(Dispatchers.Main) {
                checkIfCreatingMapLayerWasSuccessful(item)
            }

        }
    }

    private fun clearBaseLayer() {
        selectedBaseLayer = null
    }

    private fun checkIfCreatingBaseLayerWasSuccessful(item: MenuItem) {
        if (loadedBaseLayer) {
            item.isChecked = true
            baseMapLayerName = ortofotoLayerName
        }
    }

    private fun checkIfCreatingMapLayerWasSuccessful(item: MenuItem) {
        if (loadedMapLayer) {
            item.isChecked = true
        }
    }

    private fun menuIdToLayerName(id: Int): String {
        var layerName = ""

        when (id) {
            R.id.menu_BPEJ -> {
                layerName = BPEJLayerName
            }
            R.id.menu_C_parcel -> {
                layerName = C_parcelLayerName
            }
            R.id.menu_E_parcel -> {
                layerName = E_parcelLayerName
            }
            R.id.menu_LPIS -> {
                layerName = LPISLayerName
            }
            R.id.menu_JPRL -> {
                layerName = JPRLLayerName
            }
            R.id.menu_watercourse -> {
                layerName = watercourseLayerName
            }
            R.id.menu_vrstevnice10m -> {
                layerName = vrstevnice10mLayerName
            }
            R.id.menu_vrstevnice50m -> {
                layerName = vrstevnice50mLayerName
            }
        }
        return layerName
    }

    private fun clearMapCache() {
        val sqlTileWriter = SqlTileWriter()
        sqlTileWriter.purgeCache(userDamagesLayerName)
    }

    fun loadUserPolygons() {
        clearMapCache()
        setLayerToDeleteString(userDamagesLayerName)
        launch {
            getWMSTileSourceForUserData(userDamagesLayerName)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}