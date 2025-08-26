package io.github.mycampusmaptst1.utils

import org.osmdroid.util.GeoPoint
import kotlin.math.cos

data class Bounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111111.0
    }

    fun generateBuildingGrid(
        buildingPolygon: List<GeoPoint>,
        gridSizeMeters: Int
    ): List<GeoPoint> {
        val gridPoints = mutableListOf<GeoPoint>()
        val bounds = calculatePolygonBounds(buildingPolygon)

        val centerLat = (bounds.minLat + bounds.maxLat) / 2
        val latSpacing = metersToLatitudeOffset(gridSizeMeters.toDouble())
        val lonSpacing = metersToLongitudeOffset(gridSizeMeters.toDouble(), centerLat)

        var lat = bounds.minLat

        while (lat <= bounds.maxLat) {
            var lon = bounds.minLon
            while (lon <= bounds.maxLon) {
                var point = GeoPoint(lat, lon)
                if (isPointInPolygon(point, buildingPolygon)) {
                    gridPoints.add(point)
                }
                lon += lonSpacing
            }
            lat += latSpacing
        }

        return gridPoints
    }

    fun calculatePolygonBounds(polygon: List<GeoPoint>) : Bounds {
        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE

        for(point in polygon) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }
        return Bounds(minLat, maxLat, minLon, maxLon)
    }
    fun isPointInPolygon(point: GeoPoint, polygon: List<GeoPoint>) : Boolean {
        var inside = false
        val size = polygon.size

        if (size < 3) return false

        var j = size -1
        for (i in 0 until size) {
            val vi = polygon[i]
            val vj = polygon[j]

            if ((vi.longitude > point.longitude) != (vj.longitude > point.longitude) &&
                point.latitude < (vj.latitude - vi.latitude) *
                (point.longitude - vi.longitude) / (vj.longitude - vi.longitude) + vi.latitude) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    fun metersToLatitudeOffset(meters: Double): Double {
        return meters / METERS_PER_DEGREE_LAT
    }
    fun metersToLongitudeOffset(meters: Double, latitude: Double): Double {
        val cosLat = cos(Math.toRadians(latitude))
        return meters / (METERS_PER_DEGREE_LAT * cosLat)
    }
}
