package com.android.monitorporastov.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.android.monitorporastov.*
import com.android.monitorporastov.R
import com.android.monitorporastov.adapters.models.DialogItem
import com.android.monitorporastov.databinding.FragmentMapBinding
import com.android.monitorporastov.fragments.viewmodels.MapFragmentViewModel
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.BPEJLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.C_parcelLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.E_parcelLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.JPRLLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.LPISLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.ortofotoLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.userDamagesLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice10mLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice50mLayerName
import com.android.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.watercourseLayerName
import com.android.monitorporastov.location.LocationLiveData
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData
import com.android.monitorporastov.viewmodels.MainSharedViewModelNew
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.*
import okhttp3.Credentials
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.wms.WMSEndpoint
import org.osmdroid.wms.WMSParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val sharedViewModel: MainSharedViewModelNew by activityViewModels()
    private val viewModel: MapFragmentViewModel by activityViewModels()

    private var job: Job? = null
    private lateinit var mMap: MapView
    private var mainMarker: Marker? = null  // marker ukazujúci polohu používateľa
    private lateinit var lastLocation: Location // VIEWMODEL // posldená známa poloha
    private var newPolygon: Polygon = Polygon() // VIEWMODEL  // nový pridávaný polygón
    private var polyMarkers = mutableListOf<Marker>() // VIEWMODEL  // markery, resp. body polygónu

    private var manualSelecting = false // VIEWMODEL // či je zapnuté manuálne vyznačovanie územia
    private var gpsSelecting = false // VIEWMODEL // či je zapnuté vyznačovanie územia krokovaním
    private lateinit var drawerLockInterface: DrawerLockInterface  // interface na uzamykanie

    // drawer layoutu
    private lateinit var polyMarkerIcon: Drawable  // ikona markeru v polygóne
    private lateinit var mapMarkerIcon: Drawable  // ikona hlavného markeru,

    // zobrazujúceho polohu používateľa
    private var buttonDeleting = false // VIEWMODEL // či je umožnené mazanie markerov

    private val binding get() = _binding!!

    private val newPolygonDefaultId = "new polygon"  // id nového polygónu (kvôli mazaniu)
    private val polygonOutlineColorStr = "#2CE635"  // farba okraja polygónu
    private val polygonFillColorStr = "#33EA3535"  // farba vnútra polygónu
    private var actualPerimeter = 0.0 // VIEWMODEL // aktuálny obvod polygónu
    private var actualArea = 0.0 // VIEWMODEL // aktuálna rozloha polygónu
    private var mapLayerStr = "" // VIEWMODEL // typ mapy (default, orto...)

    private var activityResultLauncher: ActivityResultLauncher<Array<String>>

    private val polyMarkersHistory =
        mutableListOf<MutableList<Marker>>() // VIEWMODEL // história markerov
    private var allPermissionsAreGranted = true // VIEWMODEL // či boli udelené poovolenia
    private var detailShown = false // VIEWMODEL

    private var selectedRecord: DamageData? = null
    private val detailPolygonId = "DetailPoly"
    private var listOfGeopointsOfSelectedRecord: MutableList<GeoPoint> = mutableListOf()

    private var locationLiveData: LocationLiveData? = null

    private val mainMarkerId = "Main marker"

    companion object {
        private const val MAP_TAG = "MapFragment"
    }

    // na začiatku skontrolujeme povolenia k polohe
    init {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            allPermissionsAreGranted = true
            for (b in result.values) {
                allPermissionsAreGranted = allPermissionsAreGranted && b
            }
            // ak sú povolené, začneme sledovať polohu
            if (allPermissionsAreGranted) {
                setUpConnectionLiveData()
            } else {
                showExplainPermissionsAD()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        setUpBackStackCallback()

        // requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        _binding = FragmentMapBinding.inflate(inflater, container, false)

        return binding.root
    }

    /**
     * V tejto metóde zavoláme všekty pomocné metódy, ktorými inicializujeme všetko potrebné.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSetUp()
    }

    private fun setUpNavController() {
        // riešenie navcontrolleru, keď sa používateľ z fragmentu vráti do tohto fragmentu
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("key")?.observe(
            viewLifecycleOwner) { result ->
            if (result) {
                setDefault()
                // nastavenie defaultneho zobrazenia mapy (bez buttonov
                // zobrazených počas vyznačovania poškodeného územia)
                navController.currentBackStackEntry?.savedStateHandle?.set("key", false)
            }
        }
    }

    /**
     * Kontrola povolení o polohe.
     */
    private fun checkForPermissions() {
        val neededPermission = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        activityResultLauncher.launch(neededPermission)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        MenuCompat.setGroupDividerEnabled(menu, true)
        inflater.inflate(R.menu.map_menu, menu)
        setSelectedLayersAsChecked(menu)
    }

    private var checkedLayers = mutableListOf<String>()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        viewModel.loadLayer(item)
        return super.onOptionsItemSelected(item)
    }


    private fun showExplainPermissionsAD() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.explanation_ad_title))
            .setMessage(getString(R.string.explanation_ad_message))
            .setNegativeButton(getString(R.string.ok_text)) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    /**
     * Aktualizovanie polohy markera na mape.
     */
    private fun updateMarkerLocation(l: Location) {
        val position = GeoPoint(l.latitude, l.longitude)
        // ak je null, pridá ho
        if (mainMarker == null) {
            createMainMarker()
        }
        mainMarker!!.position = position
        mMap.invalidate()
    }

    private fun createMainMarker() {
        mainMarker = Marker(mMap)
        mainMarker!!.setOnMarkerClickListener { _, _ ->
            false
        }
        mainMarker!!.id = mainMarkerId
        // https://www.programcreek.com/java-api-examples/?class=org.osmdroid.views.overlay.Marker&method=setIcon
        // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
        mainMarker!!.icon =
            mapMarkerIcon
        mMap.overlays.add(mainMarker)
    }

    override fun onResume() {
        super.onResume()
        mMap.onResume()
    }

    /**
     * Ukladá aj parametre mapy (ako aktuálny zoom level) dočasne do pamäte.
     */
    override fun onPause() {
        super.onPause()
        viewModel.saveStateOfMap(mMap)
        mMap.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        job?.cancel()
    }

    /**
     * Nastavenie callbacku. Rieši prípady, keď používateľ klikne na "back button".
     */
    private fun setUpBackStackCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            when {
                manualSelecting -> {
                    clearMeasureAlert()
                }
                checkIfPreviousFragmentIsDataDetailFragment() -> {
                    sharedViewModel.clearSelectedDamageDataItemFromMap()
                    findNavController().navigateUp()
                }
                else -> {
                    // ak nemerá nič, opýta sa ho, či chce ukončít aplikáciu.
                    showIfShouldEndAppAD()
                }
            }
        }
    }

    private fun showIfShouldEndAppAD() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.exit_app_ad_title))
            .setPositiveButton(getString(R.string.button_positive_text)) { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton(getString(R.string.button_negative_text)) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    /**
     * Nastavenie ikoniek markerom na mape.
     */
    private fun setMarkersIcon() {
        val height = 150
        val width = 150
        val bitmap1 =
            BitmapFactory.decodeResource(context?.resources, R.drawable.ic_marker_polygon_add)
        val bitmap2 =
            BitmapFactory.decodeResource(context?.resources, R.drawable.ic_map_marker)
        val scaledBitmap1 = Bitmap.createScaledBitmap(bitmap1, width, height, false)
        val scaledBitmap2 = Bitmap.createScaledBitmap(bitmap2, width - 25, height - 25,
            false)
        polyMarkerIcon = BitmapDrawable(resources, scaledBitmap1)
        mapMarkerIcon = BitmapDrawable(resources, scaledBitmap2)
    }


    /**
     * Nastavenie onclick listenerov všetkým buttonom
     */
    private fun setUpButtonsListeners() {
        binding.startDrawingButton.setOnClickListener {
            showNewRecordAD()
        }
        binding.buttonCenter.setOnClickListener {
            centerMap()
        }
        binding.backButton.setOnClickListener {
            undoMap()
        }
        binding.deleteButton.setOnClickListener {
            clearMeasureAlert()
        }
        binding.saveButton.setOnClickListener {
            saveDamageData(it)
        }
        binding.addPointButton.setOnClickListener {
            addPoint()
        }
        binding.buttonCompass.setOnClickListener {
            centerRotationOfMap()
        }
        binding.deletePointButton.setOnClickListener {
            setButtonDeleting()
        }
        binding.doneGPSMeasureButton.setOnClickListener {
            endGPSMeasureAD()
        }
        binding.deleteRecordButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                handleDeletingRecord()
            }
        }
        binding.detailRecordButton.setOnClickListener {
            navigateToDetailFragment()
        }
        binding.editPolygonButton.setOnClickListener {
            setUpForEditingPolygon()
        }
    }


    private fun navigateToDetailFragment() {
        findNavController().navigate(R.id.action_map_fragment_TO_data_detail_fragment)
    }

    /**
     * Zobrazí alert dialog, keď chce používateľ začať vyznačovanie poškodeného územia
     * (keď klikne na to určené tlačidlo).
     *  Opýta sa ho, aký typ vyznačovania územia chce začať.
     */
    private fun showNewRecordAD() {
        // najskôr skontroluje, či už bola načítaná poloha a udelené povolenia.
        // Ak nie, alert dialog sa nezobrazí.
        if (!locationCheck()) {
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.show_measure_ad_title))
            // inicializuje List adapter na výber možností:
            .setAdapter(setUpAdapterForMeasureAD()) { _, item ->

                if (item == 0) {
                    setUpForManualSelecting()
                } else {
                    setUpForGPSSelecting()
                }
                sharedViewModel.clearSelectedDamageDataItemFromMap()
                setSelectedRecordAsNull()
            }
            .setNegativeButton(getString(R.string.button_cancel_text)) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    /**
     * Pomocná metóda slúžiaca na nastavenie adaptéru do alert dialogu, ktorým začíname
     * daný typ vyznačovania poškodenia.
     */
    private fun setUpAdapterForMeasureAD(): ListAdapter {
        val items = measureAlertDialogItems()
        val adapter: ListAdapter = object : ArrayAdapter<DialogItem>(requireContext(),
            android.R.layout.select_dialog_item,
            android.R.id.text1,
            items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById(android.R.id.text1) as TextView
                tv.setCompoundDrawablesWithIntrinsicBounds(
                    items[position].icon, 0, 0, 0)
                val margin = (10 * resources.displayMetrics.density + 0.5f).toInt()
                tv.compoundDrawablePadding = margin
                return view
            }
        }
        return adapter
    }

    /**
     * Metóda vracia hodnoty do adaptéru, ktorým začíname
     * daný typ vyznačovania.
     */
    private fun measureAlertDialogItems(): Array<DialogItem> {
        return arrayOf(
            DialogItem("Manuálne vyznačenie", R.drawable.ic_touch),
            DialogItem("Krokové vyznačenie", R.drawable.ic_walk_colored))
    }

    /**
     * Načítava WMS endpoint z url adresy.
     * @param urlString obsahuje url adresu endpointu
     */
    private fun loadWmsEndpoint(urlString: String): WMSEndpoint? {
        var wmsEndpoint: WMSEndpoint? = null
        try {
            val c: HttpURLConnection = URL(urlString).openConnection() as HttpURLConnection
            val credential = Credentials.basic(String(viewModel.usernameCharArray.value!!),
                String(viewModel.passwordCharArray.value!!))
            c.setRequestProperty("Authorization", credential)
            val inputStream: InputStream = c.inputStream
            wmsEndpoint = WMSParser.parse(inputStream)
            inputStream.close()
            c.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return wmsEndpoint
    }

    /**
     * Zobrazuje alert dialog oznamujúci, že sa nepodarilo načítať vrstvu.
     */
    private fun unloadLayerAD() {
        if (viewModel.isNetworkAvailable.value == false) {
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Nepodarilo sa načítať vrstvu")
            .setNegativeButton("Zrušiť") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
        binding.progressBar.visibility = View.GONE
    }

    /**
     * Asynchrónne spustí načítavanie vrstvy z WMS endpointu a nasledne vrstvu zobrazí v mape.
     */
    private fun loadBaseLayer(layerName: String, item: MenuItem?) {
        CoroutineScope(Dispatchers.Main).launch {
            val wmsTileSource = getWMSTileSource(layerName)
            if (wmsTileSource != null) {
                if (item != null) {
                    item.isChecked = true
                }
                mMap.setTileSource(wmsTileSource)
            }
        }
    }

    private fun loadMapLayer(layerName: String, item: MenuItem?) {
        CoroutineScope(Dispatchers.Main).launch {
            val wmsTileSource = getWMSTileSource(layerName)
            if (wmsTileSource != null) {
                // removeTilesOverlays(withUserData)
                val tileProvider = createMapTileProvider(wmsTileSource)

                val tilesOverlay = TilesOverlayRepaired(tileProvider, context)
                tilesOverlay.loadingBackgroundColor =
                    ContextCompat.getColor(requireContext(), R.color.semi_transparent_white)
                //tilesOverlay.loadingLineColor = Color.TRANSPARENT
                tilesOverlay.isHorizontalWrapEnabled = true
                tilesOverlay.isVerticalWrapEnabled = true
                tilesOverlay.layerName = layerName
                mMap.overlays?.add(0, tilesOverlay)
                redrawPolygon()
                mMap.invalidate()
                if (item != null) {
                    item.isChecked = true
                }
            }

        }
    }

    private fun createMapTileProvider(wmsTileSource: WMSTileSourceRepaired) =
        MapTileProviderBasic(context, wmsTileSource)


    private fun deleteLayerFromMap(layerName: String) {
        mMap.overlays.forEach {
            if (it is TilesOverlayRepaired && it.layerName == layerName)
                mMap.overlays.remove(it)
        }
    }

    private fun clearMapCache() {
        val sqlTileWriter = SqlTileWriter()
        sqlTileWriter.purgeCache(getString(R.string.damage_polygon_layer_name))
    }

    private suspend fun getWMSTileSource(layerName: String): WMSTileSourceRepaired? {
        val deferredSource = CompletableDeferred<WMSTileSourceRepaired?>()
        val getCapabilitiesUrl = getString(R.string.georserver_get_capabilities_url)
        getCapabilitiesUrl.replace("amp;", "")

        job = CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.VISIBLE
            }
            val wmsEndpoint: WMSEndpoint? = loadWmsEndpoint(getCapabilitiesUrl)

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (wmsEndpoint != null) {
                    val layer = wmsEndpoint.layers.filter { it.name == layerName }[0]
                    val source =
                        WMSTileSourceRepaired.createLayer(wmsEndpoint,
                            layer,
                            String(viewModel.usernameCharArray.value!!))
                    deferredSource.complete(source)

                } else {
                    deferredSource.complete(null)
                    unloadLayerAD()
                }
            }
        }
        return deferredSource.await()
    }


    /**
     * Načíta do mapy defaultnú vrstvu od Mapniku.
     */
    private fun loadDefaultLayer() {
        mMap.setTileSource(TileSourceFactory.MAPNIK)
    }

    /**
     * Metóda načítava mapový komponent do aplikácie, nastaví pre neho všetko potrebné,
     * ňako napríklad mapový podklad, maximálny a minimálny zoomm level...
     */
    private fun setUpMap() {
        setUpMapView()
        setUpSelectedLayers()
        setUpZoomController()
        setUpRotationGestureOverlay()
        // https://github.com/osmdroid/osmdroid/issues/295
        setUpMapReceiver()
    }

    private fun setUpMapView() {
        mMap = binding.mapView

        if (viewModel.mapIsInitialised) {
            viewModel.restartStateOfMap(mMap)
        }

        mMap.setDestroyMode(false)
        mMap.setMultiTouchControls(true)
        setUpZoomLevelsOfMap()
    }

    private fun setUpZoomLevelsOfMap() {
        setUpMaxZoomLevel()
        setUpMinZoomLevel()
    }

    private fun setUpMaxZoomLevel() {
        mMap.maxZoomLevel = 30.0
    }

    private fun setUpMinZoomLevel() {
        mMap.minZoomLevel = 4.0
    }

    private fun setUpSelectedLayers() {
        when (mapLayerStr) {
            getString(R.string.map_default) -> loadDefaultLayer()
            getString(R.string.ortofoto_layer_name) ->
                loadBaseLayer(getString(R.string.ortofoto_layer_name),
                    null)
        }
        for (layer in checkedLayers) {
            loadMapLayer(layer, null)
        }
    }

    private fun setUpZoomController() {
        mMap.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setUpDefaultZoomAndCenterOfMap()

    }

    private fun setUpDefaultZoomAndCenterOfMap() {
        val startZoomLevel = 10.0
        // súradnice Bratislavy:
        val lat = 48.148598
        val long = 17.107748
        val startGeoPoint = GeoPoint(lat, long)
        if (viewModel.getLastLocation() == null) {
            // kým sa nenačíta poloha, nastaví center a zoom na Slovensko, približne na Bratislavu.
            mMap.controller.setZoom(startZoomLevel)
            mMap.controller.setCenter(startGeoPoint)
        }
    }

    private fun setUpRotationGestureOverlay() {
        // overlay umožňujúci rotáciu mapy
        val rotationGestureOverlay = RotationGestureOverlay(mMap)
        rotationGestureOverlay.isEnabled = true

        mMap.overlays.add(rotationGestureOverlay)
    }

    private fun addMainMarkerToMapIfExists() {
        if (mainMarker != null) {
            removeMainMarkerIfPreviouslyAdded()
            mMap.overlays.add(mainMarker)
        }
    }

    private  fun removeMainMarkerIfPreviouslyAdded() {
        mMap.overlays.removeAll { it is Marker && it.id == mainMarkerId }
    }

    private fun handleOfShowingSomePolygon() {
        if (!checkIfPreviousFragmentIsDataDetailFragment()) {
            centerMap()
            return
        }
        observeDamageDataItem()
    }

    private fun zoomToBoundingBox(damageData: DamageData) {
        if (damageData.coordinates.isEmpty()) {
            return
        }
        val boundingBox = BoundingBox.fromGeoPoints(damageData.coordinates)
        mMap.zoomToBoundingBox(boundingBox, true, 100)
    }

    private fun observeDamageDataItem() {
        sharedViewModel.selectedDamageDataItemToShowInMap.observe(viewLifecycleOwner) { damageDataItem ->
            damageDataItem?.let {
                zoomToBoundingBox(it)
                sharedViewModel.clearSelectedDamageDataItemToShowInMap()

            }
        }
    }


    private fun checkIfPreviousFragmentIsDataDetailFragment(): Boolean {
        val previousFragment = findNavController()
            .previousBackStackEntry?.destination?.id ?: return false
        if (previousFragment != R.id.data_detail_fragment) {

            return false

        }
        return true
    }

    /**
     * Inicializuje receiver mapy, reagujujúci na kliknutie do mapy.
     * Ak je totiž zapnuté manuálne meranie poškodenej plochy (klikaním), tak vďaka tomuto
     * receiveru sa pridávajú boody (markery) do mapy.
     */
    private fun setUpMapReceiver() {
        val mReceive: MapEventsReceiver = object : MapEventsReceiver {
            // pomocou tejto metódy je možné reagovať na kliknutie do mapy
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (manualSelecting) {
                    addMarkerToMapOnClick(p)
                } else {
                    getDetailOfPolygonOnMap(p)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        // tento receiver sa nakoniec pridá do mapy ako overlay
        mMap.overlays.add(MapEventsOverlay(mReceive))
    }

    private fun createCoordinateString(p: GeoPoint): String {
        return "${p.latitude},${p.longitude}"
    }

    private fun createFilterString(p: GeoPoint): String {
        val coordinateString = createCoordinateString(p)
        return "<Filter xmlns:ogc=\"http://www.opengis.net/ogc\" " +
                "   xmlns:gml=\"http://www.opengis.net/gml\">" +
                "       <And>" +
                "           <Intersects>" +
                "               <PropertyName>geom</PropertyName>" +
                "                   <gml:Point srsName=\"urn:ogc:def:crs:EPSG::4326\">" +
                "                       <gml:coordinates>$coordinateString</gml:coordinates>" +
                "                   </gml:Point>" +
                "           </Intersects>\n" +

                "           <PropertyIsEqualTo>\n" +
                "               <PropertyName>pouzivatel</PropertyName>\n" +
                "                   <Literal>" +
                "                       ${String(viewModel.usernameCharArray.value!!)}" +
                "                   </Literal>\n" +
                "           </PropertyIsEqualTo>" +
                "       </And>" +
                "</Filter>"
    }

    private fun getDetailOfPolygonOnMap(p: GeoPoint) {
        val filterString = createFilterString(p)
        getDetailFromOfPolygonFromGeoserver(filterString)
    }

    private fun getDetailFromOfPolygonFromGeoserver(filterString: String) {
        val okHttpClient = Utils.createOkHttpClient()
        val service = GeoserverRetrofitBuilder.createServiceWithGsonFactory(okHttpClient)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.getDetail(filterString)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val usersData: UsersData? = response.body()
                        handleIfShowDetailOfPolygon(usersData)

                    } else {
                        Log.d(MAP_TAG, "Response error: ${response.message()}")
                    }
                }
            } catch (e: Throwable) {
                Log.e("ERRORUS", e.toString())
            }
        }
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

    private fun showDetailOfPolygonOnMap(usersData: UsersData) {
        val data: DamageData = usersData.features[0].properties
        sharedViewModel.selectDamageDataFromMap(data)
        listOfGeopointsOfSelectedRecord = createListOfGeopoints(usersData)
        data.coordinates = listOfGeopointsOfSelectedRecord
        Log.d(MAP_TAG, "Response was successful, data was received.")
        listOfGeopointsOfSelectedRecord.removeLast()
        showDetailInfo(data)
        setSelectedRecord(data)
        showDetailPolygon(listOfGeopointsOfSelectedRecord)
        setUpForDetail()
    }

    private fun setUpForEditingPolygon() {
        clearMapFromShownDetail()
        setUpForManualSelecting()
        addMarkersOfEditedPolygon()
        drawPolygon()
    }

    private fun addMarkersOfEditedPolygon() {
        listOfGeopointsOfSelectedRecord.forEach {
            val marker = createMarker(it)
            polyMarkers.add(marker)
            mMap.overlays.add(marker)
        }
    }

    private fun handleIfShowDetailOfPolygon(usersData: UsersData?) {
        val countOfFeatures: Int? = usersData?.features?.size

        if (countOfFeatures != null && countOfFeatures > 0) {
            showDetailOfPolygonOnMap(usersData)
        }

        if ((countOfFeatures == null || countOfFeatures == 0)
            && detailShown
        ) {
            clearMapFromShownDetail()
            setSelectedRecordAsNull()
            sharedViewModel.clearSelectedDamageDataItemFromMap()
        }
    }

    private fun clearMapFromShownDetail() {
        detailShown = false
        mMap.overlays.removeLast()
        mMap.invalidate()
        hideLayoutOfDetail()
    }

    private fun addMarkerToMapOnClick(p: GeoPoint) {
        val marker = createMarker(p)  // vytvorenie markeru
        polyMarkersHistory.add(polyMarkers.toMutableList())
        polyMarkers.add(marker)
        mMap.overlays.add(marker)

        drawPolygon()
    }

    private fun hideVisibilityOfDetailInformationLayout() {
        binding.layoutContainer.detailInformationLayout.root.visibility = View.GONE
    }

    private fun setVisibilityOfDetailInformationLayout() {
        binding.layoutContainer.detailInformationLayout.root.visibility = View.VISIBLE
    }

    private fun hideVisibilityOfAreaCalculationsLayout() {
        binding.layoutContainer.areaCalculationsLayout.root.visibility = View.GONE
    }

    private fun setVisibilityOfAreaCalculationsLayout() {
        binding.layoutContainer.areaCalculationsLayout.root.visibility = View.VISIBLE
    }

    private fun showDetailInfo(data: DamageData) {
        val txtPerimeter = "${
            data.obvod.toInt()
        } m"
        val txtArea = "${
            data.obsah.toInt()
        } m\u00B2"
        binding.layoutContainer.detailInformationLayout.damageName.text =
            if (!data.nazov.isNullOrEmpty()) data.nazov else "-------"
        binding.layoutContainer.detailInformationLayout.damageType.text =
            if (!data.typ_poskodenia.isNullOrEmpty()) data.typ_poskodenia else "(nezadaný typ poškodenia)"
        binding.layoutContainer.detailInformationLayout.damageInfo.text =
            if (!data.popis_poskodenia.isNullOrEmpty()) data.popis_poskodenia else "(nezadaný popis)"
        binding.layoutContainer.detailInformationLayout.perimeter.text = txtPerimeter
        binding.layoutContainer.detailInformationLayout.area.text = txtArea
    }

    private fun setSelectedRecord(data: DamageData) {
        selectedRecord = data
    }

    private fun setSelectedRecordAsNull() {
        selectedRecord = null
    }

    private fun showDetailPolygon(list: MutableList<GeoPoint>) {
        mMap.overlays.forEach {
            if (it is Polygon && it.id == detailPolygonId) mMap.overlays.remove(it)
        }
        val detailPolygon = Polygon()
        detailPolygon.id = detailPolygonId
        detailPolygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
        detailPolygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
        detailPolygon.points = list
        mMap.overlays?.add(detailPolygon)
        mMap.invalidate()
    }

    /**
     * Metóda slúži na vytvorenie markeru po kliknutí do mapy.
     * @param point súradnice kliknutého bodu v mape
     */
    private fun createMarker(point: GeoPoint): Marker {
        val marker = Marker(mMap)

        marker.isDraggable = true
        // aplikujeme na marker receiver, vďaka ktorému ho je možné presúvať.
        applyDraggableListenerOnMarker(marker)

        // poloha
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.isDraggable = true

        marker.icon = polyMarkerIcon  // nastavenie ikony
        marker.position = GeoPoint(point.latitude, point.longitude)  //nastavenie pozície
        // na marker nastavíme onClickListener, vďaka ktorému ho po klinutí naň bude možné zmazať
        marker.setOnMarkerClickListener { m, _ ->
            deleteMarker(m)
            true
        }
        return marker
    }

    /**
     * Metóda na marker aplikuje draggable listner.
     * Vďaka tejto metóde sa prekresľuje polygón, keď používateľ daným markerom ťahá po mape.
     */
    private fun applyDraggableListenerOnMarker(marker: Marker) {
        marker.isDraggable = true

        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker?) {
            }

            override fun onMarkerDragEnd(marker: Marker) {
                drawPolygon()
            }

            override fun onMarkerDrag(marker: Marker?) {
                drawPolygon()
            }
        }
        )
    }

    /**
     * Vymazania markera a následné prekreslenie polygónu.
     * @param marker konkrétny marker
     */
    private fun deleteMarker(marker: Marker) {
        if (!buttonDeleting) return
        mMap.overlays.remove(marker)
        polyMarkersHistory.add(polyMarkers.toMutableList())
        polyMarkers = polyMarkers.filter { it != marker }.toMutableList()
        drawPolygon()
    }

    /**
     * Pomocou tejto metódy vieme vraciať späť zmeny v mape.
     * Pod zmenami máme na mysli pridanie body (markeru) do mapy a odstránenie bodu.
     */
    private fun undoMap() {
        if (polyMarkersHistory.isEmpty()) return
        polyMarkersHistory.last().forEach {
            if (!polyMarkers.contains(it)) {
                mMap.overlays.add(it)
            }
        }
        polyMarkers.forEach {
            if (!polyMarkersHistory.last().contains(it)) {
                mMap.overlays.remove(it)
            }
        }

        polyMarkers = polyMarkersHistory.last()
        polyMarkersHistory.removeLast()

        drawPolygon()
    }

    /**
     * Vráti zoom mapy na aktuálnu polohu.
     */
    private fun centerMap() {
        if (!locationCheck()) {
            return
        }
        val maxZoomLevel = 25
        val defaultZoomLevel = 18.0
        if (mMap.zoomLevelDouble < maxZoomLevel) {
            mMap.controller.setZoom(defaultZoomLevel)
        }
        mMap.controller.setCenter(GeoPoint(viewModel.getLastLocation()))
    }

    /**
     * Vycentruje otočenie mapy.
     */
    private fun centerRotationOfMap() {
        mMap.mapOrientation = 0.0f
    }

    /**
     * Kontrola, či už bola načítaná poloha.
     */
    private fun locationCheck(): Boolean {
        checkForPermissions()
        if (!allPermissionsAreGranted) {
            return false
        }
        if (viewModel.getLastLocation() == null) {
            Toast.makeText(context, getString(R.string.location_alert),
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * Prekreslenie polygónu, ak nejaký bol už začatý.
     */
    private fun redrawPolygon() {
        if (polyMarkers.isNotEmpty()) {
            for (marker in polyMarkers) {
                mMap.overlays.add(marker)
            }
            drawPolygon()
        }
    }

    private fun setUpForDetail() {
        detailShown = true
        binding.startDrawingButton.visibility = View.GONE
        binding.editPolygonButton.visibility = View.VISIBLE
        binding.deleteRecordButton.visibility = View.VISIBLE
        binding.detailRecordButton.visibility = View.VISIBLE
        setVisibilityOfDetailInformationLayout()
    }

    private fun hideLayoutOfDetail() {
        detailShown = false
        binding.startDrawingButton.visibility = View.VISIBLE
        binding.editPolygonButton.visibility = View.GONE
        binding.deleteRecordButton.visibility = View.GONE
        binding.detailRecordButton.visibility = View.GONE
        hideVisibilityOfDetailInformationLayout()
    }

    /**
     * Metóda zviditeľní tlačidlá, ktoré majú byť viditeľné počas vyznačovaní (obidvoch typov)
     * a uzamkne drawer, aby počas vyznačovania plochy nebolo možné sa preklikávať do
     * iných položiek menu.
     */
    private fun setUpForMeasure() {
        binding.startDrawingButton.visibility = View.GONE
        setVisibilityOfAreaCalculationsLayout()

        binding.deleteButton.visibility = View.VISIBLE
        drawerLockInterface.lockDrawer() // uzamknutie draweru prostredníctvom interface
    }

    /**
     * Metóda zviditeľní buttony, ktoré sa majú zobrazovať počas manuálneho vyznačovania plochy
     * a umožní spustenie tohto vyznačovania.
     * Zviditeľní aj hornú lištu v mape, v ktorej sa zobrazuje obvod a obsah územia.
     */
    private fun setUpForManualSelecting() {
        setUpForMeasure()
        manualSelecting = true
        gpsSelecting = false
        binding.addPointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE
        binding.saveButton.visibility = View.VISIBLE
        binding.deletePointButton.visibility = View.VISIBLE
        binding.backButton.visibility = View.VISIBLE
    }

    private suspend fun handleDeletingRecord(): Boolean {
        val deferredBoolean = CompletableDeferred<Boolean>()
        val damageDataItem = selectedRecord ?: return false
        AlertDialog.Builder(requireContext())  //
            .setTitle(R.string.if_delete_record_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    binding.progressBar.visibility = View.VISIBLE
                    deferredBoolean.complete(sharedViewModel.deleteItem(damageDataItem))
                }
            }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ ->
                deferredBoolean.complete(false)
                dialog.cancel()
            }
            .create()
            .show()

        if (deferredBoolean.await()) {

            Toast.makeText(context, "Dáta boli úspešne vymazané",
                Toast.LENGTH_SHORT).show()
            setDefault()
            viewModel.loadUserPolygons()
            clearMapFromShownDetail()
        }
        binding.progressBar.visibility = View.GONE
        return deferredBoolean.await()
    }

    /**
     * Metóda zviditeľní buttony, ktoré sa majú zobrazovať počas vyznačovania územia krokovaním
     * a umožní spustenie tohto vyznačovania.
     * Zviditeľní aj hornú lištu v mape, v ktorej sa zobrazuje obvod a obsah územia.
     */
    private fun setUpForGPSSelecting() {
        setUpForMeasure()
        gpsSelecting = true
        binding.addPointButton.visibility = View.VISIBLE
        binding.doneGPSMeasureButton.visibility = View.VISIBLE
    }

    /**
     * Metóda skryje všekty buttonu zviditeľnené počas vyznačovania.
     * Skryje aj hornú lištu v mape, v ktorej boli zobrazované obvod a obsah daného územia.
     */
    private fun setDefault() {
        manualSelecting = false
        gpsSelecting = false
        binding.startDrawingButton.visibility = View.VISIBLE
        hideVisibilityOfAreaCalculationsLayout()
        binding.backButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.addPointButton.visibility = View.GONE
        binding.deletePointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE

        clearMeasure()
        drawerLockInterface.unlockDrawer()
    }

    /**
     * Metóda rieši zapínanie a vypínanie "mazacieho" módu v aplikácií a buttonu,
     * ktorý to riadi, mení farbu.
     */
    private fun setButtonDeleting() {
        if (!buttonDeleting) {
            buttonDeleting = true
            // zmenenie farby buttonu
            binding.deletePointButton.backgroundTintList = ColorStateList.valueOf(Color
                .parseColor("#EA0A0A"))
        } else {
            buttonDeleting = false
            binding.deletePointButton.backgroundTintList = ColorStateList.valueOf(Color
                .parseColor("#B4802E"))
        }
    }

    /**
     * Metóda zviditeľňuje alert dialog, ktorý sa pýta používateľa, či chce naozaj
     * ukončiť vyznačovanie územia.
     */
    private fun clearMeasureAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_measure_ad_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                setDefault()
                setSelectedRecordAsNull()
                sharedViewModel.clearSelectedDamageDataItemFromMap()
            }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Touto metódou odstránime z mapy všetko, čo vzniklo počas vyznačovania, resp. merania,
     * teda polygóny a markery.
     * Taktiež vyčistí aj prislúchajúce zoznamy.
     */
    private fun clearMeasure() {
        mMap.overlays.forEach {
            if ((it is Polygon && (it.id == newPolygonDefaultId || it.id == detailPolygonId))
                || it is Marker && it.id != "Main marker"
            ) mMap.overlays.remove(it)
        }
        polyMarkers.clear()
        polyMarkersHistory.clear()
        mMap.invalidate()  //
    }

    private fun lessThan3PointsAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Nezadali ste dostatočný počet bodov")
            .setMessage("Na uloženie záznamu musíte zadať aspoň 3 body.")
            .setNegativeButton(getText(R.string.ok_text)) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Uloženie poškodenia. Na uloženie treba mať aspoň 3 body na mape určené.
     */
    private fun saveDamageData(v: View) {
        if (!checkIfMoreThan3PointsAdded()) {
            return
        }
        sendDamageDataToAddFragment(v)
    }

    private fun checkIfMoreThan3PointsAdded(): Boolean {
        if (polyMarkers.size < 3) {
            lessThan3PointsAD()
            return false
        }
        return true
    }

    private fun createListOfPointsFromMapProperToSaveInGeoserver(): List<GeoPoint> {
        return newPolygon.actualPoints + newPolygon.actualPoints[0]
    }

    private fun sendDamageDataToAddFragment(v: View) {
        if (selectedRecord != null) {
            sendDamageDataToUpdateInGeoserver()
        } else {
            sendNewDamageDataToSaveInGeoserver()
        }
        navigateToAddDamageFragment(v)
    }

    private fun navigateToAddDamageFragment(v: View) {
        Navigation.findNavController(v)
            .navigate(R.id.action_map_fragment_TO_add_damage_fragment)
    }

    private fun sendDamageDataToUpdateInGeoserver() {
        val listOfGeoPoints = createListOfPointsFromMapProperToSaveInGeoserver()
        selectedRecord?.isInGeoserver = true
        selectedRecord?.isDirectlyFromMap = true
        if (checkIfPolygonShapeWasChanged(listOfGeoPoints)) {
            changeInfoAboutPolygonOfSelectedRecord()
        }
        selectedRecord?.let { setDamageDataFromMapInViewModel(it) }
        setSelectedRecordAsNull()
    }

    private fun changeInfoAboutPolygonOfSelectedRecord() {
        val listOfGeoPoints = createListOfPointsFromMapProperToSaveInGeoserver()
        selectedRecord?.coordinates = listOfGeoPoints
        selectedRecord?.changedShapeOfPolygon = true
        selectedRecord?.obvod = actualPerimeter
        selectedRecord?.obsah = actualArea
    }

    private fun sendNewDamageDataToSaveInGeoserver() {
        val listOfGeoPoints = createListOfPointsFromMapProperToSaveInGeoserver()
        val damageData = DamageData()
        damageData.obvod = actualPerimeter
        damageData.obsah = actualArea
        damageData.coordinates = listOfGeoPoints
        damageData.isDirectlyFromMap = true
        setDamageDataFromMapInViewModel(damageData)
    }

    private fun setDamageDataFromMapInViewModel(damageData: DamageData) {
        sharedViewModel.selectDamageDataFromMap(damageData)
    }

    private fun checkIfPolygonShapeWasChanged(listOfGeoPoints: List<GeoPoint>): Boolean {
        if (selectedRecord?.coordinates == listOfGeoPoints) {
            return false
        }
        return true
    }

    /**
     * Zobrazuje alert dialog, ktorý sa pýta používateľa, či chce ukončiť vynzačovania
     * územia krokovaním.
     */
    private fun endGPSMeasureAD() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.end_gps_measure_ad_title))
            .setPositiveButton(R.string.button_positive_text) { _, _ -> changeMarkersForManualMeasure() }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Prídáva body, resp. markery na mapu počas vyznačovania územia krokovaním.
     */
    private fun addPoint() {
        val marker = Marker(mMap)

        marker.setOnMarkerClickListener { _, _ ->
            false
        }
        marker.icon =
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_marker_polygon) }

        marker.position = GeoPoint(lastLocation.latitude, lastLocation.longitude)
        polyMarkersHistory.add(polyMarkers.toMutableList())
        polyMarkers.add(marker)
        mMap.overlays.add(marker)
        drawPolygon()  // prekreslenie polygónu
    }

    /**
     * Táto metóda po krokovom vyznačovaní územia zmení markerom ikonky, aplikuje na nich listener a pod.
     * To preto, aby bolo možné ešte prípadne zmeniť polygón.
     */
    private fun changeMarkersForManualMeasure() {
        setUpForManualSelecting()
        polyMarkers.forEach { it.icon = polyMarkerIcon }
        polyMarkers.forEach { applyDraggableListenerOnMarker(it) }
        polyMarkers.forEach { it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
        polyMarkers.forEach {
            it.setOnMarkerClickListener { m, _ ->
                deleteMarker(m)
                true
            }
        }
    }

    /**
     * Jedna z najdôležitejších metód, ktorá umožňuje kreslenie a prekresľopvanie polygónu na mape.
     *
     */
    private fun drawPolygon() {
        mMap.overlays.forEach {
            if (it is Polygon && it.id == newPolygonDefaultId) mMap.overlays.remove(it)
        }
        val geoPoints = mutableListOf<GeoPoint>()
        polyMarkers.forEach { geoPoints.add(GeoPoint(it.position.latitude, it.position.longitude)) }
        // https://github.com/osmdroid/osmdroid/wiki/Markers,-Lines-and-Polygons-(Kotlin)
        newPolygon = Polygon()

        newPolygon.id = newPolygonDefaultId
        newPolygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
        newPolygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
        newPolygon.points = geoPoints
        mMap.overlays.add(newPolygon)

        computeAreaAndPerimeter()
        mMap.invalidate()
    }

    /**
     * Metódou vypočítame obdvod a obsah polygónu.
     */
    private fun computeAreaAndPerimeter() {
        val geoPoints = mutableListOf<GeoPoint>()
        polyMarkers.forEach { geoPoints.add(GeoPoint(it.position.latitude, it.position.longitude)) }
        actualArea = SphericalUtil.computeArea(geoPoints.map {
            LatLng(it.latitude,
                it.longitude)
        }.toList())
        actualPerimeter = SphericalUtil.computeLength(geoPoints.map {
            LatLng(it.latitude,
                it.longitude)
        }.toList())

        // výpis textu
        displayTextOfAreaAndPerimeter()
    }

    /**
     * Metódou vypíšeme obvod a obsah polygónu do hornej lišty v mape,
     * ktorá je zobrazená počas vyznačovania poškodeného územia.
     */
    private fun displayTextOfAreaAndPerimeter() {
        val displayedTextArea = "${
            actualArea.toInt()
        } m\u00B2"
        val displayedTextPerimeter = "${
            actualPerimeter.toInt()
        } m"
        binding.layoutContainer.areaCalculationsLayout.perimeter.text = displayedTextPerimeter
        binding.layoutContainer.areaCalculationsLayout.area.text = displayedTextArea
    }

    private fun setUpConnectionLiveData() {
        locationLiveData = LocationLiveData(requireContext())
        setUpConnectionStatusReceiver()
    }

    private fun setUpConnectionStatusReceiver() {
        locationLiveData?.observe(viewLifecycleOwner) {
            viewModel.setLastLocation(it)
        }
    }

    private fun initSetUp() {
        checkForPermissions()
        setUpViewModel()
        setUpObservers()
        setUpAllThatIsRelatedWithViews()
        setUpNavController()

    }

    private fun setUpAllThatIsRelatedWithViews() {
        viewModel.loadUserPolygons()
        setUpMap()
        setMarkersIcon()
        setUpButtonsListeners()
        setUpDrawerLockInterface()
        checkIfSelecting()
        addExistingLayers()
        addMainMarkerToMapIfExists()
    }

    private fun checkIfSelecting() {
        if (manualSelecting) {
            setUpForManualSelecting()
        }
        if (gpsSelecting) {
            setUpForGPSSelecting()
        }
    }

    private fun setUpObservers() {
        observeLastLocation()
        observeDamageDataItem()
        observeLoadingValue()
        observeWMSBaseLayer()
        observeWMSMapLayer()
        observeThatShouldDisplayDefaultLayer()
        observeLayerToDelete()
        observeNoInternetForSelectedLayers()
        observeWMSLayerWithUserData()
    }

    private fun setUpViewModel() {
        viewModel.initViewModelMethods(sharedViewModel, viewLifecycleOwner)
        viewModel.setConfiguration()
    }

    private fun setUpDrawerLockInterface() {
        drawerLockInterface = activity as DrawerLockInterface
    }

    private fun observeLastLocation() {
        viewModel.lastLocation.observe(viewLifecycleOwner) {
            updateMarkerLocation(it)
            if (!viewModel.mapIsInitialised) {
                centerMap()
                viewModel.setThatMapWasInitialised()
            }
        }
    }

    private fun observeLoadingValue() {
        viewModel.loading.observe(viewLifecycleOwner) { loadingValue ->
            binding.progressBar.visibility = if (loadingValue) View.VISIBLE else View.GONE
        }
    }

    private fun observeWMSBaseLayer() {
        viewModel.wmsTileSourceForBaseLayer.observe(viewLifecycleOwner) { baseLayer ->
            if (baseLayer.hasBeenHandled) {
                return@observe
            }
            baseLayer.getContentIfNotHandled().let {
                if (it == null) {
                    unloadLayerAD()
                    return@observe
                }
                mMap.setTileSource(it)
            }
        }
    }

    private fun observeWMSMapLayer() {
        viewModel.wmsTileSourceForMapLayer.observe(viewLifecycleOwner) { pair ->
            pair.getContentIfNotHandled()?.let {
                val layerName = it.first
                val wmsTileSource = it.second
                if (wmsTileSource == null) {
                    unloadLayerAD()
                    return@observe
                }
                addNewLayerToMap(layerName, wmsTileSource)
            }

        }
    }

    private fun observeWMSLayerWithUserData() {
        viewModel.wmsTileSourceForUserData.observe(viewLifecycleOwner) { layer ->
            if (layer.hasBeenHandled) {
                return@observe
            }
            layer.getContentIfNotHandled()?.let {
                if (layer == null) {
                    unloadLayerAD()
                    return@observe
                }
                addNewLayerWithUserDataToMap(it)
                addMainMarkerToMapIfExists()
            }

        }
    }

    private fun addNewLayerToMap(layerName: String, wmsTileSource: WMSTileSourceRepaired) {
        val tilesOverlayRepaired = createTilesOverlay(layerName, wmsTileSource)
        mMap.overlays?.add(0, tilesOverlayRepaired)
        redrawPolygon()
        mMap.invalidate()
    }

    private fun addNewLayerWithUserDataToMap(wmsTileSource: WMSTileSourceRepaired) {
        val tilesOverlayRepaired = createTilesOverlay(userDamagesLayerName, wmsTileSource)
        mMap.overlays?.add(tilesOverlayRepaired)
        redrawPolygon()
        mMap.invalidate()
    }

    private fun createTilesOverlay(
        layerName: String,
        wmsTileSource: WMSTileSourceRepaired,
    ): TilesOverlayRepaired {
        val tileProvider = createMapTileProvider(wmsTileSource)

        val tilesOverlay = TilesOverlayRepaired(tileProvider, context)
        tilesOverlay.loadingBackgroundColor =
            ContextCompat.getColor(requireContext(), R.color.semi_transparent_white)
        tilesOverlay.isHorizontalWrapEnabled = true
        tilesOverlay.isVerticalWrapEnabled = true
        tilesOverlay.layerName = layerName
        return tilesOverlay
    }

    private fun observeThatShouldDisplayDefaultLayer() {
        viewModel.displayDefaultLayer.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let { loadDefaultLayer() }
        }
    }

    private fun observeLayerToDelete() {
        viewModel.layerToDeleteString.observe(viewLifecycleOwner) { layerToDelete ->
            layerToDelete.getContentIfNotHandled()?.let { deleteLayerFromMap(it) }
        }
    }

    private fun observeNoInternetForSelectedLayers() {
        viewModel.noInternetForSelectLayers.observe(viewLifecycleOwner) {
            Toast.makeText(context,
                "Na výber iných mapových vrstiev musíte mať prístup na internet.",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun setSelectedLayersAsChecked(menu: Menu) {
        viewModel.selectedLayersList.forEach {
            setMenuItemAsCheckedByLayerName(it, menu)
        }
    }

    private fun addExistingLayers() {
        addExistingBaseLayer()
        addExistingMapLayers()
    }

    private fun addExistingBaseLayer() {
        if (viewModel.selectedBaseLayer != null) {
            mMap.setTileSource(viewModel.selectedBaseLayer)
        }
    }

    private fun addExistingMapLayers() {
        viewModel.mapLayersList.forEach {
            addNewLayerToMap(it.first, it.second)
        }
    }

    private fun setMenuItemAsCheckedByLayerName(layerName: String, menu: Menu) {
        when (layerName) {
            ortofotoLayerName -> menu.findItem(R.id.menu_ortofoto).isChecked =
                true
            BPEJLayerName -> menu.findItem(R.id.menu_BPEJ).isChecked =
                true
            C_parcelLayerName -> menu.findItem(R.id.menu_C_parcel).isChecked =
                true
            E_parcelLayerName -> menu.findItem(R.id.menu_E_parcel).isChecked =
                true
            LPISLayerName -> menu.findItem(R.id.menu_LPIS).isChecked =
                true
            JPRLLayerName -> menu.findItem(R.id.menu_JPRL).isChecked =
                true
            watercourseLayerName -> menu.findItem(R.id.menu_watercourse).isChecked =
                true
            vrstevnice10mLayerName -> menu.findItem(R.id.menu_vrstevnice10m).isChecked =
                true
            vrstevnice50mLayerName -> menu.findItem(R.id.menu_vrstevnice50m).isChecked =
                true
        }
    }

}