package io.github.mycampusmaptst1.gps_nav

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import io.github.mycampusmaptst1.overlays.RouteOverlay
import io.github.mycampusmaptst1.utils.GeoUtils
import io.github.mycampusmaptst1.utils.NavigationState
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class NavigationController(
    private val context: Context,
    private val mapView: MapView,
    private val locationProvider: LocationProvider,
    private val routeOverlay: RouteOverlay
) {
    private var state = NavigationState()
    private var isNavigating = false
    var onDestinationReached: (() -> Unit)? = null
    companion object {
        private const val ARRIVAL_THRESHOLD_METERS = 15.0
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startNavigation(destination: GeoPoint) {
        state = state.copy(destination = destination)
        isNavigating = true
        // current point
        state.currentLocation?.let { current ->
            routeOverlay.drawRoute(listOf(current, destination))
        }

        locationProvider.startLocationUpdates()
    }

    fun stopNavigation() {
        isNavigating = false
        locationProvider.stopLocationUpdate()
        clearRoute()
    }

    fun updateLocation(location: Location) {
        if (!isNavigating) return

        val current = GeoPoint(location.latitude, location.longitude)
        state = state.copy(currentLocation = current)

        updateRoute()
        checkArrival()
    }

    private fun updateRoute() {
        state.currentLocation?.let { current ->
            state.destination?.let { dest ->
                val routePoints = listOf(current, dest)
                state = state.copy(routePoints = routePoints)
                routeOverlay.drawRoute(routePoints)
            }
        }
    }

    private fun checkArrival() {
        state.currentLocation?.let { current ->
            state.destination?.let { dest ->
                if (GeoUtils.calculateDistance(current, dest) < ARRIVAL_THRESHOLD_METERS) {
                    onDestinationReached?.invoke()
                    stopNavigation()
                }
            }
        }
    }

    private fun clearRoute() {
        routeOverlay.clear()
        state = NavigationState()
    }



}