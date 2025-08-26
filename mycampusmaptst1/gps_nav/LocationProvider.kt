package io.github.mycampusmaptst1.gps_nav

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import io.github.mycampusmaptst1.utils.PermissionHelper

class LocationProvider(
    private val context: Context,
    private val onLocationChange: ((Location) -> Unit)? = null
) : LocationListener {

    companion object {
        private const val MIN_TIME_BTW_UPDATES = 1000L
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 5f
    }

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    ])
    fun startLocationUpdates() {

        if (!PermissionHelper.hasLocationPermissions(context)) {
            PermissionHelper.requestLocationPermissions(context as Fragment)
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BTW_UPDATES,
                MIN_DISTANCE_CHANGE_FOR_UPDATES,
                this
            )


        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun stopLocationUpdate() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        onLocationChange?.invoke(location)
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

}