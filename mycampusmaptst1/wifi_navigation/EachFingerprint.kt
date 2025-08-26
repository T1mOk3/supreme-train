package io.github.mycampusmaptst1.wifi_navigation

data class EachFingerprint (
    val buildingId: Int,
    val bssid: String,
    val ssid: String,
    val signalValue: Int,
    val latitude: Double,
    val longitude: Double
)