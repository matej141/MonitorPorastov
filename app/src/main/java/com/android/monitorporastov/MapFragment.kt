package com.android.monitorporastov

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.android.monitorporastov.databinding.FragmentMapBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay


class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private lateinit var fused: FusedLocationService
    private lateinit var mMap: MapView
    private var mainMarker: Marker? = null
    private lateinit var mMapController: IMapController
    private var centered = false
    private lateinit var lastLocation: Location
    private var geoPoints = mutableListOf<GeoPoint>()
    private var newPolygon: Polygon = Polygon()
    private var polyMarkers = mutableListOf<Marker>()
    private var defaultZoomLevel = 18.0
    private var manualMeasure = false
    private var gpsMeasure = false
    private var lastMarker: Marker? = null
    private var firstLoad = true
    private lateinit var alertDialogFactory: AlertDialogFactory
    private lateinit var drawerLockInterface: DrawerLockInterface
    private lateinit var polyMarkerIcon: Drawable
    private var buttonDeleting = false

    private var mPrefs: SharedPreferences? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val interval = 1000
    private val fastestInterval = 1000L
    private val polyMarkersHistory = mutableListOf<MutableList<Marker>>()

    private val binding get() = _binding!!
    private val polygons = mutableListOf<Polygon>()
    private val newPolygonDefaultId = "new polygon"
    private val polygonOutlineColorStr = "#2CE635"
    private val polygonFillColorStr = "#33EA3535"
    private var actualPerimeter = 0.0
    private var actualArea = 0.0

    // https://stackoverflow.com/questions/40760625/how-to-check-permission-in-fragment/68347506#68347506
    private var activityResultLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        private const val PREFS_NAME = "MAP_FRAGMENT_PREFS"
        private const val PREFS_TILE_SOURCE = "tileSource"
        private const val PREFS_LATITUDE_STRING = "latitudeString"
        private const val PREFS_LONGITUDE_STRING = "longitudeString"
        private const val PREFS_ORIENTATION = "orientation"
        private const val PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble"
    }

    init {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            var allAreGranted = true
            for (b in result.values) {
                allAreGranted = allAreGranted && b
            }

            if (allAreGranted) {
                startLocationUpdates()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        alertDialogFactory = AlertDialogFactory(requireContext())
        mPrefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // setUpLocationService()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval((interval).toLong())
            .setFastestInterval((fastestInterval))

        setUpCallback()

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentMapBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(
            context))
        // centered = false
        val neededPermission = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        activityResultLauncher.launch(neededPermission)
        // mainMarker = null

        setUpMap()


        if (manualMeasure) {
            setUpForManualMeasure()
        }
        if (gpsMeasure) {
            setUpForGPSMeasure()
        }
        setPolyMarkerIcon()
        setUpButtonsListeners()
        drawerLockInterface = activity as DrawerLockInterface

        // https://www.py4u.net/discuss/616531
        val navController = findNavController()
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("key")?.observe(
            viewLifecycleOwner) { result ->
            if (result) {
                val polygon = Polygon()
                polygon.points = newPolygon.actualPoints
                polygon.outlinePaint.color = Color.parseColor(polygonOutlineColorStr)
                polygon.fillPaint.color = Color.parseColor(polygonFillColorStr)
                polygons.add(polygon)
                drawExistingPolygons()
                setDefault()
            }
        }
        drawExistingPolygons()
        // drawerLockInterface.lockDrawer()
    }

    private fun drawExistingPolygons() {
        CoroutineScope(Dispatchers.Main).launch {
            polygons.forEach { mMap.overlays.add(it) }
            mMap.invalidate()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            if (locationResult == null) {
                return
            }
            lastLocation = locationResult.lastLocation
            if (firstLoad) {
                centerMap()
                mMapController.setZoom(defaultZoomLevel)
                firstLoad = false
            }

            updateLocation(locationResult.lastLocation)
        }

    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun setPolyMarkerIcon() {
        val height = 150
        val width = 150
        val bitmap =
            BitmapFactory.decodeResource(context?.resources, R.drawable.ic_marker_polygon_add)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        polyMarkerIcon = BitmapDrawable(resources, scaledBitmap)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private fun setUpCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {

            if (manualMeasure) {
                clearDrawingAlert()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Chcete ukončiť aplikáciu?")
                    .setPositiveButton("Áno") { _, _ -> requireActivity().finish() }
                    .setNegativeButton("Nie") { dialog, _ -> dialog.cancel() }
                    .create()
                    .show()
            }
        }
    }

    private fun setUpButtonsListeners() {
        binding.startDrawingButton.setOnClickListener {
            showMeasureAD()
        }
        binding.buttonCenter.setOnClickListener {
            centerMap()
        }
        binding.backButton.setOnClickListener {
            undoMap()
        }
        binding.deleteButton.setOnClickListener {
            clearDrawingAlert()
        }
        binding.saveButton.setOnClickListener {
            saveMeasure(it)
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
    }

    private fun endGPSMeasureAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Naozaj chcete ukončiť meranie krokovaním?")
            .setPositiveButton("Áno") { _, _ -> setToManualMeasure() }
            .setNegativeButton("Nie") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun setToManualMeasure() {
        setUpForManualMeasure()
        polyMarkers.forEach { it.icon = polyMarkerIcon }
        polyMarkers.forEach { applyDraggableListener(it) }
        polyMarkers.forEach { it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
        polyMarkers.forEach {
            it.setOnMarkerClickListener { m, _ ->
                deletePoint(m)
                true
            }
        }
    }

    private fun setButtonDeleting() {
        if (!buttonDeleting) {
            buttonDeleting = true
            binding.deletePointButton.backgroundTintList = ColorStateList.valueOf(Color
                .parseColor("#EA0A0A"))
        } else {
            buttonDeleting = false
            binding.deletePointButton.backgroundTintList = ColorStateList.valueOf(Color
                .parseColor("#B4802E"))
        }
    }

    private fun saveMeasure(v: View) {
        if (polyMarkers.size < 3) {
            AlertDialog.Builder(requireContext())
                .setTitle("Nezadali ste dostatočný počet bodov")
                .setMessage("Na uloženie záznamu musíte zadať aspoň 3 body.")
                .setNegativeButton("Ok") { dialog, _ -> dialog.cancel() }
                .create()
                .show()
        } else {
            val bundle = Bundle()
            bundle.putDouble(AddDamageFragment.ARG_AREA_ID, actualArea)
            bundle.putDouble(AddDamageFragment.ARG_PERIMETER_ID, actualPerimeter)
            Navigation.findNavController(v)
                .navigate(R.id.action_mapFragment_to_addDrawingFragment, bundle)
        }
    }

    private fun addPoint() {
        geoPoints = mutableListOf()
        val marker = Marker(mMap)

        //marker.isDraggable = true
        // applyDraggableListener(marker)
        marker.setOnMarkerClickListener { _, _ ->
            false
        }
        marker.icon =
            context?.let { ContextCompat.getDrawable(it, R.drawable.ic_marker_polygon) }

        marker.position = GeoPoint(lastLocation.latitude, lastLocation.longitude)
        polyMarkersHistory.add(polyMarkers.toMutableList())
        polyMarkers.add(marker)
        mMap.overlays.add(marker)
        // https://stackoverflow.com/questions/54574152/how-to-remove-markers-from-osmdroid-map
        lastMarker = marker
        drawPolygon()
    }

    private fun showMeasureAD() {
        if (!locationCheck()) {
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Vyberte spôsob:")
            .setAdapter(setUpAdapterForMeasureAD()) { dialog, item ->

                if (item == 0) {
                    setUpForManualMeasure()
                } else {
                    setUpForGPSMeasure()
                }
            }
            .setNegativeButton("Zrušiť") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    private fun measureAlertDialogItems(): Array<DialogItem> {
        return arrayOf(
            DialogItem("Manuálne vyznačenie", R.drawable.ic_touch),
            DialogItem("Krokové vyznačenie", R.drawable.ic_walk_colored))
    }

    private fun setUpAdapterForMeasureAD(): ListAdapter {
        val items = measureAlertDialogItems()
        val adapter: ListAdapter = object : ArrayAdapter<DialogItem>(requireContext(),
            android.R.layout.select_dialog_item,
            android.R.id.text1,
            items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                //Use super class to create the View
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById(android.R.id.text1) as TextView

                //Put the image on the TextView
                tv.setCompoundDrawablesWithIntrinsicBounds(items[position].icon, 0, 0, 0)

                //Add margin between image and text (support various screen densities)
                val margin = (10 * resources.displayMetrics.density + 0.5f).toInt()
                tv.compoundDrawablePadding = margin
                return view
            }
        }
        return adapter
    }

    private fun setUpMap() {
        mMap = binding.mapView
        mMap.setDestroyMode(false)
        // mMap.mapOrientation = 45.0f

//        runBlocking(Dispatchers.IO) {
//            var c: HttpURLConnection? = null
//            var inputStream: InputStream? = null // we should use IO thread here !
//            c = URL("https://zbgisws.skgeodesy.sk/zbgis_ortofoto_wms/service.svc/get").openConnection() as HttpURLConnection
//            inputStream = c.inputStream
//            val wmsEndpoint: WMSEndpoint = WMSParser.parse(inputStream)
//
//            inputStream.close()
//            c.disconnect()
//            val source = WMSTileSource.createFrom(wmsEndpoint, wmsEndpoint.layers[0])
//
//            mMap.setTileSource(source)
//        }

        mMap.setTileSource(TileSourceFactory.MAPNIK)

        mMap.setMultiTouchControls(true)
        mMap.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mMapController = mMap.controller
        mMap.maxZoomLevel = 30.0
        val startZoomLevel = 5.0
        val startGeoPoint = GeoPoint(48.148598, 17.107748)
        if (firstLoad) {
            mMapController.setZoom(startZoomLevel)
            mMapController.setCenter(startGeoPoint)
        }

        val mRotationGestureOverlay = RotationGestureOverlay(mMap)
        mRotationGestureOverlay.isEnabled = true

        mMap.overlays.add(mRotationGestureOverlay)
        // https://github.com/osmdroid/osmdroid/issues/295
        setUpMapReceiver()
        redrawPolygon()
        if (mainMarker != null) {
            mMap.overlays.add(mainMarker)
        }
    }

    private fun centerRotationOfMap() {
        mMap.mapOrientation = 0.0f
    }

    private fun redrawPolygon() {
        if (polyMarkers.isNotEmpty()) {
            for (marker in polyMarkers) {
                mMap.overlays.add(marker)
            }
            drawPolygon()
        }
    }

    private fun setUpMapReceiver() {
        val mReceive: MapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (manualMeasure) {
                    geoPoints = mutableListOf()
                    val marker = Marker(mMap)

                    marker.isDraggable = true
                    applyDraggableListener(marker)

                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.isDraggable = true
                    applyDraggableListener(marker)
//                    marker.icon =
//                    context?.let { ContextCompat.getDrawable(it, R.drawable.ic_marker_polygon_add) }

                    marker.icon = polyMarkerIcon
                    marker.position = GeoPoint(p.latitude, p.longitude)
                    polyMarkersHistory.add(polyMarkers.toMutableList())
                    polyMarkers.add(marker)
                    mMap.overlays.add(marker)
                    // https://stackoverflow.com/questions/54574152/how-to-remove-markers-from-osmdroid-map

                    // mMap.overlays.forEach { if (it is Polygon) mMap.overlays.remove(it) }
                    lastMarker = marker
                    marker.setOnMarkerClickListener { m, _ ->
                        deletePoint(m)
                        true
                    }

                    drawPolygon()
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        mMap.overlays.add(MapEventsOverlay(mReceive))
    }

    private fun deletePoint(marker: Marker) {
        if (!buttonDeleting) return
        mMap.overlays.remove(marker)
        polyMarkersHistory.add(polyMarkers.toMutableList())
        polyMarkers = polyMarkers.filter { it != marker }.toMutableList()
        drawPolygon()
    }

    override fun onStart() {
        super.onStart()
//        fused.startLocationUpdates()
        if (!firstLoad) {
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
        }

    }


    override fun onAttach(context: Context) {
        super.onAttach(context)


    }

    override fun onDetach() {
        super.onDetach()
        //fused.stopLocationUpdates()
        //mMap.onDetach()
    }

    override fun onResume() {
        super.onResume()
        // fused.startLocationUpdates()

        // mMap.onResume()
    }

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
            edit.apply()
        }
        mMap.onPause()

    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

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

//        if (lastMarker == null) return
//        polyMarkers.removeLast()
//        mMap.overlays.remove(lastMarker)
//        lastMarker = if (polyMarkers.isEmpty()) {
//            null
//        } else {
//            polyMarkers.last()
//        }
        drawPolygon()
    }

    private fun centerMap() {
        if (!locationCheck()) {
            return
        }
        if (mMap.zoomLevelDouble < 25) {
            mMapController.setZoom(defaultZoomLevel)
        }
        mMapController.setCenter(GeoPoint(lastLocation))
    }

    private fun locationCheck(): Boolean {
        if (!this::lastLocation.isInitialized) {
            Toast.makeText(context, "Poloha ešte nebola získaná, počkajte prosím",
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun setUpForMeasure() {
        binding.startDrawingButton.visibility = View.GONE
        binding.areCalculationsLayout.root.visibility = View.VISIBLE

        binding.deleteButton.visibility = View.VISIBLE

        drawerLockInterface.lockDrawer()
    }

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

    private fun setUpForGPSMeasure() {
        setUpForMeasure()
        gpsMeasure = true
        binding.addPointButton.visibility = View.VISIBLE
        binding.doneGPSMeasureButton.visibility = View.VISIBLE
    }

    private fun setDefault() {
        manualMeasure = false
        gpsMeasure = false
        binding.startDrawingButton.visibility = View.VISIBLE
        binding.areCalculationsLayout.root.visibility = View.GONE
        binding.backButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.addPointButton.visibility = View.GONE
        binding.deletePointButton.visibility = View.GONE
        binding.doneGPSMeasureButton.visibility = View.GONE
        clearMeasure()
        drawerLockInterface.unlockDrawer()
    }

    private fun clearDrawingAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle("Chcete zahodiť aktuálne meranie?")
            .setPositiveButton("Áno") { _, _ -> setDefault() }
            .setNegativeButton("Nie") { dialog, _ -> dialog.cancel() }
            .create()
            .show()
    }

    // https://stackoverflow.com/questions/30124960/how-to-implement-a-draggable-extendedoverlayitem-on-osmdroid
    fun applyDraggableListener(marker: Marker) {
        marker.isDraggable = true

        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker?) {
                geoPoints = mutableListOf()

            }

            override fun onMarkerDragEnd(marker: Marker) {
                drawing()
            }

            override fun onMarkerDrag(marker: Marker?) {
                //mMap.overlays.forEach { if (it is Polygon) mMap.overlays.remove(it) }
                drawing()
            }

            fun drawing() {
                drawPolygon()
            }
        }

        )
    }

    private fun clearMeasure() {
        mMap.overlays.forEach {
            if (it is Polygon || it is Marker && it.id != "Main marker") mMap.overlays.remove(it)
        }
        geoPoints.clear()
        polyMarkers.clear()
        mMap.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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

        computeArea()
        computePerimeter()
        mMap.invalidate()
    }

    private fun computeArea() {
        val geoPoints = mutableListOf<GeoPoint>()
        polyMarkers.forEach { geoPoints.add(GeoPoint(it.position.latitude, it.position.longitude)) }
        actualArea = SphericalUtil.computeArea(geoPoints.map {
            LatLng(it.latitude,
                it.longitude)
        }.toList())
        val displayedText = "${
            "%.${3}f".format(actualArea)
        } m\u00B2"
        binding.areCalculationsLayout.area.text = displayedText
    }

    private fun computePerimeter() {
        val geoPoints = mutableListOf<GeoPoint>()
        polyMarkers.forEach { geoPoints.add(GeoPoint(it.position.latitude, it.position.longitude)) }
        actualPerimeter = SphericalUtil.computeLength(geoPoints.map {
            LatLng(it.latitude,
                it.longitude)
        }.toList())
        val displayedText = "${
            "%.${3}f".format(actualPerimeter)
        } m"
        binding.areCalculationsLayout.perimeter.text = displayedText
    }

    private fun setUpLocationService() {
        fused = FusedLocationService(requireContext()) {
            lastLocation = it
            updateLocation(it)
            centerController(it)
        }
        fused.startLocationUpdates()

    }

    private fun centerController(l: Location) {

        mMapController.setCenter(GeoPoint(l.latitude, l.longitude))
        // mMapController.setZoom(defaultZoomLevel)


    }

    private fun updateLocation(l: Location) {
        val position = GeoPoint(l.latitude, l.longitude)
        if (mainMarker == null) {
            mainMarker = Marker(mMap)
            mainMarker!!.setOnMarkerClickListener { marker, mapView ->
                false
            }
            mainMarker!!.id = "Main marker"
            // https://www.programcreek.com/java-api-examples/?class=org.osmdroid.views.overlay.Marker&method=setIcon
            // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
            mainMarker!!.icon =
                context?.let { ContextCompat.getDrawable(it, R.drawable.ic_map_marker) }
            mMap.overlays.add(mainMarker)
        }

        mainMarker!!.position = position
        mMap.invalidate()

    }

    override fun onDestroy() {
        super.onDestroy()
        context?.getSharedPreferences(PREFS_NAME, 0)?.edit()?.clear()?.apply()
        val preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
        _binding = null
    }

}