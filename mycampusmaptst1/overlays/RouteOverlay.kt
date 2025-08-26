package io.github.mycampusmaptst1.overlays

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class RouteOverlay(private val mapView: MapView) {
    private var polyline: Polyline? = null

    fun drawRoute(points: List<GeoPoint>) {
        clear() // clear prev route
        polyline = Polyline().apply {
            setPoints(points)
            color = 0xFF4285F4.toInt() // #4285F4
            width = 8f
        }
        // adding new route
        mapView.overlays.add(polyline)
        mapView.invalidate()
    }

    fun clear() {
        polyline?.let {
            mapView.overlays.remove(it) // remove from map
            mapView.invalidate() // update map
        }
        polyline = null // drop link
    }

}