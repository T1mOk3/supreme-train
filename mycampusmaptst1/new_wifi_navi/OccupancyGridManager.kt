package io.github.mycampusmaptst1.new_wifi_navi

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.sqrt

class OccupancyGridManager(context: Context) {

    private val fingerprintDatabaseHelper = FingerprintDatabaseHelper(context)
    private var gridCells: List<GridCell> = emptyList()
    private val rssThreshold = 5.0 // 5 dB threshold

    data class GridCell(
        val cellId: String,
        val easting: Double,
        val northing: Double,
        var probability: Double = 0.0
    )

    data class PositioningResult(
        val position: GeoPoint,
        val confidence: Double,
        val estimatedError: Double
    )

    // Initialize grid based on fingerprint locations
    fun initializeGridFromFingerprints(): Boolean {
        val fingerprints = fingerprintDatabaseHelper.getAllFingerprints()
        if (fingerprints.isEmpty()) {
            Log.w("OccupancyGrid", "No fingerprints available for grid initialization")
            return false
        }

        // Create grid cells around fingerprint points
        gridCells = createGridCells(fingerprints)
        resetProbabilities()
        return true
    }


    private fun createGridCells(fingerprints: List<EachWifiFingerprint>): List<GridCell> {
        val cells = mutableListOf<GridCell>()
        val resolution = 2.0

        fingerprints.forEach { fingerprint ->
            // From geo to metric
            val centerEasting = fingerprint.longitude * 111320.0 // lat radius
            val centerNorthing = fingerprint.latitude * 111111.0 // lon radius
            // Create a 3x3 grid around each fingerprint point
            for (i in -1..1) {
                for (j in -1..1) {
                    val easting = centerEasting + (i * resolution)
                    val northing = centerNorthing + (j * resolution)

                    cells.add(
                        GridCell(
                            cellId = "cell_${fingerprint.pointId}_${i}_${j}",
                            easting = easting,
                            northing = northing,
                            probability = 0.0
                        )
                    )
                }
            }
        }
        cells.forEach { cell ->
            Log.d("GridManager", "cell ${+1}: ${cell.easting}, ${cell.northing}")
        }

        Log.d(
            "OccupancyGrid",
            "Created ${cells.size} grid cells around ${fingerprints.size} fingerprints"
        )
        return cells.distinctBy { "${it.easting}_${it.northing}" } // Remove duplicates
    }

    fun resetProbabilities() {
        val totalCells = gridCells.size
        if (totalCells > 0) {
            gridCells.forEach { cell ->
                cell.probability = 1.0 / totalCells
            }
        }
    }

    // Core probabilistic positioning from the paper
    fun estimatePositionProbabilistic(liveScanMap: Map<String, Int>): PositioningResult? {
        if (gridCells.isEmpty()) {
            Log.w("OccupancyGrid", "Grid not initialized")
            return null
        }
        resetProbabilities()
        val fingerprints = fingerprintDatabaseHelper.getAllFingerprints()
        // Update probabilities for each access point
        liveScanMap.forEach { (bssid, liveRss) ->
            updateProbabilitiesForAP(bssid, liveRss.toDouble(), fingerprints)
        }
        // Find most probable position
        return findMostProbablePosition()
    }

    private fun updateProbabilitiesForAP(
        bssid: String,
        liveRss: Double,
        fingerprints: List<EachWifiFingerprint>
    ) {
        gridCells.forEachIndexed { index, cell ->
            // Find closest fingerprint to this grid cell
            val closestFp = findClosestFingerprintToCell(cell, fingerprints)
            val fingerprintRss = closestFp?.accessPoints?.get(bssid)?.toDouble()
            // Calculate probability factor (0.4 or 0.6 as in paper)
            val factor = calculateProbabilityFactor(liveRss, fingerprintRss)
            // Update cell probability
            cell.probability *= factor
        }
        // Normalize probabilities
        normalizeProbabilities()
    }

    private fun calculateProbabilityFactor(liveRss: Double?, fingerprintRss: Double?): Double {
        if (liveRss == null || fingerprintRss == null) {
            return 0.4 // No match found - reduce probability
        }
        val rssDifference = abs(liveRss - fingerprintRss)
        return if (rssDifference <= rssThreshold) {
            0.6 // Good match - increase probability
        } else {
            0.4 // Poor match - reduce probability
        }
    }

    private fun normalizeProbabilities() {
        val totalProbability = gridCells.sumOf { it.probability }
        if (totalProbability > 0) {
            gridCells.forEach { cell ->
                cell.probability /= totalProbability
            }
        }
    }

    private fun findMostProbablePosition(): PositioningResult? {
        val maxCell = gridCells.maxByOrNull { it.probability } ?: return null
        // Convert metric coordinates back to geographic
        val latitude = maxCell.northing / 111111.0
        val longitude = maxCell.easting / 111320.0
        // Calculate confidence based on probability distribution
        val confidence = calculateConfidence()
        val estimatedError = calculateEstimatedError(confidence)
        //
        return PositioningResult(
            position = GeoPoint(latitude, longitude),
            confidence = confidence,
            estimatedError = estimatedError
        )
    }

    private fun calculateConfidence(): Double {
        val maxProb = gridCells.maxOf { it.probability }
        val avgProb = gridCells.map { it.probability }.average()
        return (maxProb - avgProb).coerceIn(0.0, 1.0)
    }

    private fun calculateEstimatedError(confidence: Double): Double {
        // Higher confidence = lower error estimate
        return 10.0 * (1.0 - confidence) // 0-10 meter error estimate
    }

    private fun findClosestFingerprintToCell(
        cell: GridCell,
        fingerprints: List<EachWifiFingerprint>
    ): EachWifiFingerprint? {
        return fingerprints.minByOrNull { fingerprint ->
            val cellLat = cell.northing / 111111.0
            val cellLon = cell.easting / 111320.0
            calculateDistance(
                GeoPoint(cellLat, cellLon),
                GeoPoint(fingerprint.latitude, fingerprint.longitude)
            )
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val latDiff = point1.latitude - point2.latitude
        val lonDiff = point1.longitude - point2.longitude
        return sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }
}