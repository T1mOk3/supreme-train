package io.github.mycampusmaptst1.new_wifi_navi

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import org.osmdroid.util.GeoPoint


class AdvancedPositioningManager(context: Context) {

    private val fingerprintDatabaseHelper = FingerprintDatabaseHelper(context)
    private val occupancyGridManager = OccupancyGridManager(context)
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // OFFLINE SURVEY phase
    fun collectFingerprint(pointId: String, buildingId: Int, location: GeoPoint) : Boolean {
        return try {
            val scanResults = getLatestScanResults()
            if (scanResults.isEmpty()) {
                Log.d("AdvPositioning", "No Wi-Fi networks found for fingerprint.")
                return false
            }

            val apMap = mutableMapOf<String, MutableList<Int>>()
            repeat(3) { // Take 3 measurements as paper suggests
                val currentScan = getLatestScanResults()
                currentScan.forEach { result ->
                    if (result.level > -85) {
                        apMap.getOrPut(result.BSSID) { mutableListOf() }.add(result.level)
                    }
                }
                Thread.sleep(500) // Wait between measurements
            }
            // Calculate average RSSI for each AP
            val averagedApMap = apMap.mapValues { (_, values) ->
                values.average().toInt()
            }

            val fingerprint = EachWifiFingerprint(
                pointId = pointId,
                buildingId = buildingId,
                latitude = location.latitude,
                longitude = location.longitude,
                accessPoints = averagedApMap
            )
            val success = fingerprintDatabaseHelper.insertFingerprint(fingerprint)

            // Reinitialize grid when new fingerprints are added
            if (success) {
                occupancyGridManager.initializeGridFromFingerprints()
            }

            success
        } catch (e: Exception) {
            Log.e("AdvPositioning", "Error collecting fingerprint", e)
            false
        }
    }

    // ONLINE POSITIONING phase
    fun estimatePositionProbabilistic(): OccupancyGridManager.PositioningResult? {
        val liveScanResults = getLatestScanResults()
        val filteredResults = filterScanResults(liveScanResults)

        if (filteredResults.isEmpty()) {
            Log.w("AdvPositioning", "No reliable live scan results for estimation.")
            return null
        }

        val liveScanMap = filteredResults.associate { it.BSSID to it.level }

        // Initialize grid if not already done
        if (!occupancyGridManager.initializeGridFromFingerprints()) {
            Log.w("AdvPositioning", "Cannot initialize grid - no fingerprints available")
            return null
        }

        return occupancyGridManager.estimatePositionProbabilistic(liveScanMap)
    }

    fun estimatePositionDeterministic(): GeoPoint? {
        val liveScanResults = getLatestScanResults()
        val filteredResults = filterScanResults(liveScanResults)

        if (filteredResults.isEmpty()) {
            Log.w("AdvPositioning", "No live scan results for estimation.")
            return null
        }

        val liveScanMap = filteredResults.associate { it.BSSID to it.level }
        val allFingerprints = fingerprintDatabaseHelper.getAllFingerprints()

        if (allFingerprints.isEmpty()) {
            Log.d("AdvPositioning", "No fingerprints in database for estimation.")
            return null
        }

        // Enhanced deterministic approach with weighted averaging
        val topMatches = allFingerprints.map { fingerprint ->
            val distance = fingerprint.calculateEuclideanDistance(liveScanMap, -90) // Less aggressive default
            Pair(fingerprint, distance)
        }.sortedBy { it.second }.take(3) // Take top 3 matches

        if (topMatches.isEmpty()) return null

        // Weighted average of top matches
        val weights = topMatches.map { 1.0 / (it.second + 0.1) } // Avoid division by zero
        val totalWeight = weights.sum()

        var weightedLat = 0.0
        var weightedLon = 0.0

        topMatches.forEachIndexed { index, (fingerprint, _) ->
            val weight = weights[index] / totalWeight
            weightedLat += fingerprint.latitude * weight
            weightedLon += fingerprint.longitude * weight
        }

        return GeoPoint(weightedLat, weightedLon)
    }

    private fun filterScanResults(scanResults: List<ScanResult>): List<ScanResult> {
        return scanResults.filter {
            it.level > -85 && // Stronger than -85 dBm
                    it.BSSID != null && // Valid BSSID
                    !it.SSID.isNullOrEmpty() // Valid SSID
        }
    }

    private fun getLatestScanResults(): List<ScanResult> {
        return try {
            wifiManager.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("AdvPositioning", "Permission denied accessing Wi-Fi scan results", e)
            emptyList()
        }
    }

}