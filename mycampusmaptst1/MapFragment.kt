package io.github.mycampusmaptst1

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.mycampusmaptst1.gps_nav.DestinationMarker
import io.github.mycampusmaptst1.new_wifi_navi.AdvancedPositioningManager
import io.github.mycampusmaptst1.new_wifi_navi.OccupancyGridManager
import io.github.mycampusmaptst1.overlays.RouteOverlay
import io.github.mycampusmaptst1.overlays.SharedViewModel
import io.github.mycampusmaptst1.utils.Bounds
import io.github.mycampusmaptst1.utils.PermissionHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

class MapFragment : Fragment(R.layout.map_fragment) {
    private var lastToastTime = 0L
    companion object {
        private const val TOAST_COOLDOWN = 2000
        private val CAMPUS_CENTER = GeoPoint(22.681323996194592, 114.20004844665527)
        // private val CAMPUS_CENTER = GeoPoint(22.683085, 114.200014)
        private const val MAX_ZOOM_LVL = 20.0
        private const val MIN_ZOOM_LVL = 14.0
        private val WIFI_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

    }
    //  map components
    private lateinit var mapView: MapView
    // gps nav
//    private lateinit var locationProvider: LocationProvider
//    private lateinit var navigationController: NavigationController
    private lateinit var routeOverlay: RouteOverlay
    private lateinit var destinationMarker: DestinationMarker
    // location tracking
    private var selectedDestination: GeoPoint? = null
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var userMarker: Marker? = null
    // data management
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val locationMarkers = mutableListOf<Marker>()
    // wifi navi
    private var wifiUpdateInterval = 5000L // 5 seconds
    private var wifiUpdateHandler = Handler(Looper.getMainLooper())
    private val wifiUpdateRunnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun run() {
//            sharedViewModel.updateWifiPosition()
//            updatePositionUsingActiveSystem()
            wifiUpdateHandler.postDelayed(this, wifiUpdateInterval)
        }
    }
    //  wifi navi components
