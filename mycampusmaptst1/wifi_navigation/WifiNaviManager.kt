package io.github.mycampusmaptst1.wifi_navigation


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.mycampusmaptst1.utils.PermissionHelper
import org.osmdroid.util.GeoPoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WifiNaviManager(
    private val context: Context,
    private val wifiNaviDatabaseHelper: WifiNaviDatabaseHelper
) {
    private val wifiManager by lazy {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    // Track scan state to avoid multiple simultaneous scans
    private var isScanning = false
    private var wifiScanReceiver: BroadcastReceiver? = null
    private var scanLatch: CountDownLatch? = null
    private var latestScanResults: List<ScanResult> = emptyList()

    fun scanNetwork(): List<ScanResult> {
        if (!PermissionHelper.hasRequiredWifiPermissions(context)) {
            Log.e("WifiNav", "Missing required permissions for WiFi scan")
            throw SecurityException("Missing required WiFi or location permissions")
        }

        // Don't start a new scan if one is already in progress
        if (isScanning) {
            Log.w("WifiNav", "Scan already in progress, skipping new request")
            return emptyList()
        }

        isScanning = true
        latestScanResults = emptyList()

        return try {
            // Enable WiFi if not enabled
            if (!wifiManager.isWifiEnabled) {
                Log.d("WifiNav", "Enabling WiFi...")
                wifiManager.isWifiEnabled = true
                // Wait for WiFi to actually enable
                Thread.sleep(2000)
                if (!wifiManager.isWifiEnabled) {
                    Log.w("WifiNav", "WiFi failed to enable")
                    isScanning = false
                    return emptyList()
                }
            }

            val latch = CountDownLatch(1)

            wifiScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success) {
                        scanSuccess()
                    } else {
                        scanFailure()
                    }
                }
            }

            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            context.applicationContext.registerReceiver(wifiScanReceiver, intentFilter)

//            wifiManager.setWifiEnabled(true)
            Log.d("WifiNav", "Starting WiFi scan...")
            val success = wifiManager.startScan()

            if (!success) {
                Log.w("WifiNav", "startScan() returned false")
                // Handle scan failure immediately
                scanFailure()
            } else Log.d("WifiNav", "Scan started successfully")

            // Wait for results with timeout (10 seconds)
            val resultsReceived = latch.await(10, TimeUnit.SECONDS)

            if (!resultsReceived) {
                Log.w("WifiNav", "Scan timeout, trying to get any available results")
                try {
                    val availableResults = wifiManager.scanResults
                    if (!availableResults.isNullOrEmpty()) {
                        latestScanResults = availableResults.filter {
                            it.BSSID != null && it.BSSID.isNotBlank()
                        }
                        Log.d("WifiNav", "Using results despite timeout: ${latestScanResults.size}")
                    }
                } catch (e: SecurityException) {
                    Log.e("WifiNav", "Security exception accessing timeout results", e)
                }
            }

            wifiScanReceiver?.let {
                try {
                    context.applicationContext.unregisterReceiver(it)
                } catch (e: IllegalArgumentException) {
                    Log.w("WifiNav", "Receiver already unregistered", e)
                }
            }

            wifiScanReceiver = null
            scanLatch = null

            Log.d("WifiNav", "Final scan results: ${latestScanResults.size}")
            latestScanResults.forEachIndexed { index, result ->
                Log.d("WifiNav", "Network $index: ${result.SSID} (${result.BSSID}), strength: ${result.level}dBm")
            }

            latestScanResults

        } catch (e: SecurityException) {
            Log.e("WifiNav", "Permission denied for WiFi scan", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("WifiNav", "Error during WiFi scan", e)
            emptyList()
        } finally {
            isScanning = false
        }
    }

    private fun scanSuccess() {
        Log.d("WifiNav", "Scan succeeded, getting results...")
        try {
            val results = wifiManager.scanResults
            if (results != null) {
                latestScanResults = results.filter {
                    it.BSSID != null && it.BSSID.isNotBlank()
                }
                Log.d("WifiNav", "Scan successful, found ${latestScanResults.size} networks")
            }
        } catch (e: SecurityException) {
            Log.e("WifiNav", "Security exception when accessing scan results", e)
        } finally {
            scanLatch?.countDown()
        }
    }
    private fun scanFailure() {
        Log.w("WifiNav", "Scan failed, trying to use older results...")
        try {
            val results = wifiManager.scanResults
            if (results != null && results.isNotEmpty()) {
                latestScanResults = results.filter {
                    it.BSSID != null && it.BSSID.isNotBlank()
                }
                Log.d("WifiNav", "Using older scan results: ${latestScanResults.size}")
            } else {
                Log.w("WifiNav", "No older results available")
            }
        } catch (e: SecurityException) {
            Log.e("WifiNav", "Security exception when accessing older results", e)
        } finally {
            scanLatch?.countDown()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun estimatePosition() : GeoPoint? {
        // multiple scans
//        val scanResults = scanNetwork()
        val scanResults = List(3) { scanNetwork() }.flatten()
        if (scanResults.isEmpty()) {
            Log.w("WifiNav", "No scan results available")
            return null
        }

        return wifiNaviDatabaseHelper.trilateratePosition(scanResults) ?: run {
            val closest = wifiNaviDatabaseHelper.findClosestFingerprint(scanResults, 5)

            if (closest.isNotEmpty()) {
                val avgLat = closest.map { it.latitude }.average()
                val avgLon = closest.map { it.longitude }.average()
                GeoPoint(avgLat, avgLon)
            } else null
        }
    }




    @RequiresApi(Build.VERSION_CODES.R)
    fun collectFingerprint(buildingId: Int, location: GeoPoint): Boolean {
        return try {
            scanNetwork().forEach { scanResult ->
                wifiNaviDatabaseHelper.insertOrUpdate(EachFingerprint(
                    buildingId,
                    scanResult.BSSID,
                    scanResult.SSID,
                    scanResult.level,
                    location.latitude,
                    location.longitude
                ))
            }
            true
        } catch (e: Exception) {
            Log.e("WifiNav", "Error collecting fingerprint", e)
            false
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return PermissionHelper.hasRequiredWifiPermissions(context)
    }

}