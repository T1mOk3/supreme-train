package io.github.mycampusmaptst1.utils

import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    private const val EARTH_RADIUS = 6371000.0

    fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLat = abs(lat2 - lat1)
        val dLon = abs(lon2 - lon1)


        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun calculateBearing(start: GeoPoint, end: GeoPoint): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

//        val dLat = lat2 - lat1
//        val dLon = lon2 - lon1
//
//        Haversine formula
//        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
//        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
//
//        return EARTH_RADIUS_KM * c
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360
        return bearing.toFloat()
    }
}