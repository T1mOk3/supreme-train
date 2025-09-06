package io.github.mycampusmaptst1.utils

sealed class CampusItem {
    data class EachLocation(
        val location: io.github.mycampusmaptst1.EachLocation
    ) : CampusItem()
    data class EachWifiFingerprint(
        val classroom: io.github.mycampusmaptst1.new_wifi_navi.EachWifiFingerprint,
        val buildingName: String
    ) : CampusItem()
}