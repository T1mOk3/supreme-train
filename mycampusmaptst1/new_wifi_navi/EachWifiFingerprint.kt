package io.github.mycampusmaptst1.new_wifi_navi

import android.util.Log
import org.json.JSONObject
import kotlin.math.sqrt

data class EachWifiFingerprint(
    val pointId: String, // unique identifier for this fingerprint point
    val buildingId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(), // time when fingerprint was recorded
    val accessPoints: Map<String, Int> //  map of BSSID to the average RSSI for point.
) {

    // This is the core similarity metric for the k-Nearest Neighbors (k-NN) algorithm.
    fun calculateEuclideanDistance(liveScanMap: Map<String, Int>, defaultRssi: Int = -100): Double {
        var sumOfSquaredDifferences = 0.0
        val allBssid = (accessPoints.keys + liveScanMap.keys)

        for (bssid in allBssid) {
            val storedRssi = accessPoints[bssid] ?: defaultRssi
            val liveRssi = liveScanMap[bssid] ?: defaultRssi
            val difference = (storedRssi - liveRssi).toDouble()
            sumOfSquaredDifferences += difference * difference
        }

        return sqrt(sumOfSquaredDifferences)
    }

    fun accessPointsToJsonString(): String {
        val jsonObject = JSONObject()
        accessPoints.forEach { (bssid, rssi) ->
            jsonObject.put(bssid, rssi)
        }
        return jsonObject.toString()
    }

     companion object {
         fun parseAccessPointsFromJson(jsonString: String): Map<String, Int> {
             val apMap = mutableMapOf<String, Int>()
             try {
                 val jsonObject = JSONObject(jsonString)
                 val keys = jsonObject.keys()
                 while (keys.hasNext()) {
                     val key = keys.next()
                     if (!jsonObject.isNull(key)) {
                         apMap[key] = jsonObject.getInt(key)
                     } else {
                         // Handle null values - either skip them or use a default
                         apMap[key] = -100  // Uncomment to use default instead of skipping
                         Log.w("Parse", "Skipping null RSSI value for MAC: $key")
                     }
                 }
             } catch (e: Exception) {
                 Log.e("Prase:", "Did not prase Ap form Json", e)
             }
             return apMap
         }
     }
}