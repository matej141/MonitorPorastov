package com.android.monitorporastov

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
import androidx.preference.PreferenceManager
import com.android.monitorporastov.databinding.FragmentMapBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
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
    private var polygon: Polygon = Polygon()
    private var polyMarkers = mutableListOf<Marker>()
    private var defaultZoomLevel = 18.0
    private var manualDrawing = false
    private var lastMarker: Marker? = null
    private var firstLoad = true
    private lateinit var alertDialogFactory: AlertDialogFactory
    private lateinit var drawerLockInterface: DrawerLockInterface

    private val PREFS_NAME = "org.andnav.osm.prefs"
    private val PREFS_TILE_SOURCE = "tilesource"
    private val PREFS_LATITUDE_STRING = "latitudeString"
    private val PREFS_LONGITUDE_STRING = "longitudeString"
    private val PREFS_ORIENTATION = "orientation"
    private val PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble"

    private var mPrefs: SharedPreferences? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val interval = 1000
    private val fastestInterval = 1000L

    private val binding get() = _binding!!

    // https://stackoverflow.com/questions/40760625/how-to-check-permission-in-fragment/68347506#68347506
    private var activityResultLauncher: ActivityResultLauncher<Array<String>>
    init {
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            var allAreGranted = true
            for(b in result.values) {
                allAreGranted = allAreGranted && b
            }

            if(allAreGranted) {
                startLocating()
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


        if (manualDrawing) {
            setUpForManualMeasure()
        }
        setUpButtonsListeners()
        drawerLockInterface = activity as DrawerLockInterface
        // drawerLockInterface.lockDrawer()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            if (locationResult == null) {
                return
            }
            lastLocation = locationResult.lastLocation
            if (firstLoad) {
                centerController(lastLocation)
                mMapController.setZoom(defaultZoomLevel)
                firstLoad = false
            }

            updateLocation(locationResult.lastLocation)
        }

    }

    @SuppressLint("MissingPermission")
    private fun startLocating() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private fun setUpCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {

            if (manualDrawing) {
                clearDrawingAlert()
            } else {
                AlertDialog.Builder(requireContext())
                    .setMessage("Chcete ukončiť aplikáciu?")
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
            // Navigation.findNavController(it).navigateUp()
            Navigation.findNavController(it).navigate(R.id.action_mapFragment_to_addDrawingFragment)
        }
    }

    private fun showMeasureAD() {
        AlertDialog.Builder(requireContext())
            .setTitle("Vyberte spôsob:")
            .setAdapter(setUpAdapterForMeasureAD()) { dialog, item ->

                if (item == 0) {
                    setUpForManualMeasure()
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

    private fun firstSetUp() {
        firstLoad = !firstLoad
//        CoroutineScope(Dispatchers.Main).launch {
//            val result: Deferred<Location> = async { setUpLocationService() }
//            lastLocation = result.await()
//            mMapController.setCenter(GeoPoint(lastLocation.latitude, lastLocation.longitude))
//        }


    }

    private fun setUpMap() {
        mMap = binding.mapView
        mMap.setDestroyMode(false)
        // mMap.mapOrientation = 45.0f
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.setMultiTouchControls(true)
        mMap.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mMapController = mMap.controller
        mMap.maxZoomLevel = 30.0
        mMapController.setZoom(defaultZoomLevel)
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
                if (manualDrawing) {
                    geoPoints = mutableListOf()
                    val marker = Marker(mMap)

                    marker.isDraggable = true
                    applyDraggableListener(marker)
                    marker.setOnMarkerClickListener { marker, mapView ->
                        //marker.icon = context?.let { ContextCompat.getDrawable(it, R.drawable.ic_map_marker) }
                        false
                    }

                    val height = 150
                    val width = 150
                    val bitmap = BitmapFactory.decodeResource(context?.resources, R.drawable.ic_marker_polygon)
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)

                    marker.isDraggable = true
                    applyDraggableListener(marker)
                    marker.setOnMarkerClickListener { marker, mapView ->
                        //marker.icon = context?.let { ContextCompat.getDrawable(it, R.drawable.ic_map_marker) }
                        false
                    }
//                    marker.icon =
//                    context?.let { ContextCompat.getDrawable(it, R.drawable.ic_marker_polygon) }
                    val bitmapd =  BitmapDrawable(resources, scaledBitmap)
                    marker.icon = bitmapd
                    marker.position = GeoPoint(p.latitude, p.longitude)
                    polyMarkers.add(marker)
                    mMap.overlays.add(marker)
                    // https://stackoverflow.com/questions/54574152/how-to-remove-markers-from-osmdroid-map

                    // mMap.overlays.forEach { if (it is Polygon) mMap.overlays.remove(it) }
                    lastMarker = marker
                    drawPolygon()
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        mMap.overlays.add(MapEventsOverlay(mReceive))
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
        if (lastMarker == null) return
        polyMarkers.removeLast()
        mMap.overlays.remove(lastMarker)
        lastMarker = if (polyMarkers.isEmpty()) {
            null
        } else {
            polyMarkers.last()
        }
        drawPolygon()
    }

    private fun centerMap() {
        if (!this::lastLocation.isInitialized) {
            Toast.makeText(context, "Poloha ešte nebola získaná, počkajte prosím",
                Toast.LENGTH_SHORT).show()
            return
        }
        if (mMap.zoomLevelDouble < 25) {
            mMapController.setZoom(defaultZoomLevel)
        }
        mMapController.setCenter(GeoPoint(lastLocation))
    }

    private fun setUpForMeasure() {
        binding.startDrawingButton.visibility = View.GONE
        binding.areCalculationsLayout.root.visibility = View.VISIBLE
    }

    private fun setUpForManualMeasure() {
        setUpForMeasure()
        binding.backButton.visibility = View.VISIBLE
        binding.deleteButton.visibility = View.VISIBLE
        binding.saveButton.visibility = View.VISIBLE
        manualDrawing = true
        drawerLockInterface.lockDrawer()
    }

    private fun setDefault() {
        manualDrawing = false
        binding.startDrawingButton.visibility = View.VISIBLE
        binding.areCalculationsLayout.root.visibility = View.GONE
        binding.backButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        clearMeasure()
        drawerLockInterface.unlockDrawer()
    }

    private fun clearDrawingAlert() {
        AlertDialog.Builder(requireContext())
            .setMessage("Chcete zahodiť aktuálne meranie?")
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

            }

            override fun onMarkerDrag(marker: Marker?) {
                //mMap.overlays.forEach { if (it is Polygon) mMap.overlays.remove(it) }
                polyMarkers.forEach {
                    geoPoints.add(GeoPoint(it.position.latitude,
                        it.position.longitude))
                }
                drawPolygon()
            }
        })
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
        mMap.overlays.forEach { if (it is Polygon) mMap.overlays.remove(it) }
        geoPoints = mutableListOf()
        polyMarkers.forEach { geoPoints.add(GeoPoint(it.position.latitude, it.position.longitude)) }
        // https://github.com/osmdroid/osmdroid/wiki/Markers,-Lines-and-Polygons-(Kotlin)
        polygon = Polygon()
        polygon.id = "New poly"
        polygon.outlinePaint.color = Color.GREEN
        polygon.fillPaint.color = Color.parseColor("#1EFFE70E")
        polygon.points = geoPoints
        mMap.overlays.add(polygon)

        computeArea()
        computePerimeter()
        mMap.invalidate()
    }

    private fun computeArea() {
        val displayedText = "${
            "%.${3}f".format(SphericalUtil.computeArea(geoPoints.map {
                LatLng(it.latitude,
                    it.longitude)
            }.toList()))
        } m\u00B2"
        binding.areCalculationsLayout.area.text = displayedText
    }

    private fun computePerimeter() {
        val displayedText = "${
            "%.${3}f".format(SphericalUtil.computeLength(geoPoints.map {
                LatLng(it.latitude,
                    it.longitude)
            }.toList()))
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
        if (!centered) {
            mMapController.setCenter(GeoPoint(l.latitude, l.longitude))
           // mMapController.setZoom(defaultZoomLevel)
            centered = !centered
        }
    }

    private fun updateLocation(l: Location) {
        val position = GeoPoint(l.latitude, l.longitude)
        if (mainMarker == null) {
            mainMarker = Marker(mMap)
            mainMarker!!.setOnMarkerClickListener { marker, mapView ->
                false
            }
            mainMarker!!.id = "Main marker"
            val box = mMap.boundingBox
            // https://www.programcreek.com/java-api-examples/?class=org.osmdroid.views.overlay.Marker&method=setIcon
            // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
            mainMarker!!.icon =
                context?.let { ContextCompat.getDrawable(it, R.drawable.ic_map_marker) }
            mMap.overlays.add(mainMarker)
        }

        mainMarker!!.position = position

    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MapFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MapFragment().apply {
                arguments = Bundle().apply {

                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        context?.getSharedPreferences(PREFS_NAME, 0)?.edit()?.clear()?.apply()
        val preferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
    }

}