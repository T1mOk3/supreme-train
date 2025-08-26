package io.github.mycampusmaptst1.wifi_navigation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.wifi.ScanResult
import android.util.Log
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

class WifiNaviDatabaseHelper (context: Context) : SQLiteOpenHelper(context, DB_NAME, null , DB_VERSION) {

    companion object {
        private const val DB_NAME = "MyDatabase15.db"
        private const val DB_VERSION = 1
        const val TABLE_NAME = "allWifiFingerprintsTable"
        const val COLUMN_BUILDING_ID = "building_id"
        const val COLUMN_BSSID = "bssid"
        const val COLUMN_SSID = "ssid"
        const val COLUMN_SIGNAL_VALUE = "signal_value"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
    }

    private val dbPath: String = context.getDatabasePath(WifiNaviDatabaseHelper.Companion.DB_NAME).path
    private val myContext: Context = context.applicationContext

    fun databaseCreate() {
        val file = File(dbPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            try {
                myContext.assets.open(WifiNaviDatabaseHelper.Companion.DB_NAME).use { input ->
                    FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                        output.flush()
                        Log.d("WifiNaviDatabaseHelper", "Database copied to $dbPath")
                    }
                }
            } catch (ex: IOException) {
                Log.d("WifiNaviDatabaseHelper", ex.message ?: "Unknown error!")
                throw RuntimeException("Database copy failed")
            }
        }
    }

    fun insertOrUpdate(fingerprint: EachFingerprint) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_BUILDING_ID, fingerprint.buildingId)
            put(COLUMN_BSSID, fingerprint.bssid)
            put(COLUMN_SSID, fingerprint.ssid)
            put(COLUMN_SIGNAL_VALUE, fingerprint.signalValue)
            put(COLUMN_LATITUDE, fingerprint.latitude)
            put(COLUMN_LONGITUDE, fingerprint.longitude)
        }
        db.replace(TABLE_NAME, null, values)
        db.close()
    }

    fun getAllFingerprints(): List<EachFingerprint> {
        Log.d("WifiNav", "Loading all fingerprints from database")
        val fingerprints = mutableListOf<EachFingerprint>()
        readableDatabase.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fp = EachFingerprint(
                    buildingId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_BUILDING_ID)),
                    bssid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BSSID)),
                    ssid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SSID)),
                    signalValue = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SIGNAL_VALUE)),
                    latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)),
                    longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                )
                Log.d("WifiNav", "Loaded fingerprint: ${fp.bssid} (${fp.ssid})")
                fingerprints.add(fp)
            }
        }
        Log.d("WifiNav", "Total fingerprints loaded: ${fingerprints.size}")
        return fingerprints
    }

    fun findClosestFingerprint(
        scanResults: List<ScanResult>,
        limit: Int = 5
    ) : List<EachFingerprint> {
        val currentScan = scanResults.associateBy { it.BSSID }

        return getAllFingerprints()
            .filter { currentScan.containsKey(it.bssid) }
            .map { fingerprint ->
                val signalDifference = abs(currentScan[fingerprint.bssid]!!.level - fingerprint.signalValue)
//                fingerprint to signalDifference
                Pair(fingerprint, signalDifference)
            }
            .sortedBy { (_, diff) -> diff }
            .take(limit)
            .map { (fingerprint, _) -> fingerprint }
            .also { results ->
                Log.d("WifiNav", "Found ${results.size} matching fingerprints for ${scanResults.size} scan results")
                results.forEach { fp ->
                    Log.d("WifiNav", "Matched fingerprint: ${fp.bssid} (${fp.ssid}) at (${fp.latitude},${fp.longitude})")
                }
//                scanResults.forEach { scan ->
//                    Log.d("WifiNav", "Scanned network: ${scan.BSSID} (${scan.SSID})")
//                }
            }
    }

    fun trilateratePosition(scanResult: List<ScanResult>) : GeoPoint? {
        Log.d("WifiNav", "Started solving trilateration problem")
        if (scanResult.size < 3) return null
        val fingerprints = findClosestFingerprint(scanResult, 3)
        if (fingerprints.size < 3) {
            Log.w("WifiNav", "Not enough fingerprints for trilateration (found ${fingerprints.size})")
            return null
        }
        // map for quick lookup of current RSSI by BSSID
        val currentScanMap = scanResult.associateBy { it.BSSID }
        val (p1, p2, p3) = fingerprints
        //
        val currRssi1 = currentScanMap[p1.bssid]!!.level
        val currRssi2 = currentScanMap[p2.bssid]!!.level
        val currRssi3 = currentScanMap[p3.bssid]!!.level

        Log.d("WifiNav", "Trilaterating with points:")
        Log.d("WifiNav", "P1: (${p1.latitude}, ${p1.longitude}), RSSI: $currRssi1 dBm")
        Log.d("WifiNav", "P2: (${p2.latitude}, ${p2.longitude}), RSSI: $currRssi2 dBm")
        Log.d("WifiNav", "P3: (${p3.latitude}, ${p3.longitude}), RSSI: $currRssi3 dBm")

        val weight1 = (currRssi1 + 100).toDouble().coerceAtLeast(0.1)
        val weight2 = (currRssi2 + 100).toDouble().coerceAtLeast(0.1)
        val weight3 = (currRssi3 + 100).toDouble().coerceAtLeast(0.1)

        val totalWeight = weight1 + weight2 + weight3

        val avgLat = (p1.latitude * weight1 + p2.latitude * weight2 + p3.latitude * weight3) / totalWeight
        val avgLon = (p1.longitude * weight1 + p2.longitude * weight2 + p3.longitude * weight3) / totalWeight

        Log.d("WifiNav", "Weighted Avg GeoPoint: ($avgLat, $avgLon)")
        return GeoPoint(avgLat, avgLon)
    }
    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
}