//    private lateinit var wifiNaviDatabaseHelper: WifiNaviDatabaseHelper
//    private lateinit var wifiNaviManager: WifiNaviManager
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handleWifiPermissionResult(permissions)
    }
    // added for new wifi navi approach
    private lateinit var advancedPositioningManager: AdvancedPositioningManager
    private var isAdvancedWifiActive = false

    private val gridMarkers = mutableListOf<Overlay>()
    private val buildingOutlines = mutableListOf<Overlay>()

    private val bounds :Bounds ?= null

    //  initialize map and osmdroid
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//      initialise osmdroid
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            load(
                context,
                context?.getSharedPreferences(
                    "osmprefs",
                    Context.MODE_PRIVATE
                )
            )
        }
        return inflater.inflate(R.layout.map_fragment, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        sharedViewModel.initWifiNavigation(requireContext())
        setupMapComponents(view)
        setupObservers()
        setupFabListeners(view)
        setupWifiNavigation()
    }
    // for new wifi navi approach
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updatePositionUsingActiveSystem() {
        val positioningResult = if (isAdvancedWifiActive) {
            advancedPositioningManager.estimatePositionProbabilistic()
        } else {
            val position = advancedPositioningManager.estimatePositionDeterministic()
            position?.let {
                OccupancyGridManager.PositioningResult(it, 0.5, 5.0) // Default values
            }
        }

        positioningResult?.let { result ->
            updateUserMarker(result.position)
//            sharedViewModel.updateUserMarkerPosition(result.position)
            sharedViewModel.updateWifiPositionWithConfidence(result.position, result.confidence)

            // Show confidence information
            val confidencePercent = (result.confidence * 100).toInt()
            showToast("Position updated (${confidencePercent}% confidence, ±${result.estimatedError.toInt()}m)")

            mapView.controller.animateTo(result.position)
        } ?: showToast("Could not determine position")


//        estimatedPosition?.let { position ->
//            updateUserMarker(position)
////            sharedViewModel.updateWifiPosition()
//            showToast("Position updated via ${if (isAdvancedWifiActive) "Advanced" else "Basic"} WiFi")
////            mapView.controller.animateTo(position)
//        } ?: showToast("Could not determine position")
    }

    private fun handleWifiPermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startWifiNavigation()
        } else { showToast("WiFi navigation requires all permissions to work") }
    }

    private fun startWifiNavigation() {
        if (!PermissionHelper.hasRequiredWifiPermissions(requireContext())) {
            showToast("Missing permissions for WiFi navigation")
            checkWifiPermissionsAndProceed()
            return
        }
        try {
            wifiUpdateHandler.post(wifiUpdateRunnable)
            showToast("New WiFi navigation started")
        } catch (e: SecurityException) {
            showToast("New WiFi navigation error: ${e.message}")
        }
    }
    private fun stopWifiNavigation() {
        wifiUpdateHandler.removeCallbacks(wifiUpdateRunnable)
        showToast("WiFi navigation stopped")
    }
    private fun setupWifiNavigation() {
//        wifiNaviDatabaseHelper = WifiNaviDatabaseHelper(requireContext())
//        wifiNaviDatabaseHelper.databaseCreate()
//        wifiNaviManager = WifiNaviManager(requireContext(), wifiNaviDatabaseHelper)
        // new
        advancedPositioningManager = AdvancedPositioningManager(requireContext())
    }

    private fun checkWifiPermissionsAndProceed() {
        when {
            PermissionHelper.hasRequiredWifiPermissions(requireContext()) -> {
                startWifiNavigation()
            }
            PermissionHelper.shouldShowWifiPermissionRationale(this) -> {
                showPermissionRationale()
            }
            else -> requestPermissionLauncher.launch(WIFI_PERMISSIONS)
        }
    }
    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permission Needed")
            .setMessage("WiFi navigation requires location and WiFi permissions to function properly")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(WIFI_PERMISSIONS)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupObservers() {
        sharedViewModel.locations.observe(viewLifecycleOwner) { locations ->
            Log.d("MapFragment", "Locations received: ${locations.size}")
            addLocationMarker(locations)
        }
        sharedViewModel.userPos.observe(viewLifecycleOwner) { position ->
            position?.let {
                updateUserMarker(it)
            }
        }
        // Add WiFi position observer
        sharedViewModel.wifiPosition.observe(viewLifecycleOwner) { position ->
            position?.let {
                updateUserMarker(it)
                if (selectedDestination != null) {
                    val route = listOf(it, selectedDestination!!)
                    routeOverlay.drawRoute(route)
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.R  )
    private fun setupFabListeners(view: View) {
//      to draw route
        view.findViewById<FloatingActionButton>(R.id.fabDrawRoute).setOnClickListener {
            selectedDestination?.let { dest ->

                val startPoint = sharedViewModel.wifiPosition.value
                    ?: sharedViewModel.userPos.value
                    ?: CAMPUS_CENTER

                val route = listOf(startPoint, dest)
                routeOverlay.drawRoute(route)
//              adjust view
                val boundingBox = BoundingBox.fromGeoPoints(listOf(startPoint, dest) )
                mapView.controller.apply {
                    zoomToSpan(boundingBox.latitudeSpan, boundingBox.longitudeSpan)
                    setCenter(boundingBox.centerWithDateLine)
                }
            } ?: run { showToast("Please select a destination first") }

        }
//      to clear route
        view.findViewById<FloatingActionButton>(R.id.fabClear).setOnClickListener {
            destinationMarker.removeMarker()
            selectedDestination = null
            routeOverlay.clear()
        }
        // new wifi navi
        view.findViewById<FloatingActionButton>(R.id.fabSwitchWifiMode).setOnClickListener {
            isAdvancedWifiActive = !isAdvancedWifiActive
            val mode = if (isAdvancedWifiActive) "Advanced" else "Basic"
            showToast("Switched to $mode WiFi positioning")
            // Immediately update position with the new mode
            updatePositionUsingActiveSystem()
        }
        view.findViewById<FloatingActionButton>(R.id.fabCollectFingerprint).setOnClickListener {
            collectAdvancedFingerprint()
        }
        // old wifi navi
        view.findViewById<FloatingActionButton>(R.id.fabStartWifiNav).setOnClickListener {
            if (wifiUpdateHandler.hasCallbacks(wifiUpdateRunnable)) {
                showToast("WiFi navigation already running")
            } else {
                checkWifiPermissionsAndProceed()
            }
        }
        view.findViewById<FloatingActionButton>(R.id.fabStopWifiNav).setOnClickListener {
            stopWifiNavigation()
        }
    }

    private fun collectAdvancedFingerprint() {
        val pointId = "FP_${System.currentTimeMillis()}"
        val buildingId = 1
        val location = sharedViewModel.userPos.value ?: return
        val success = advancedPositioningManager.collectFingerprint(pointId, buildingId, location)
        if (success) {
            showToast("Advanced fingerprint collected successfully!")
            Log.d("MapFragment", "Saved fingerprint at: $location")
        } else {
            showToast("Failed to collect advanced fingerprint")
        }
    }

    private fun setupMapComponents(view: View) {
//      init map components
        mapView = view.findViewById(R.id.mapView)
        destinationMarker = DestinationMarker(mapView)
        routeOverlay = RouteOverlay(mapView)

        val zoomLevel = 18.0
        val animationDuration = 1500L

        mapView.apply {
            setTileSource(TileSourceFactory.OpenTopo)
            overlays.clear()
            minZoomLevel = MIN_ZOOM_LVL
            maxZoomLevel = MAX_ZOOM_LVL
            setMultiTouchControls(true)
        }
        mapView.controller.apply {
            setZoom(zoomLevel)
            setCenter(CAMPUS_CENTER)
            animateTo(CAMPUS_CENTER, zoomLevel, animationDuration)
        }
        myLocationOverlay = MyLocationNewOverlay(
            object : GpsMyLocationProvider(requireContext()) {
                override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer?): Boolean {
                    // Don't start GPS provider TODO: Need to set it to false
                    return true
                }
            },
            mapView
        ).apply {
            setPersonIcon(
                getFixedSizeIcon(R.drawable.outline_person)?.toBitmap()
            )
            mapView.overlays.add(this)
        }


        setupMapClickListeners()
//      init user marker pos
        sharedViewModel.userPos.value?.let {
            updateUserMarker(it)
        } ?: run {
            val defaultPos = CAMPUS_CENTER
            updateUserMarker(defaultPos)
        }
    }

    private fun setupMapClickListeners() {
        mapView.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                if (e == null || mapView == null) return false

                // marker tap
                val tappedMarker = findMarkerAtPos(e.x, e.y, mapView)
                tappedMarker?.let { marker ->
                    val location = marker.relatedObject as? EachLocation
                    location?.let {
                        marker.showInfoWindow()
                        selectedDestination = marker.position
                        destinationMarker.updatePosition(marker.position)
                        return true
                    }
                }

                // map tap
                val geoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as? GeoPoint
                geoPoint?.let { point ->
                    selectedDestination = point
                    destinationMarker.updatePosition(point)
                    mapView.controller.animateTo(point)
                }
                return true
            }
        })
    }

    private fun findMarkerAtPos(x: Float, y: Float, mapView: MapView): Marker? {
        return mapView.overlays
            .filterIsInstance<Marker>()
            .firstOrNull { marker ->
                if (marker == userMarker) return@firstOrNull false

                val markerPos = mapView.projection.toPixels(marker.position, null)
                val distance = sqrt(
                    (x - markerPos.x).pow(2) + (y - markerPos.y).pow(2)
                )
                distance < 50
            }
    }
    private fun updateUserMarker(position: GeoPoint) {
        clearUserMarker()
//      add new marker
        userMarker = Marker(mapView).apply {
            this.position = position
            title = "Me"
            icon = getFixedSizeIcon(R.drawable.outline_person)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(userMarker)
        mapView.controller.animateTo(position)
        mapView.invalidate()
    }
    private fun clearUserMarker() {
        userMarker?.let { mapView.overlays.remove(it) }
    }
    private fun addLocationMarker(locations: List<EachLocation>) {
        clearLocationMarkers()
//      create marker for each location from db
        locations.forEach { location ->
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            val marker = Marker(mapView).apply {
                position = geoPoint
                title = location.name
                snippet = "${location.type}\n${location.openHours}"
                icon = getIconForLocationType(location.type)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                relatedObject = location
            }
            locationMarkers.add(marker)
        }
//      add markers
        locationMarkers.forEach { marker ->
            if (!mapView.overlays.contains(marker)) {
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }
    private fun clearLocationMarkers() {
        locationMarkers.forEach { marker ->
            mapView.overlays.remove(marker)
        }
        locationMarkers.clear()
        mapView.invalidate()
    }

    private fun getFixedSizeIcon(drawableResId: Int): Drawable? {
        val markerWidth = 48
        val markerHeight = 48

        return try {
            val originalDrawable = ContextCompat.getDrawable(
                requireContext(),
                drawableResId
            ) ?: return null

            // Handle different drawable types
            val originalBitmap = when (originalDrawable) {
                is BitmapDrawable -> originalDrawable.bitmap
                else -> {
                    // Convert vector drawable to bitmap if needed
                    val bitmap = createBitmap(
                        originalDrawable.intrinsicWidth,
                        originalDrawable.intrinsicHeight
                    )
                    val canvas = Canvas(bitmap)
                    originalDrawable.setBounds(0, 0, canvas.width, canvas.height)
                    originalDrawable.draw(canvas)
                    bitmap
                }
            }
            // Use fully qualified Bitmap.createScaledBitmap()
            val scaledBitmap = originalBitmap.scale(markerWidth, markerHeight)
            scaledBitmap.toDrawable(resources)
        } catch (e: Exception) {
            Log.e("MapFragment", "Error scaling bitmap: ${e.message}")
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.outline_dest_marker
            ) // Fallback
        }
    }
    private fun getIconForLocationType(type: String?): Drawable? {
        return when (type?.lowercase(Locale.ROOT)) {
            "study" -> getFixedSizeIcon(R.drawable.outline_study_marker)
            "restaurant" -> getFixedSizeIcon(R.drawable.outline_restaurant_marker)
            "office" -> getFixedSizeIcon(R.drawable.outline_office_marker)
            "library" -> getFixedSizeIcon(R.drawable.outline_library_marker)
            else -> {
                getFixedSizeIcon(R.drawable.outline_dest_marker)
            }
        }
    }

    private fun showToast(message: String) {

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > TOAST_COOLDOWN) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            lastToastTime = currentTime
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay.enableMyLocation()
    }
    override fun onPause() {
        super.onPause()
//      save pos
        myLocationOverlay.myLocation?.let {
            sharedViewModel.updateUserMarkerPosition(it)
        }
//      clean
        mapView.onPause()
        myLocationOverlay.disableMyLocation()
    }

    fun drawBuildingGrid(buildingPolygon: List<GeoPoint>) {
        // Clear existing grid markers
        clearGridMarkers()

        // Generate grid points for this specific building
        val gridPoints = bounds?.generateBuildingGrid(buildingPolygon, 10) // 10m spacing

        // Draw the grid points
        gridPoints?.forEachIndexed { index, point ->
            val marker = Marker(mapView).apply {
                position = point
                title = "Building Point ${index + 1}"
                snippet = "GeoPoint: ${point.latitude}, ${point.longitude})"
                icon = getFixedSizeIcon(R.drawable.outline_grid_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
            gridMarkers.add(marker) // Keep track of grid markers
        }

        // Also draw the building outline for reference
        drawBuildingOutline(buildingPolygon)

        mapView.invalidate()
    }
    fun drawBuildingOutline(polygon: List<GeoPoint>) {
        val polyline = Polyline(mapView).apply {
            setPoints(ArrayList(polygon))
            color = Color.BLUE
            width = 3.0f
        }
        mapView.overlays.add(polyline)
        buildingOutlines.add(polyline) // Keep track of outlines
    }
    fun clearGridMarkers() {
        mapView.overlays.removeAll(gridMarkers)
        mapView.overlays.removeAll(buildingOutlines)
        gridMarkers.clear()
        buildingOutlines.clear()
    }

    private fun drawGrid(spacingMeters: Int, gridSize: Int) {

        val gridPoints = generateGridPoints(
            CAMPUS_CENTER.latitude,
            CAMPUS_CENTER.longitude,
            spacingMeters,
            gridSize
        )

        gridPoints.forEachIndexed { index, point ->
            val marker = Marker(mapView).apply {
                position = point
                title = "Point ${index + 1}"
                snippet = "GeoPoint: ${point.latitude}, ${point.longitude})"
                icon = getFixedSizeIcon(R.drawable.outline_grid_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
//            Log.d("Grid", "Point ${index + 1}: (${point.latitude}, ${point.longitude})")
        }
        mapView.invalidate()
    }
    private fun generateGridPoints(centerLat: Double, centerLon: Double, spacingMeters: Int, gridSize: Int): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val halfRange = (gridSize - 1) / 2 * spacingMeters

        for (i in -halfRange..halfRange step spacingMeters) {
            for (j in -halfRange..halfRange step spacingMeters) {
                val latOffset = metersToLatitudeOffset(i.toDouble())
                val lonOffset = metersToLongitudeOffset(j.toDouble(), centerLat)

                val pointLat = centerLat + latOffset
                val pointLon = centerLon + lonOffset

                points.add(GeoPoint(pointLat, pointLon))
            }
        }

        return points
    }
    private fun metersToLatitudeOffset(meters: Double): Double {
        val METERS_PER_DEGREE_LAT = 111111.0
        return meters / METERS_PER_DEGREE_LAT
    }
    private fun metersToLongitudeOffset(meters: Double, latitude: Double): Double {
        val METERS_PER_DEGREE_LAT = 111111.0
        val cosLat = cos(Math.toRadians(latitude))
        return meters / (METERS_PER_DEGREE_LAT * cosLat)
    }



}
