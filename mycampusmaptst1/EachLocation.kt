package io.github.mycampusmaptst1

data class EachLocation(
    val buildingId: Int,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val openHours: String,
    val imagePath: String
)