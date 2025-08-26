package io.github.mycampusmaptst1.utils

import android.view.WindowInsetsAnimation
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLat = abs(lat2 - lat1)
        val dLon = abs(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun createSquareBounds(center: GeoPoint, sideLengthMeters: Double) : BoundingBox {
        val earthRadius = 6371000.0 // meters
        val latDelta = sideLengthMeters / earthRadius * (180.0 / Math.PI)
        val lonDelta = latDelta / cos(Math.toRadians(center.latitude))

        return BoundingBox(
            center.latitude + latDelta/2,
            center.longitude + lonDelta/2,
            center.latitude - latDelta/2,
            center.longitude - lonDelta/2
        )
    }
}