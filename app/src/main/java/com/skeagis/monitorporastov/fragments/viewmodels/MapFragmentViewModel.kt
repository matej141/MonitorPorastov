package com.skeagis.monitorporastov.fragments.viewmodels

import android.location.Location
import android.util.Log
import android.view.MenuItem
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.skeagis.monitorporastov.*
import com.skeagis.monitorporastov.fragments.viewmodels.base_view_models.MapBaseViewModel
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.C_parcelLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.E_parcelLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.LPISLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.defaultLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.ortofotoLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.userDamagesLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice50mLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.watercourseLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.UrlsNames.getCapabilitiesUrl
import com.skeagis.monitorporastov.geoserver.factories.GeoserverDataFilterStringsFactory.createFilterStringByIntersectAndUsername
import com.skeagis.monitorporastov.geoserver.retrofit.GeoserverRetrofitAPI
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.model.UsersData
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.BPEJLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.JPRLLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.buildingsListLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.huntingGroundsLayerName
import kotlinx.coroutines.*
import okhttp3.Credentials
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.wms.WMSEndpoint
import org.osmdroid.wms.WMSParser
import retrofit2.Response
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MapFragmentViewModel : MapBaseViewModel() {

    companion object {
        private const val MAP_FRAGMENT_TAG = "MapFragment"
    }

    private val _lastLocation = MutableLiveData<Location>()
    val lastLocation: LiveData<Location> = _lastLocation

    private val _displayDefaultLayer = MutableLiveData<Event<Boolean>>()
    val displayDefaultLayer: LiveData<Event<Boolean>> = _displayDefaultLayer

    private val _wmsTileSourceForBaseLayer = MutableLiveData<Event<WMSTileSourceRepaired?>>()
    val wmsTileSourceForBaseLayer: LiveData<Event<WMSTileSourceRepaired?>> =
        _wmsTileSourceForBaseLayer

    private val _wmsTileSourceForMapLayer =
        MutableLiveData<Event<Pair<String, WMSTileSourceRepaired?>>>()
    val wmsTileSourceForMapLayer: LiveData<Event<Pair<String, WMSTileSourceRepaired?>>> =
        _wmsTileSourceForMapLayer

    private val _wmsTileSourceForUserData = MutableLiveData<Event<WMSTileSourceRepaired?>>()
    val wmsTileSourceForUserData: LiveData<Event<WMSTileSourceRepaired?>> =
        _wmsTileSourceForUserData

    private val _layerToDeleteString = MutableLiveData<Event<String>>()
    val layerToDeleteString: LiveData<Event<String>> = _layerToDeleteString

    private val _noInternetForSelectLayers = MutableLiveData<Event<Boolean>>()
    val noInternetForSelectLayers: LiveData<Event<Boolean>> = _noInternetForSelectLayers

    private val _geopoints = MutableLiveData<List<GeoPoint>?>()
    val geopoints: LiveData<List<GeoPoint>?> = _geopoints

    private val _actualArea = MutableLiveData<Double>()
    val actualArea: LiveData<Double> = _actualArea

    private val _actualPerimeter = MutableLiveData<Double>()
    val actualPerimeter: LiveData<Double> = _actualPerimeter

    private val _manualSelecting = MutableLiveData<Boolean>()
    val manualSelecting: LiveData<Boolean> = _manualSelecting

    private val _gpsSelecting = MutableLiveData<Boolean>()
    val gpsSelecting: LiveData<Boolean> = _gpsSelecting

    private val _isDefaultModeOfMap = MutableLiveData<Event<Boolean>>()
    val isDefaultModeOfMap: LiveData<Event<Boolean>> = _isDefaultModeOfMap

    private val _markerDeletingMode = MutableLiveData<Boolean>()
    val markerDeletingMode: LiveData<Boolean> = _markerDeletingMode

    private val _detailModeShown = MutableLiveData<Boolean>()
    val detailModeShown: LiveData<Boolean> = _detailModeShown

    private val _markersToAddToMap = MutableLiveData<Event<List<Marker>>>()
    val markersToAddToMap: LiveData<Event<List<Marker>>> = _markersToAddToMap

    private val _markersToRemoveFromMap = MutableLiveData<Event<List<Marker>>>()
    val markersToRemoveFromMap: LiveData<Event<List<Marker>>> = _markersToRemoveFromMap

    private val _detailDamageData = MutableLiveData<DamageData?>()
    val detailDamageData: LiveData<DamageData?> = _detailDamageData

    private val _geopointsOfSelectedPolygon = MutableLiveData<List<GeoPoint>?>()
    val geopointsOfSelectedPolygon: LiveData<List<GeoPoint>?> = _geopointsOfSelectedPolygon

    private val _loadedMapLayerWithUserData = MutableLiveData<Boolean>()
    val loadedMapLayerWithUserData: LiveData<Boolean> = _loadedMapLayerWithUserData

    private val _newPolygonMarkersHistory = MutableLiveData<MutableList<List<Marker>>>()
    val newPolygonMarkersHistory: LiveData<MutableList<List<Marker>>> = _newPolygonMarkersHistory

    private val loadedMapLayerWithUserDataObserver = Observer<Boolean> { loaded ->
        sharedViewModel?.setIfLoadedMapLayerWithUserData(loaded)
    }

    override fun setObservers() {
        super.setObservers()
        loadedMapLayerWithUserData.observeForever(loadedMapLayerWithUserDataObserver)
    }

    override fun setToViewModel() {
        super.setToViewModel()
        sharedViewModel?.loadedMapLayerWithUserData?.value?.let {
            setIfLoadedMapLayerWithUserData(it)
        }
    }

    private var mapOrientation: Float = 0.0F
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var zoomLevel: Double = 0.0
    var mapIsInitialised = false

    private var baseMapLayerName = ""
    var selectedBaseLayer: WMSTileSourceRepaired? = null
    var mapLayersList = mutableListOf<Pair<String, WMSTileSourceRepaired>>()
    var userDataLayer: WMSTileSourceRepaired? = null
    private var loadedBaseLayer = false
    private var loadedMapLayer = false

    var selectedLayersList = mutableListOf<String>()
    var polygonMarkersList = mutableListOf<Marker>()
    var listOfGeopointsOfSelectedPolygon: MutableList<GeoPoint> = mutableListOf()
    private var selectedDamageRecord: DamageData? = null

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

    private fun saveChangesToHistory() {
        if (_newPolygonMarkersHistory.value == null) {
            _newPolygonMarkersHistory.value = mutableListOf()
        }
        val list = _newPolygonMarkersHistory.value
        if (list != null) {
            list.add(polygonMarkersList.toMutableList())
            _newPolygonMarkersHistory.value = list!!
        }

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

    private fun setGeopoints(geopoints: List<GeoPoint>) {
        _geopoints.value = geopoints
    }

    private fun setActualArea(area: Double) {
        _actualArea.value = area
    }

    private fun setActualPerimeter(perimeter: Double) {
        _actualPerimeter.value = perimeter
    }

    private fun setManualSelectingValue(value: Boolean) {
        _manualSelecting.value = value
    }

    private fun setGPSSelectingValue(value: Boolean) {
        _gpsSelecting.value = value
    }

    private fun setDefaultModeOfMapValue() {
        _isDefaultModeOfMap.value = Event(true)
    }

    fun setForManualSelecting() {
        setManualSelectingValue(true)
        setGPSSelectingValue(false)

    }

    fun setForGPSSelecting() {
        setManualSelectingValue(false)
        setGPSSelectingValue(true)
    }

    fun setDefaultModeOfMap() {
        setManualSelectingValue(false)
        setGPSSelectingValue(false)
        setDefaultModeOfMapValue()
    }

    fun setMarkerDeletingMode() {
        if (_markerDeletingMode.value == null || _markerDeletingMode.value == false) {
            _markerDeletingMode.value = true
        } else if (_markerDeletingMode.value == true) {
            _markerDeletingMode.value = false
        }
    }

    private fun setMarkersToAddToMap(markers: List<Marker>) {
        _markersToAddToMap.value = Event(markers)
    }

    private fun setMarkersToRemoveFromMap(markers: List<Marker>) {
        _markersToRemoveFromMap.value = Event(markers)
    }

    private fun setDetailMode(value: Boolean) {
        _detailModeShown.value = value
    }

    private fun setDetailDamageData(data: DamageData?) {
        _detailDamageData.value = data
    }

    private fun setGeopointsOfSelectedPolygon(geopoints: List<GeoPoint>?) {
        _geopointsOfSelectedPolygon.value = geopoints
    }

    private fun setNoInternetForSelectLayers() {
        _noInternetForSelectLayers.postValue(Event(true))
    }

    fun setIfLoadedMapLayerWithUserData(value: Boolean) {
        _loadedMapLayerWithUserData.value = value
    }

    fun addMarkerToPolygonOnMap(marker: Marker) {
        saveChangesToHistory()
        polygonMarkersList.add(marker)

    }

    fun removeMarker(marker: Marker) {
        saveChangesToHistory()
        polygonMarkersList.remove(marker)
    }

    fun clearNewPolygonData() {
        polygonMarkersList.clear()
        _newPolygonMarkersHistory.value?.clear()
        _geopoints.value = null
    }

    private suspend fun getWMSTileSourceForBaseLayer(layerName: String) {
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

    private suspend fun getWMSTileSourceForMapLayer(layerName: String) {
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

    private suspend fun getWMSTileSourceForUserData(layerName: String) {
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
                if (loadedMapLayerWithUserData.value == true) {
                    deferredSource.complete(null)
                } else {
                    setLoading(true)
                    val wmsTileSource = createWMSTileSource(layerName)
                    deferredSource.complete(wmsTileSource)
                }
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
        launch {
            setLoading(true)
            val wmsEndpoint: WMSEndpoint? = loadWmsEndpoint()

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

    private fun loadWmsEndpoint(): WMSEndpoint? {
        var wmsEndpoint: WMSEndpoint? = null
        try {
            val c: HttpURLConnection = URL(getCapabilitiesUrl).openConnection() as HttpURLConnection
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
            selectedLayersList.remove(ortofotoLayerName)
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
            selectedLayersList.remove(layerName)
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

            R.id.menu_hunting_grounds -> {
                layerName = huntingGroundsLayerName
            }

            R.id.menu_watercourse -> {
                layerName = watercourseLayerName
            }
//            R.id.menu_vrstevnice10m -> {
//                layerName = vrstevnice10mLayerName
//            }
            R.id.menu_vrstevnice50m -> {
                layerName = vrstevnice50mLayerName
            }

            R.id.menu_buildings -> {
                layerName = buildingsListLayerName
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

    private fun createListOfGeopointsFromMarkers(): List<GeoPoint> {
        return polygonMarkersList.map {
            GeoPoint(it.position.latitude,
                it.position.longitude)
        }
    }

    private fun createLatLngListFromGeopointsList(geopoints: List<GeoPoint>): List<LatLng> {
        return geopoints.map { LatLng(it.latitude, it.longitude) }
    }

    fun createDamagePolygon() {
        val geoPointsList: List<GeoPoint> = createListOfGeopointsFromMarkers()
        setGeopoints(geoPointsList)
        computeAreAndPerimeter(geoPointsList)
    }

    private fun computeAreAndPerimeter(geopoints: List<GeoPoint>) {
        val latLngList = createLatLngListFromGeopointsList(geopoints)
        computeArea(latLngList)
        computePerimeter(latLngList)
    }

    private fun computeArea(latLngList: List<LatLng>) {
        val area = SphericalUtil.computeArea(latLngList)
        setActualArea(area)
    }

    private fun computePerimeter(latLngList: List<LatLng>) {
        val perimeter = SphericalUtil.computeLength(latLngList)
        setActualPerimeter(perimeter)
    }

    fun undoMap() {
        val newPolygonMarkersHistoryList = _newPolygonMarkersHistory.value
        if (newPolygonMarkersHistoryList.isNullOrEmpty()) {
            return
        }
        val markersToAddToMap =
            newPolygonMarkersHistoryList.last().filter { !polygonMarkersList.contains(it) }
        val markersToRemoveFromMap =
            polygonMarkersList.filter { !newPolygonMarkersHistoryList.last().contains(it) }
        setMarkersToAddToMap(markersToAddToMap)
        setMarkersToRemoveFromMap(markersToRemoveFromMap)
        polygonMarkersList = newPolygonMarkersHistoryList.last().toMutableList()
        removeLastFromNewPolygonMarkersHistory()
        createDamagePolygon()
    }

    private fun removeLastFromNewPolygonMarkersHistory() {
        val list = _newPolygonMarkersHistory.value
        if (list != null) {
            list.removeLast()
            _newPolygonMarkersHistory.value = list!!
        }
    }

    suspend fun getDetailOfPolygonOnMap(geoPoint: GeoPoint) {
        if (usernameCharArray.value == null || isNetworkAvailable.value == false) {
            clearFromDetailOfPolygon()
            return
        }
        val username = String(usernameCharArray.value!!)
        val filterString = createFilterStringByIntersectAndUsername(geoPoint, username)
        getDetailOfPolygonFromGeoserver(filterString)
    }

    private suspend fun getDetailOfPolygonFromGeoserver(filterString: String) {
        val geoserverRetrofitAPI: GeoserverRetrofitAPI =
            getGeoserverServiceAPIWithGson() ?: return
        val resultOfCallToGeoserver: Pair<Boolean, Response<UsersData>?> =
            performCallToGeoserver { geoserverRetrofitAPI.getDetail(filterString) }
        processOfResultOfCallToGeoserver(resultOfCallToGeoserver)
    }

    private fun processOfResultOfCallToGeoserver(
        resultOfCallToGeoserver: Pair<Boolean,
                Response<UsersData>?>,
    ) {
        val wasCallSuccessful = resultOfCallToGeoserver.first
        val response = resultOfCallToGeoserver.second

        if (response == null) {
            Log.e(MAP_FRAGMENT_TAG, "Error was occurred during attempt to get detail of polygon")
            return
        }
        if (wasCallSuccessful) {
            Log.d(MAP_FRAGMENT_TAG,
                "Detail of polygon has been successfully received")
            val userData: UsersData = response.body() ?: return
            handleIfShowDetailOfPolygon(userData)
        }
    }

    private fun handleIfShowDetailOfPolygon(usersData: UsersData?) {
        val countOfFeatures: Int? = usersData?.features?.size

        if (countOfFeatures != null && countOfFeatures > 0) {
            setDetailMode(true)
            createDetailOfPolygonOnMap(usersData)
        }

        if ((countOfFeatures == null || countOfFeatures == 0)
            && _detailModeShown.value == true
        ) {
            clearFromDetailOfPolygon()
            setSelectedDamageRecordAsNull()
            sharedViewModel?.clearSelectedDamageDataItemFromMap()

        }
    }

    private fun createDetailOfPolygonOnMap(usersData: UsersData) {
        val data: DamageData = usersData.features[0].properties
        setDamageDataFromMapInSharedViewModel(data)
        listOfGeopointsOfSelectedPolygon = createListOfGeopoints(usersData)
        data.coordinates = listOfGeopointsOfSelectedPolygon
        listOfGeopointsOfSelectedPolygon.removeLast()
        setDetailDamageData(data)
        setSelectedDamageRecord(data)
        setGeopointsOfSelectedPolygon(listOfGeopointsOfSelectedPolygon.toList())
    }

    private fun createListOfGeopoints(usersData: UsersData): MutableList<GeoPoint> {
        val listOfGeopoints = mutableListOf<GeoPoint>()
        usersData.features[0].geometry.coordinates.forEach {
            it.forEach { p ->
                listOfGeopoints.add(GeoPoint(p[1], p[0]))
            }
        }
        return listOfGeopoints
    }

    fun clearFromDetailOfPolygon() {
        setDetailMode(false)
        setDetailDamageData(null)
        setGeopointsOfSelectedPolygon(null)
    }

    private fun setSelectedDamageRecord(data: DamageData) {
        selectedDamageRecord = data
    }

    fun getSelectedDamageRecord(): DamageData? {
        return selectedDamageRecord
    }

    fun setSelectedDamageRecordAsNull() {
        selectedDamageRecord = null
    }

    fun prepareForSaveData() {
        if (selectedDamageRecord != null) {
            sendDamageDataToUpdateInGeoserver()
        } else {
            sendNewDamageDataToSaveInGeoserver()
        }
    }

    private fun sendDamageDataToUpdateInGeoserver() {
        val listOfGeoPoints = createListOfGeoPointsProperToSaveInGeoserver()
        selectedDamageRecord?.isInGeoserver = true
        selectedDamageRecord?.isDirectlyFromMap = true
        if (checkIfPolygonShapeWasChanged(listOfGeoPoints)) {
            changeInfoAboutShapeOfSelectedDamageRecord()
        }
        selectedDamageRecord?.let { setDamageDataFromMapInSharedViewModel(it) }
        setSelectedDamageRecordAsNull()
    }

    private fun checkIfPolygonShapeWasChanged(listOfGeoPoints: List<GeoPoint>): Boolean {
        if (selectedDamageRecord?.coordinates == listOfGeoPoints) {
            return false
        }
        return true
    }

    private fun changeInfoAboutShapeOfSelectedDamageRecord() {
        val listOfGeoPoints = createListOfGeoPointsProperToSaveInGeoserver()
        selectedDamageRecord?.coordinates = listOfGeoPoints
        selectedDamageRecord?.changedShapeOfPolygon = true
        selectedDamageRecord?.let { setAreAndPerimeterToDamageDataItem(it) }
    }

    private fun setDamageDataFromMapInSharedViewModel(damageData: DamageData) {
        sharedViewModel?.selectDamageDataFromMap(damageData)
    }

    private fun createListOfGeoPointsProperToSaveInGeoserver(): List<GeoPoint> {
        val geopoints = createListOfGeopointsFromMarkers()
        return geopoints + geopoints[0]
    }

    private fun sendNewDamageDataToSaveInGeoserver() {
        val listOfGeoPoints = createListOfGeoPointsProperToSaveInGeoserver()
        val damageData = DamageData()
        setAreAndPerimeterToDamageDataItem(damageData)
        damageData.coordinates = listOfGeoPoints
        damageData.isDirectlyFromMap = true
        setDamageDataFromMapInSharedViewModel(damageData)
    }

    private fun setAreAndPerimeterToDamageDataItem(damageData: DamageData) {
        setAreaToDamageDataItem(damageData)
        setPerimeterToDamageDataItem(damageData)
    }

    private fun setAreaToDamageDataItem(damageData: DamageData) {
        val area = actualArea.value
        if (area != null) {
            damageData.obsah = area
        }
    }

    private fun setPerimeterToDamageDataItem(damageData: DamageData) {
        val perimeter = actualPerimeter.value
        if (perimeter != null) {
            damageData.obvod = perimeter
        }
    }

    fun onSuccessfulDelete() {
        setIfLoadedMapLayerWithUserData(false)
        setDefaultModeOfMap()
        loadUserPolygons()
        setGeopointsOfSelectedPolygon(null)
        setDetailDamageData(null)
    }

    override fun onCleared() {
        super.onCleared()
        loadedMapLayerWithUserData.removeObserver(loadedMapLayerWithUserDataObserver)
    }
}