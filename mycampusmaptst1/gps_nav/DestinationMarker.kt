package io.github.mycampusmaptst1.gps_nav

import androidx.core.content.ContextCompat
import io.github.mycampusmaptst1.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class DestinationMarker(private val mapView: MapView) {
    private var marker: Marker? = null

    fun updatePosition(point: GeoPoint) {
        if (marker == null) {
            marker = Marker(mapView).apply {
                position = point
                title = "Your destination"
                snippet = "${point.latitude}, ${point.longitude}"
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.outline_dest_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) // centralise
            }
            mapView.overlays.add(marker)
        }
        marker?.position = point
        mapView.invalidate()
    }

    fun removeMarker() {
        marker?.let {
            mapView.overlays.remove(it)
            mapView.invalidate()
        }
        marker = null
    }

}