package io.github.mycampusmaptst1.utils

import android.Manifest
import androidx.fragment.app.Fragment
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    private const val LOCATION_PERMISSION_REQUEST_CODE  = 1001
    const val WIFI_NAV_PERMISSION_REQUEST_CODE = 1002
    fun hasLocationPermissions(context: Context): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation && coarseLocation

    }
    fun requestLocationPermissions(fragment: Fragment) {
        fragment.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }


    fun hasRequiredWifiPermissions(context: Context) : Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val accessWifiState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val changeWifiState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CHANGE_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation && coarseLocation && accessWifiState && changeWifiState
    }

    fun shouldShowWifiPermissionRationale(fragment: Fragment): Boolean {
        val fineLocation = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        val accessWifiState = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_WIFI_STATE)
        val changeWifiState = fragment.shouldShowRequestPermissionRationale(Manifest.permission.CHANGE_WIFI_STATE)
        return fineLocation || accessWifiState || changeWifiState

    }

}