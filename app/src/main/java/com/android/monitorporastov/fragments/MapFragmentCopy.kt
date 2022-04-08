package com.android.monitorporastov.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
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
import androidx.preference.PreferenceManager
import com.android.monitorporastov.*
import com.android.monitorporastov.R
import com.android.monitorporastov.adapters.models.DialogItem
import com.android.monitorporastov.databinding.FragmentMapBinding
import com.android.monitorporastov.fragments.viewmodels.MapFragmentViewModel
import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.UsersData
import com.android.monitorporastov.viewmodels.MainSharedViewModel
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.*
import okhttp3.Credentials
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
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
import java.text.SimpleDateFormat
import java.util.*


class MapFragmentCopy : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val sharedViewModel: MainSharedViewModel by activityViewModels()
    private var job: Job? = null
    private lateinit var mMap: MapView
    private var mainMarker: Marker? = null  // marker ukazujúci polohu používateľa
    private lateinit var mMapController: IMapController
    private lateinit var lastLocation: Location  // posldená známa poloha
    private var newPolygon: Polygon = Polygon()  // nový pridávaný polygón
    private var polyMarkers = mutableListOf<Marker>()  // markery, resp. body polygónu
    private var defaultZoomLevel = 18.0  // zoom level mapy
    private var manualMeasure = false  // či je zapnuté manuálne vyznačovanie územia
    private var gpsMeasure = false  // či je zapnuté vyznačovanie územia krokovaním
    private var firstLoad = true  // či je fragment spustený prvýkrát
    private lateinit var drawerLockInterface: DrawerLockInterface  // interface na uzamykanie

    // drawer layoutu
    private lateinit var polyMarkerIcon: Drawable  // ikona markeru v polygóne
    private lateinit var mapMarkerIcon: Drawable  // ikona hlavného markeru,

    // zobrazujúceho polohu používateľa
    private var buttonDeleting = false  // či je umožnené mazanie markerov

    private var mPrefs: SharedPreferences? = null

    private val binding get() = _binding!!
    private val polygons = mutableListOf<Polygon>()  // existujúce polygóny
    private val newPolygonDefaultId = "new polygon"  // id nového polygónu (kvôli mazaniu)
    private val polygonOutlineColorStr = "#2CE635"  // farba okraja polygónu
    private val polygonFillColorStr = "#33EA3535"  // farba vnútra polygónu
    private var actualPerimeter = 0.0  // aktuálny obvod polygónu
    private var actualArea = 0.0  // aktuálna rozloha polygónu
    private var mapLayerStr = ""  // typ mapy (default, orto...)

    private var activityResultLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val interval = 1000
    private val fastestInterval = 1000L
    private val polyMarkersHistory = mutableListOf<MutableList<Marker>>()  // história markerov
    private var allPermissionsAreGranted = true  // či boli udelené poovolenia
    private var detailShown = false

    private var selectedRecord: DamageData? = null
    private val detailPolygonId = "DetailPoly"
    private var listOfGeopointsOfSelectedRecord: MutableList<GeoPoint> = mutableListOf()

    companion object {
        private const val PREFS_NAME = "MAP_FRAGMENT_PREFS"
        private const val PREFS_TILE_SOURCE = "tileSource"
        private const val PREFS_LATITUDE_STRING = "latitudeString"
        private const val PREFS_LONGITUDE_STRING = "longitudeString"
        private const val PREFS_ORIENTATION = "orientation"
        private const val PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble"
        private const val PREFS_MAP_TYPE = "mapType"
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
                startLocationUpdates()
            } else {
                showExplainPermissionsAD()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mPrefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval((interval).toLong())
            .setFastestInterval((fastestInterval))

//        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
//            if (location != null) {
//                lastLocation = location
//            }
//        }
        setUpBackStackCallback()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDateAndTime: String = sdf.format(Date())

        //requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
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
        Configuration.getInstance().load(context, context?.let {
            PreferenceManager.getDefaultSharedPreferences(
                it)
        })
        // volanie kontroly povolené
        checkForPermissions()

        // volanie metódy, ktorá nastavuje mapu
        setUpMap()

        // pridanie prvkov na mapu, ak bolo predtým zapnuté vyznačovanie územia
        if (manualMeasure) {
            setUpForManualMeasure()
        }
        if (gpsMeasure) {
            setUpForGPSMeasure()
        }
        // nastavenie inkonky markeru
        setMarkersIcon()
        // nastavenie listenerov buttonom
        setUpButtonsListeners()
        drawerLockInterface = activity as DrawerLockInterface

        sharedViewModel.clearSelectedDamageDataItemFromMap()
        // https://www.py4u.net/discuss/616531
        // riešenie navcontrolleru, keď sa používateľ z fragmentu vráti do tohto fragmentu
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("key")?.observe(
            viewLifecycleOwner) { result ->
            if (result) {
                // pridanie vytvoreného polygónu do zoznamu existujúcich polygónov
//                val polygon = Polygon()
//                polygon.points = newPolygon.actualPoints
//                newPolygon.actualPoints.clear()
//                polygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
//                polygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
//                polygons.add(polygon)
//                drawExistingPolygons()  // vykreslenie všetkých existujúcich polygónov
                setDefault()  // nastavenie defaultneho zobrazenia mapy (bez buttonov
                // zobrazených počas vyznačovania poškodeného územia)
                navController.currentBackStackEntry?.savedStateHandle?.set("key", false)
            }
        }

        // vykreslenie už existujúcich polygónov
        drawExistingPolygons()
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
        when (mapLayerStr) {
            getString(R.string.ortofoto_layer_name) -> menu.findItem(R.id.menu_ortofoto).isChecked =
                true
            getString(R.string.BPEJ_layer_name) -> menu.findItem(R.id.menu_BPEJ).isChecked = true
        }
        for (layer in checkedLayers) {
            when (layer) {
                getString(R.string.JPRL_layer_name) -> menu.findItem(R.id.menu_JPRL).isChecked =
                    true
                getString(R.string.LPIS_layer_name) -> menu.findItem(R.id.menu_LPIS).isChecked =
                    true
                getString(R.string.C_parcel_layer_name) -> menu.findItem(R.string.C_parcel_layer_name).isChecked =
                    true
                getString(R.string.E_parcel_layer_name) -> menu.findItem(R.string.E_parcel_layer_name).isChecked =
                    true
                getString(R.string.watercourse_layer_name) -> menu.findItem(R.string.watercourse_layer_name).isChecked =
                    true
            }
        }
    }

    private var checkedLayers = mutableListOf<String>()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        var loadBase = false

        if (id == R.id.menu_default_map && mapLayerStr != getString(R.string.map_default)) {
            mapLayerStr = getString(R.string.map_default)
            loadDefaultLayer()
            item.isChecked = true
            return super.onOptionsItemSelected(item)
        }
        if (id == R.id.menu_ortofoto && mapLayerStr != getString(R.string.ortofoto_layer_name)) {
            mapLayerStr = getString(R.string.ortofoto_layer_name)
            loadBase = true
        } else if (id == R.id.menu_BPEJ && mapLayerStr != getString(R.string.BPEJ_layer_name)) {
            mapLayerStr = getString(R.string.BPEJ_layer_name)
            loadBase = true
        }
