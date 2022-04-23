package com.skeagis.monitorporastov.fragments

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
import com.skeagis.monitorporastov.*
import com.skeagis.monitorporastov.R
import com.skeagis.monitorporastov.activities.MainActivity
import com.skeagis.monitorporastov.adapters.models.DialogItem
import com.skeagis.monitorporastov.databinding.FragmentMapBinding
import com.skeagis.monitorporastov.fragments.viewmodels.MapFragmentViewModel
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.BPEJLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.C_parcelLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.E_parcelLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.JPRLLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.LPISLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.buildingsListLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.huntingGroundsLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.ortofotoLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.userDamagesLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice10mLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.vrstevnice50mLayerName
import com.skeagis.monitorporastov.geoserver.GeoserverPropertiesNames.LayersNames.watercourseLayerName
import com.skeagis.monitorporastov.location.LocationLiveData
import com.skeagis.monitorporastov.model.DamageData
import com.skeagis.monitorporastov.viewmodels.MainSharedViewModel
import kotlinx.coroutines.*
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


class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val sharedViewModel: MainSharedViewModel by activityViewModels()
    private val viewModel: MapFragmentViewModel by activityViewModels()

    private var job: Job? = null
    private lateinit var mapView: MapView
    private var mainMarker: Marker? = null  // marker ukazujúci polohu používateľa

    private lateinit var drawerLockInterface: DrawerLockInterface  // interface na uzamykanie
                                                                // drawer layoutu
    private lateinit var polyMarkerIcon: Drawable  // ikona markeru v polygóne
    private lateinit var mapMarkerIcon: Drawable  // ikona hlavného markeru,

    private val binding get() = _binding!!

    private val polygonOutlineColorStr = "#2CE635"  // farba okraja polygónu
    private val polygonFillColorStr = "#33EA3535"  // farba vnútra polygónu

    private lateinit var activityResultLauncher: ActivityResultLauncher<Array<String>>

    private var allPermissionsAreGranted = true // či boli udelené poovolenia

    private var locationLiveData: LocationLiveData? = null

    private val mainMarkerId = "Main marker"
    private val detailPolygonId = "DetailPolygon"

    companion object {
        private const val SHARED_PREFS_FOR_STORING_VERSION_NAME =
            "shared_preferences_for_storing_version_num"
        private const val SHARED_PREFS_VERSION_KEY = "version_number"
        private const val CURRENT_VERSION_CODE_NUM = 10
    }

    // na začiatku skontrolujeme povolenia k polohe
    init {
        checkIfPermissionsAreGranted()
    }

    private fun checkIfPermissionsAreGranted() {
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
                viewModel.setDefaultModeOfMap()
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
        mapView.invalidate()
    }

    private fun createMainMarker() {
        mainMarker = Marker(mapView)
        mainMarker!!.setOnMarkerClickListener { _, _ ->
            false
        }
        mainMarker!!.id = mainMarkerId
        // https://www.programcreek.com/java-api-examples/?class=org.osmdroid.views.overlay.Marker&method=setIcon
        // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
        mainMarker!!.icon =
            mapMarkerIcon
        mapView.overlays.add(mainMarker)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    /**
     * Ukladá aj parametre mapy (ako aktuálny zoom level) dočasne do pamäte.
     */
    override fun onPause() {
        super.onPause()
        viewModel.saveStateOfMap(mapView)
        mapView.onPause()
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
                viewModel.manualSelecting.value == true -> {
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
            viewModel.undoMap()
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
        binding.deleteMarkerButton.setOnClickListener {
            viewModel.setMarkerDeletingMode()
        }
        binding.doneGPSMeasureButton.setOnClickListener {
            endGPSSelectingAD()
        }
        binding.deleteRecordButton.setOnClickListener {
            askIfDeleteDataAD()
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
                    viewModel.setForManualSelecting()
                } else {
                    viewModel.setForGPSSelecting()
                }
                sharedViewModel.clearSelectedDamageDataItemFromMap()
                viewModel.setSelectedDamageRecordAsNull()
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

    private fun createMapTileProvider(wmsTileSource: WMSTileSourceRepaired) =
        MapTileProviderBasic(context, wmsTileSource)


    private fun deleteLayerFromMap(layerName: String) {
        mapView.overlays.forEach {
            if (it is TilesOverlayRepaired && it.layerName == layerName)
                mapView.overlays.remove(it)
        }
    }

    /**
     * Načíta do mapy defaultnú vrstvu od Mapniku.
     */
    private fun loadDefaultLayer() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
    }

    /**
     * Metóda načítava mapový komponent do aplikácie, nastaví pre neho všetko potrebné,
     * ňako napríklad mapový podklad, maximálny a minimálny zoomm level...
     */
    private fun setUpMap() {
        setUpMapView()
        setUpZoomController()
        setUpRotationGestureOverlay()
        // https://github.com/osmdroid/osmdroid/issues/295
        setUpMapReceiver()
        clearMapCache()
    }

    private fun setUpMapView() {
        mapView = binding.mapView

        if (viewModel.mapIsInitialised) {
            viewModel.restartStateOfMap(mapView)
        }

        mapView.setDestroyMode(false)
        mapView.setMultiTouchControls(true)
        setUpZoomLevelsOfMap()
    }

    private fun setUpZoomLevelsOfMap() {
        setUpMaxZoomLevel()
        setUpMinZoomLevel()
    }

    private fun setUpMaxZoomLevel() {
        mapView.maxZoomLevel = 30.0
    }

    private fun setUpMinZoomLevel() {
        mapView.minZoomLevel = 4.0
    }

    private fun setUpZoomController() {
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
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
            mapView.controller.setZoom(startZoomLevel)
            mapView.controller.setCenter(startGeoPoint)
        }
    }

    private fun setUpRotationGestureOverlay() {
        // overlay umožňujúci rotáciu mapy
        val rotationGestureOverlay = RotationGestureOverlay(mapView)
        rotationGestureOverlay.isEnabled = true

        mapView.overlays.add(rotationGestureOverlay)
    }

    private fun addMainMarkerToMapIfExists() {
        if (mainMarker != null) {
            removeMainMarkerIfPreviouslyAdded()
            mapView.overlays.add(mainMarker)
        }
    }

    private fun removeMainMarkerIfPreviouslyAdded() {
        mapView.overlays.removeAll { it is Marker && it.id == mainMarkerId }
    }

    private fun removeAllMarkersOfPolygon() {
        mapView.overlays.removeAll { it is Marker && it.id != mainMarkerId }
    }

    private fun zoomToBoundingBox(damageData: DamageData) {
        if (damageData.coordinates.isEmpty()) {
            return
        }
        val boundingBox = BoundingBox.fromGeoPoints(damageData.coordinates)
        val geoPoint = boundingBox.centerWithDateLine
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.getDetailOfPolygonOnMap(geoPoint)
        }
        mapView.post {
            mapView.zoomToBoundingBox(boundingBox, true, 100)
        }

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
                if (viewModel.manualSelecting.value == true) {
                    addMarkerToMapOnClick(p)
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.getDetailOfPolygonOnMap(p)
                    }

                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        // tento receiver sa nakoniec pridá do mapy ako overlay
        mapView.overlays.add(MapEventsOverlay(mReceive))
    }

    private fun clearMapCache() {
        if (!checkIfSavedVersionOfCodeIsSmaller()) {
            return
        }
        uploadVersionOfCode()
        clearCacheOfChangedLayers()
    }

    private fun clearCacheOfChangedLayers() {
        val sqlTileWriter = SqlTileWriter()
        sqlTileWriter.purgeCache(C_parcelLayerName)
        sqlTileWriter.purgeCache(watercourseLayerName)
        sqlTileWriter.purgeCache(vrstevnice50mLayerName)
    }

    private fun checkIfSavedVersionOfCodeIsSmaller(): Boolean {
        val savedVersionOfCodeNumber = getSavedVersionOfCode()
        return savedVersionOfCodeNumber < CURRENT_VERSION_CODE_NUM
    }

    private fun getSavedVersionOfCode(): Int {
        val sharedPreferences = createSharedPrefs() ?: return 0
        return sharedPreferences.getInt(SHARED_PREFS_VERSION_KEY, 0)
    }

    private fun uploadVersionOfCode() {
        val sharedPreferences = createSharedPrefs() ?: return
        with(sharedPreferences.edit()) {
            putInt(SHARED_PREFS_VERSION_KEY,
                CURRENT_VERSION_CODE_NUM)
            apply()
        }
    }

    private fun createSharedPrefs(): SharedPreferences? {
        return requireActivity().getSharedPreferences(
            SHARED_PREFS_FOR_STORING_VERSION_NAME, Context.MODE_PRIVATE)
    }

    private fun setUpForEditingPolygon() {
        viewModel.clearFromDetailOfPolygon()
        viewModel.setForManualSelecting()
        addMarkersOfEditedPolygon()
        viewModel.createDamagePolygon()
    }

    private fun addMarkersOfEditedPolygon() {
        viewModel.listOfGeopointsOfSelectedPolygon.forEach {
            val marker = createMarker(it)
            viewModel.polygonMarkersList.add(marker)
            mapView.overlays.add(marker)
        }
    }

    private fun clearMapFromShownDetail() {
        mapView.overlays.removeAll { it is Polygon }
        mapView.invalidate()
        hideLayoutOfDetail()
    }

    private fun addMarkerToMapOnClick(p: GeoPoint) {
        val marker = createMarker(p)  // vytvorenie markeru
        mapView.overlays.add(marker)
        viewModel.addMarkerToPolygonOnMap(marker)
        viewModel.createDamagePolygon()
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

    private fun showDetailPolygon(list: List<GeoPoint>) {
        mapView.overlays.removeAll { it is Polygon && it.id == detailPolygonId }
        val detailPolygon = Polygon()
        detailPolygon.id = detailPolygonId
        detailPolygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
        detailPolygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
        detailPolygon.points = list
        mapView.overlays?.add(detailPolygon)
        mapView.invalidate()
    }

    /**
     * Metóda slúži na vytvorenie markeru po kliknutí do mapy.
     * @param point súradnice kliknutého bodu v mape
     */
    private fun createMarker(point: GeoPoint): Marker {
        val marker = Marker(mapView)

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
                viewModel.createDamagePolygon()
            }

            override fun onMarkerDrag(marker: Marker?) {
                viewModel.createDamagePolygon()
            }
        }
        )
    }

    /**
     * Vymazania markera a následné prekreslenie polygónu.
     * @param marker konkrétny marker
     */
    private fun deleteMarker(marker: Marker) {
        if (viewModel.markerDeletingMode.value != true) return
        mapView.overlays.remove(marker)
        viewModel.removeMarker(marker)
        viewModel.createDamagePolygon()
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
        if (mapView.zoomLevelDouble < maxZoomLevel) {
            mapView.controller.setZoom(defaultZoomLevel)
        }
        mapView.controller.setCenter(GeoPoint(viewModel.getLastLocation()))
    }

    /**
     * Vycentruje otočenie mapy.
     */
    private fun centerRotationOfMap() {
        mapView.mapOrientation = 0.0f
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
    private fun reAddMarkersToMap() {
        val polygonMarkersList = viewModel.polygonMarkersList
        removeAllMarkersOfPolygon()
        if (polygonMarkersList.isNotEmpty()) {
            for (marker in polygonMarkersList) {
                mapView.overlays.add(marker)
            }
            viewModel.createDamagePolygon()
        }
    }

    private fun setUpViewForDetail() {
        binding.startDrawingButton.visibility = View.GONE
        binding.editPolygonButton.visibility = View.VISIBLE
        binding.deleteRecordButton.visibility = View.VISIBLE
        binding.detailRecordButton.visibility = View.VISIBLE
        setVisibilityOfDetailInformationLayout()
    }

    private fun hideLayoutOfDetail() {
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
    private fun setUpViewForSelecting() {
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
    private fun setUpViewForManualSelecting() {
        setUpViewForSelecting()
        binding.addPointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE
        binding.saveButton.visibility = View.VISIBLE
        binding.deleteMarkerButton.visibility = View.VISIBLE
    }

    /**
     * Metóda zviditeľní buttony, ktoré sa majú zobrazovať počas vyznačovania územia krokovaním
     * a umožní spustenie tohto vyznačovania.
     * Zviditeľní aj hornú lištu v mape, v ktorej sa zobrazuje obvod a obsah územia.
     */
    private fun setUpViewForGPSSelecting() {
        setUpViewForSelecting()
        binding.addPointButton.visibility = View.VISIBLE
        binding.doneGPSMeasureButton.visibility = View.VISIBLE
    }

    /**
     * Metóda skryje všekty buttonu zviditeľnené počas vyznačovania.
     * Skryje aj hornú lištu v mape, v ktorej boli zobrazované obvod a obsah daného územia.
     */
    private fun setDefaultViewOfMapFragment() {
        binding.startDrawingButton.visibility = View.VISIBLE
        hideVisibilityOfAreaCalculationsLayout()
        binding.backButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.addPointButton.visibility = View.GONE
        binding.deleteMarkerButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE

        clearSelectingOfDamageArea()
        drawerLockInterface.unlockDrawer()
    }

    /**
     * Metóda zviditeľňuje alert dialog, ktorý sa pýta používateľa, či chce naozaj
     * ukončiť vyznačovanie územia.
     */
    private fun clearMeasureAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_measure_ad_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                viewModel.setDefaultModeOfMap()
                viewModel.setSelectedDamageRecordAsNull()
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
    private fun clearSelectingOfDamageArea() {
        mapView.overlays.removeAll { (it is Marker && it.id != mainMarkerId) || it is Polygon }
        viewModel.clearNewPolygonData()
        mapView.invalidate()
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
        if (viewModel.polygonMarkersList.size < 3) {
            lessThan3PointsAD()
            return false
        }
        return true
    }

    private fun sendDamageDataToAddFragment(v: View) {
        viewModel.prepareForSaveData()
        navigateToAddDamageFragment(v)
    }

    private fun navigateToAddDamageFragment(v: View) {
        Navigation.findNavController(v)
            .navigate(R.id.action_map_fragment_TO_add_damage_fragment)
    }

    /**
     * Zobrazuje alert dialog, ktorý sa pýta používateľa, či chce ukončiť vynzačovania
     * územia krokovaním.
     */
    private fun endGPSSelectingAD() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.end_gps_measure_ad_title))
            .setPositiveButton(R.string.button_positive_text) { _, _ -> changeMarkersForManualSelecting() }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    /**
     * Prídáva body, resp. markery na mapu počas vyznačovania územia krokovaním.
     */
    private fun addPoint() {
        val marker = Marker(mapView)

        marker.setOnMarkerClickListener { _, _ ->
            false
        }
        marker.icon =
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_marker_polygon) }

        marker.position = viewModel.getLastLocation()
            ?.let { GeoPoint(it.latitude, viewModel.getLastLocation()!!.longitude) }
        viewModel.addMarkerToPolygonOnMap(marker)
        mapView.overlays.add(marker)
        viewModel.createDamagePolygon()  // prekreslenie polygónu
    }

    /**
     * Táto metóda po krokovom vyznačovaní územia zmení markerom ikonky, aplikuje na nich listener a pod.
     * To preto, aby bolo možné ešte prípadne zmeniť polygón.
     */
    private fun changeMarkersForManualSelecting() {
        setUpViewForManualSelecting()
        val list = viewModel.polygonMarkersList
        list.forEach {
            it.icon = polyMarkerIcon
            applyDraggableListenerOnMarker(it)
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.setOnMarkerClickListener { m, _ ->
                deleteMarker(m)
                true
            }}
        mapView.invalidate()
    }

    private fun setUpConnectionLiveData() {
        locationLiveData = LocationLiveData(requireContext())
        setUpLocationReceiver()
    }

    private fun setUpLocationReceiver() {
        locationLiveData?.observe(viewLifecycleOwner) {
            viewModel.setLastLocation(it)
        }
    }

    private fun askIfDeleteDataAD() {
        AlertDialog.Builder(requireContext())  //
            .setTitle(R.string.if_delete_record_title)
            .setPositiveButton(R.string.button_positive_text) { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                sharedViewModel.prepareToDelete(viewModel.getSelectedDamageRecord())
            }
            .setNegativeButton(R.string.button_negative_text) { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun initSetUp() {
        checkForPermissions()
        setUpViewModel()
        setUpObservers()
        setUpAllThatIsRelatedWithViews()
        setUpNavController()
    }

    private fun setUpAllThatIsRelatedWithViews() {
        loadUserPolygons()
        setUpMap()
        setMarkersIcon()
        setUpButtonsListeners()
        setUpDrawerLockInterface()
        addExistingLayers()
        addMainMarkerToMapIfExists()
    }

    private fun loadUserPolygons() {
        if (viewModel.loadedMapLayerWithUserData.value != true) {
            viewModel.loadUserPolygons()
        }
    }

    private fun setUpObservers() {
        observeLastLocation()
        observeDamageDataItem()
        observeLoadingValue()
        observeMapLayers()
        observeThatShouldDisplayDefaultLayer()
        observeLayerToDelete()
        observeNoInternetForSelectedLayers()
        observeGeopoints()
        observeAreaAndPerimeter()
        observeDetailOfPolygonOnMap()
        observeSelecting()
        observeNewPolygonMarkersHistory()
        observeMarkersToAddOrRemoveFromMap()
        observeIfDeletingWasSuccessful()
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

    private fun observeMapLayers() {
        observeWMSBaseLayer()
        observeWMSMapLayer()
        observeWMSLayerWithUserData()
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
                mapView.setTileSource(it)
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
                viewModel.userDataLayer = it
                viewModel.setIfLoadedMapLayerWithUserData(true)
            }

        }
    }

    private fun addNewLayerToMap(layerName: String, wmsTileSource: WMSTileSourceRepaired) {
        val tilesOverlayRepaired = createTilesOverlay(layerName, wmsTileSource)
        mapView.overlays?.add(0, tilesOverlayRepaired)
        reAddMarkersToMap()
        mapView.invalidate()
    }

    private fun addNewLayerWithUserDataToMap(wmsTileSource: WMSTileSourceRepaired) {
        val tilesOverlayRepaired = createTilesOverlay(userDamagesLayerName, wmsTileSource)
        if (viewModel.detailModeShown.value == true && mapView.overlays.size - 1 > -1) {
            mapView.overlays?.add(mapView.overlays.size - 1, tilesOverlayRepaired)
        } else {
            mapView.overlays?.add(tilesOverlayRepaired)
        }

        reAddMarkersToMap()
        mapView.invalidate()
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
            it.getContentIfNotHandled().let { value ->
                if (value != null) {
                    Toast.makeText(context,
                        "Na výber iných mapových vrstiev musíte mať prístup na internet.",
                        Toast.LENGTH_SHORT).show()
                }
            }
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
        addExistingUserDataLayer()
    }

    private fun addExistingBaseLayer() {
        if (viewModel.selectedBaseLayer != null) {
            mapView.setTileSource(viewModel.selectedBaseLayer)
        }
    }

    private fun addExistingMapLayers() {
        viewModel.mapLayersList.forEach {
            addNewLayerToMap(it.first, it.second)
        }
    }

    private fun addExistingUserDataLayer() {
        if (viewModel.userDataLayer != null) {
            addNewLayerWithUserDataToMap(viewModel.userDataLayer!!)
            addMainMarkerToMapIfExists()
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
            huntingGroundsLayerName -> menu.findItem(R.id.menu_hunting_grounds).isChecked =
                true
            watercourseLayerName -> menu.findItem(R.id.menu_watercourse).isChecked =
                true
//            vrstevnice10mLayerName -> menu.findItem(R.id.menu_vrstevnice10m).isChecked =
//                true
            vrstevnice50mLayerName -> menu.findItem(R.id.menu_vrstevnice50m).isChecked =
                true
            buildingsListLayerName -> menu.findItem(R.id.menu_buildings).isChecked =
                true
        }
    }

    private fun observeGeopoints() {
        viewModel.geopoints.observe(viewLifecycleOwner) {
            if (it != null) {
                onNewGeopoints(it)
            }
        }
    }

    private fun onNewGeopoints(geopoints: List<GeoPoint>) {
        deleteOldPolygons()
        addNewPolygonToMap(geopoints)
    }

    private fun observeNewPolygonMarkersHistory() {
        viewModel.newPolygonMarkersHistory.observe(viewLifecycleOwner) { list ->
            binding.backButton.visibility = if (list.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun addNewPolygonToMap(geoPoints: List<GeoPoint>) {
        val newPolygon = Polygon()
        newPolygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
        newPolygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
        newPolygon.points = geoPoints
        mapView.overlays.add(newPolygon)
        mapView.invalidate()
    }

    private fun deleteOldPolygons() {
        mapView.overlays.removeAll { it is Polygon }
    }

    private fun observeAreaAndPerimeter() {
        observeActualArea()
        observeActualPerimeter()
    }

    private fun observeActualArea() {
        viewModel.actualArea.observe(viewLifecycleOwner) { actualArea ->
            val displayedTextArea = "${
                actualArea.toInt()
            } m\u00B2"
            binding.layoutContainer.areaCalculationsLayout.area.text = displayedTextArea
        }
    }

    private fun observeActualPerimeter() {
        viewModel.actualPerimeter.observe(viewLifecycleOwner) { actualPerimeter ->
            val displayedTextPerimeter = "${
                actualPerimeter.toInt()
            } m"
            binding.layoutContainer.areaCalculationsLayout.perimeter.text = displayedTextPerimeter
        }
    }

    private fun observeSelecting() {
        observeManualSelecting()
        observeGPSSelecting()
        observeIfDefaultModeOfMap()
        observeMarkerDeletingMode()
    }

    private fun observeDetailOfPolygonOnMap() {
        observePolygonDetailMode()
        observeDetailDamageData()
        observeGeopointsOfSelectedPolygon()
    }

    private fun observeManualSelecting() {
        viewModel.manualSelecting.observe(viewLifecycleOwner) { value ->
            if (value) {
                setUpViewForManualSelecting()
            }
        }
    }

    private fun observeGPSSelecting() {
        viewModel.gpsSelecting.observe(viewLifecycleOwner) { value ->
            if (value) {
                setUpViewForGPSSelecting()
            }
        }
    }

    private fun observeIfDefaultModeOfMap() {
        viewModel.isDefaultModeOfMap.observe(viewLifecycleOwner) { value ->
            value.getContentIfNotHandled().let {
                if (it == true) {
                    setDefaultViewOfMapFragment()
                }
            }
        }
    }

    private fun observeMarkerDeletingMode() {
        viewModel.markerDeletingMode.observe(viewLifecycleOwner) { value ->
            if (value) {
                setDeletingModeOfDeleteMarkerButton()
            } else {
                unsetDeletingModeOfDeleteMarkerButton()
            }
        }
    }

    private fun observePolygonDetailMode() {
        viewModel.detailModeShown.observe(viewLifecycleOwner) { value ->
            if (viewModel.manualSelecting.value == true) {
                return@observe
            }
            if (value) {
                setUpViewForDetail()
            } else {
                setDefaultViewOfMapFragment()
            }
        }
    }

    private fun setDeletingModeOfDeleteMarkerButton() {
        binding.deleteMarkerButton.backgroundTintList = ColorStateList.valueOf(Color
            .parseColor("#EA0A0A"))
    }

    private fun unsetDeletingModeOfDeleteMarkerButton() {
        binding.deleteMarkerButton.backgroundTintList = ColorStateList.valueOf(Color
            .parseColor("#B4802E"))
    }

    private fun observeMarkersToAddOrRemoveFromMap() {
        observeMarkersToAddToMap()
        observeMarkersToRemoveFromMap()
    }

    private fun observeMarkersToAddToMap() {
        viewModel.markersToAddToMap.observe(viewLifecycleOwner) { list ->
            list.getContentIfNotHandled().let { listOfMarkers ->
                listOfMarkers?.forEach { mapView.overlays.add(it) }
            }
        }
    }

    private fun observeMarkersToRemoveFromMap() {
        viewModel.markersToRemoveFromMap.observe(viewLifecycleOwner) { list ->
            list.getContentIfNotHandled().let { listOfMarkers ->
                listOfMarkers?.forEach { mapView.overlays.remove(it) }
            }
        }
    }

    private fun observeDetailDamageData() {
        viewModel.detailDamageData.observe(viewLifecycleOwner) {
            if (viewModel.manualSelecting.value == true) {
                return@observe
            }
            if (it != null) {
                showDetailInfo(it)
            } else {
                clearMapFromShownDetail()
            }
        }
    }

    private fun observeGeopointsOfSelectedPolygon() {
        viewModel.geopointsOfSelectedPolygon.observe(viewLifecycleOwner) {
            if (it != null) {
                showDetailPolygon(it)
            }
        }
    }

    private fun observeIfDeletingWasSuccessful() {
        sharedViewModel.deletingWasSuccessful.observe(viewLifecycleOwner) { value ->
            value.getContentIfNotHandled()?.let { wasSuccessful ->
                if (wasSuccessful) {
                    Toast.makeText(context, getString(R.string.successful_deleting),
                        Toast.LENGTH_SHORT).show()
                    viewModel.onSuccessfulDelete()
                    clearMapFromShownDetail()
                } else {
                    Toast.makeText(context, getString(R.string.unsuccessful_deleting),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
