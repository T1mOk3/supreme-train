package io.github.mycampusmaptst1.utils

import org.osmdroid.util.GeoPoint

data class NavigationState(
    val currentLocation: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val routePoints: List<GeoPoint> = emptyList()
)