//        else if (id == R.id.menu_hunting_territories && mapLayerStr != getString(R.string.hunting_territories_layer_name)) {
//            mapLayerStr = getString(R.string.hunting_territories_layer_name)
//            loadBase = true
//        }

        if (loadBase) {
            loadBaseLayer(mapLayerStr, item)
            return super.onOptionsItemSelected(item)
        }

        var layerName = ""

        when (id) {
            R.id.menu_C_parcel -> {
                layerName = getString(R.string.C_parcel_layer_name)
            }
            R.id.menu_E_parcel -> {
                layerName = getString(R.string.E_parcel_layer_name)
            }
            R.id.menu_LPIS -> {
                layerName = getString(R.string.LPIS_layer_name)
            }
            R.id.menu_JPRL -> {
                layerName = getString(R.string.JPRL_layer_name)
            }
            R.id.menu_watercourse -> {
                layerName = getString(R.string.watercourse_layer_name)
            }
        }

        if (layerName.isEmpty()) {
            return super.onOptionsItemSelected(item)
        }

        if (item.isChecked) {
            item.isChecked = false
            deleteLayerFromMap(layerName)
            checkedLayers.remove(layerName)
            return super.onOptionsItemSelected(item)
        }

        checkedLayers.add(layerName)
        loadMapLayer(layerName, item)

        return super.onOptionsItemSelected(item)
    }

    /**
     * Inicializácia locationCallbacku, základu pre určovanie polohy
     */
    private val locationCallback = object : LocationCallback() {
        // https://stackoverflow.com/questions/45576935/android-fusedlocationprovider-returns-null-if-no-internet-connection-available
        // https://stackoverflow.com/questions/59734242/android-fusedlocationproviderclient-not-working-with-apn-sim-its-working-with-wi
        override fun onLocationResult(locationResult: LocationResult?) {
            if (locationResult == null) {
                return
            }
            lastLocation = locationResult.lastLocation
            if (firstLoad) {
                centerMap()
                mMapController.setZoom(defaultZoomLevel)
                firstLoad = false
                observeDamageDataItem()

            }

            updateMarkerLocation(locationResult.lastLocation)
        }
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
     * Začiatok merania polohy.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Zastavenie sledovania polohy.
     */
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Aktualizovanie polohy markera na mape.
     */
    private fun updateMarkerLocation(l: Location) {
        val position = GeoPoint(l.latitude, l.longitude)
        // ak je null, pridá ho
        if (mainMarker == null) {
            mainMarker = Marker(mMap)
            mainMarker!!.setOnMarkerClickListener { _, _ ->
                false
            }
            mainMarker!!.id = "Main marker"
            // https://www.programcreek.com/java-api-examples/?class=org.osmdroid.views.overlay.Marker&method=setIcon
            // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
            mainMarker!!.icon =
                mapMarkerIcon
            mMap.overlays.add(mainMarker)
        }

        mainMarker!!.position = position
        mMap.invalidate()
    }

    /**
     * Ak mapa bola už raz načítaná, nastaví jej parametre, ako napr. aktuálnu
     * rotáciu (otočenie) mapy.
     */
    override fun onStart() {
        super.onStart()
        if (!firstLoad) {
            startLocationUpdates()
            val zoomLevel = mPrefs!!.getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 1f)
            mMap.controller.setZoom(zoomLevel.toDouble())
            val orientation = mPrefs!!.getFloat(PREFS_ORIENTATION, 0f)
            mMap.setMapOrientation(orientation, false)
            val latitudeString = mPrefs!!.getString(PREFS_LATITUDE_STRING, "1.0")
            val longitudeString = mPrefs!!.getString(PREFS_LONGITUDE_STRING, "1.0")
            val latitude = latitudeString?.toDouble()
            val longitude = longitudeString?.toDouble()
            if (latitude != null && longitude != null) {
                mMap.setExpectedCenter(GeoPoint(latitude, longitude))
            }
            val mapTypeStr = mPrefs!!.getString(PREFS_MAP_TYPE, getString(R.string.map_default))
            if (mapTypeStr != null) {
                this.mapLayerStr = mapTypeStr
            }
        }
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
        stopLocationUpdates()
        val edit: SharedPreferences.Editor? = mPrefs?.edit()
        if (edit != null) {
            // https://github.com/osmdroid/osmdroid/blob/master/OpenStreetMapViewer/src/main/java/org/osmdroid/StarterMapFragment.java
            edit.putString(PREFS_TILE_SOURCE, mMap.tileProvider.tileSource.name())
            edit.putFloat(PREFS_ORIENTATION, mMap.mapOrientation)
            edit.putString(PREFS_LATITUDE_STRING, mMap.mapCenter.latitude.toString())
            edit.putString(PREFS_LONGITUDE_STRING, mMap.mapCenter.longitude.toString())
            edit.putFloat(PREFS_ZOOM_LEVEL_DOUBLE, mMap.zoomLevelDouble.toFloat())
            edit.putString(PREFS_MAP_TYPE, mapLayerStr)
            edit.apply()
        }
        mMap.onPause()
    }

    /**
     * Zastaví sledovanie polohy.
     */
    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    /**
     * Vymaže z pamäte uložené informácie, obsahujúce parametre mapy (napr. aktuálny zoom level).
     */
    override fun onDestroy() {
        super.onDestroy()
        if (!noToDestroy) {
            context?.getSharedPreferences(PREFS_NAME, 0)?.edit()?.clear()?.apply()
            val preferences =
                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences.edit().clear().apply()
        }

        noToDestroy = false
        stopLocationUpdates()
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
//        if (!noToDestroy)  {
//            viewModel.onCleared()
//        }
        job?.cancel()
    }

    private var noToDestroy = false

    /**
     * Nastavenie callbacku. Rieši prípady, keď používateľ klikne na "back button".
     */
    private fun setUpBackStackCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            when {
                manualMeasure -> {
                    // ak používateľ merá plochu, opýta sa ho, či chce prestať vyznačovať územia a
                    // či chce zahodiť vyznačovanie územia
                    clearMeasureAlert()
                }
                checkIfPreviousFragmentIsDataDetailFragment() -> {
                    // navigateToDetailFragment()
                    findNavController().navigateUp()
                    noToDestroy = true
                }
                else -> {
                    // ak nemerá nič, opýta sa ho, či chce ukončít aplikáciu.
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
            }
        }
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
                    setUpForManualMeasure()
                } else {
                    setUpForGPSMeasure()
                }
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
            val credential = Credentials.basic("dano", "test")
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
        AlertDialog.Builder(requireContext())
            .setTitle("Nepodarilo sa načítať vrstvu")
            .setNegativeButton("Zrušiť") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
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
                // updateTileSizeForDensity(tileProvider.tileSource)
                mMap.overlays?.add(0, tilesOverlay)
                redrawPolygon()
                //mMap.setTileSource(source)
                mMap.invalidate()
                if (item != null) {
                    item.isChecked = true
                }
            }

        }
    }

    private fun createMapTileProvider(wmsTileSource: WMSTileSourceRepaired) =
        MapTileProviderBasic(context, wmsTileSource)


    private fun loadWMSPolygons() {
        clearMapCache()
        deleteLayerFromMap(getString(R.string.damage_polygon_layer_name))
        loadMapLayer(getString(R.string.damage_polygon_layer_name), null)
    }

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


    private fun removeTilesOverlays(withUserData: Boolean) {
        mMap.overlays.forEach {
            if (it is TilesOverlayRepaired && it.withUserData == withUserData)
                mMap.overlays.remove(it)
        }
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
                        WMSTileSourceRepaired.createFrom(wmsEndpoint, layer)
                    // authorization dat iba raz:
                    val credential = Credentials.basic("dano", "test")
                    Configuration.getInstance().additionalHttpRequestProperties["Authorization"] =
                        credential
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
        mMap = binding.mapView
        mMap.setDestroyMode(false)
        val credential = Credentials.basic(UserData.username, String(UserData.password))
        Configuration.getInstance().additionalHttpRequestProperties["Authorization"] =
            credential
        when (mapLayerStr) {
            getString(R.string.map_default) -> loadDefaultLayer()
            getString(R.string.ortofoto_layer_name) -> loadBaseLayer(getString(R.string.ortofoto_layer_name),
                null)
            getString(R.string.BPEJ_layer_name) -> loadBaseLayer(getString(R.string.BPEJ_layer_name),
                null)
        }
        for (layer in checkedLayers) {
            loadMapLayer(layer, null)
        }

        mMap.setMultiTouchControls(true)
        // aby sa nezobrazovali defaultne buttony na zoom:
        mMap.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        mMapController = mMap.controller
        mMap.maxZoomLevel = 30.0  // 30.0
        mMap.minZoomLevel = 4.0  // 4.0

        val startZoomLevel = 10.0
        // súradnice Bratislavy:
        val lat = 48.148598
        val long = 17.107748
        val startGeoPoint = GeoPoint(lat, long)
        if (firstLoad) {
            // kým sa nenačíta poloha, nastaví center a zoom na Slovensko, približne na Bratislavu.
            mMapController.setZoom(startZoomLevel)
            mMapController.setCenter(startGeoPoint)
        }

        // overlay umožňujúci rotáciu mapy
        val mRotationGestureOverlay = RotationGestureOverlay(mMap)
        mRotationGestureOverlay.isEnabled = true

        mMap.overlays.add(mRotationGestureOverlay)
        loadWMSPolygons()
        // https://github.com/osmdroid/osmdroid/issues/295
        setUpMapReceiver()
        // redrawPolygon()  // ak boli nejaké polygóny
        if (mainMarker != null) {
            mMap.overlays.add(mainMarker)
        }
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
//                if (it.showThisItemOnMap) {
//                    zoomToBoundingBox(it)
//                    viewModel.clearItem()
//                    viewModel.damageDataItem.removeObservers(this)
//                }
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
                if (manualMeasure) {
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
                "xmlns:gml=\"http://www.opengis.net/gml\">" +
                "<And>" +
                "<Intersects>" +
                "<PropertyName>geom</PropertyName>" +
                "<gml:Point srsName=\"urn:ogc:def:crs:EPSG::4326\">" +
                "<gml:coordinates>$coordinateString</gml:coordinates>" +
                "</gml:Point>" +
                "</Intersects>\n" +
                "<PropertyIsEqualTo>\n" +
                "<PropertyName>pouzivatel</PropertyName>\n" +
                "<Literal>dano</Literal>\n" +   // zmenit pouzivatela
                "</PropertyIsEqualTo>" +
                "</And></Filter>"
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
        setUpForManualMeasure()
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
            "%.${3}f".format(data.obvod)
        } m"
        val txtArea = "${
            "%.${3}f".format(data.obsah)
        } m\u00B2"
        binding.layoutContainer.detailInformationLayout.damageName.text =
            if (!data.nazov.isNullOrEmpty()) data.nazov else "-------"
        binding.layoutContainer.detailInformationLayout.damageType.text =
            if (!data.typ_poskodenia.isNullOrEmpty()) data.typ_poskodenia else "-------"
        binding.layoutContainer.detailInformationLayout.damageInfo.text =
            if (!data.popis_poskodenia.isNullOrEmpty()) data.popis_poskodenia else "-------"
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
     * Vykreslenie existujúcich markerov do mapy. Vykresľujú sa asynchrónne, keby ich bolo viac.
     */
    private fun drawExistingPolygons() {
        CoroutineScope(Dispatchers.Main).launch {
            polygons.forEach { mMap.overlays.remove(it) }
            polygons.forEach { mMap.overlays.add(it) }
            mMap.invalidate()
        }
    }

    /**
     * Vráti zoom mapy na aktuálnu polohu.
     */
    private fun centerMap() {
        // najskôr skontrolujeme, či už bola poloha načítaná.
        if (!locationCheck()) {
            return
        }
        // ak je zoom príliš veľký
        if (mMap.zoomLevelDouble < 25) {
            mMapController.setZoom(defaultZoomLevel)
        }
        mMapController.setCenter(GeoPoint(lastLocation))
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
        if (!this::lastLocation.isInitialized) {
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
    private fun setUpForManualMeasure() {
        setUpForMeasure()
        manualMeasure = true
        gpsMeasure = false
        binding.addPointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE
        binding.saveButton.visibility = View.VISIBLE
        binding.deletePointButton.visibility = View.VISIBLE
        binding.backButton.visibility = View.VISIBLE
    }

    private suspend fun handleDeletingRecord() {
        if (selectedRecord == null) {
            return
        }

        Utils.setSelectedItem(selectedRecord!!)
        val resultOfOperation: Boolean =
            withContext(CoroutineScope(Dispatchers.Main).coroutineContext) {
                Utils.handleDeletingOfRecord(requireContext(), binding.progressBar)
            }
        if (resultOfOperation) {
            Toast.makeText(context, "Dáta boli úspešne vymazané",
                Toast.LENGTH_SHORT).show()
            setDefault()
            loadWMSPolygons()
        }
    }

    /**
     * Metóda zviditeľní buttony, ktoré sa majú zobrazovať počas vyznačovania územia krokovaním
     * a umožní spustenie tohto vyznačovania.
     * Zviditeľní aj hornú lištu v mape, v ktorej sa zobrazuje obvod a obsah územia.
     */
    private fun setUpForGPSMeasure() {
        setUpForMeasure()
        gpsMeasure = true
        binding.addPointButton.visibility = View.VISIBLE
        binding.doneGPSMeasureButton.visibility = View.VISIBLE
    }

    /**
     * Metóda skryje všekty buttonu zviditeľnené počas vyznačovania.
     * Skryje aj hornú lištu v mape, v ktorej boli zobrazované obvod a obsah daného územia.
     */
    private fun setDefault() {
        manualMeasure = false
        gpsMeasure = false
//        detailShown = false
        binding.startDrawingButton.visibility = View.VISIBLE
        hideVisibilityOfAreaCalculationsLayout()
        binding.backButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.addPointButton.visibility = View.GONE
        binding.deletePointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE
//        binding.editPolygonButton.visibility = View.GONE
//        binding.deleteRecordButton.visibility = View.GONE
//        binding.detailRecordButton.visibility = View.GONE
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
        if (!checkIfPolygonShapeWasChanged(listOfGeoPoints)) {
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
        setUpForManualMeasure()
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

    private fun createStringFromPoints(): String {
        val geoPoints = newPolygon.actualPoints
        var str = ""
        geoPoints.forEach { str += "${it.latitude},${it.longitude} " }
        str += "${geoPoints[0].latitude},${geoPoints[0].longitude}"

        return str
    }

//    private fun updateTileSizeForDensity(aTileSource: ITileSource) {
//        val mTilesScaleFactor = 1f
//        val tile_size = aTileSource.tileSizePixels
//        val density = resources.displayMetrics.density * 256 / tile_size
//        val size =
//            (tile_size * density * mTilesScaleFactor).toInt()
//        if (Configuration.getInstance().isDebugMapView) Log.d(IMapView.LOGTAG,
//            "Scaling tiles to $size")
//        TileSystem.setTileSize(size)
//    }

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
            "%.${3}f".format(actualArea)
        } m\u00B2"
        val displayedTextPerimeter = "${
            "%.${3}f".format(actualPerimeter)
        } m"
        binding.layoutContainer.areaCalculationsLayout.perimeter.text = displayedTextPerimeter
        binding.layoutContainer.areaCalculationsLayout.area.text = displayedTextArea

    }
}