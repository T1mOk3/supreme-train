package io.github.mycampusmaptst1.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mycampusmaptst1.DatabaseHelper
import io.github.mycampusmaptst1.EachLocation
import io.github.mycampusmaptst1.utils.PermissionHelper
import io.github.mycampusmaptst1.wifi_navigation.WifiNaviDatabaseHelper
import io.github.mycampusmaptst1.wifi_navigation.WifiNaviManager
//import io.github.mycampusmaptst1.wifi_navigation.WifiNaviDatabaseHelper
//import io.github.mycampusmaptst1.wifi_navigation.WifiNaviManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class SharedViewModel: ViewModel() {

    private val _locations = MutableLiveData<List<EachLocation>>()
    val locations: LiveData<List<EachLocation>> = _locations

    private val _userPos = MutableLiveData<GeoPoint?>()
    val userPos : LiveData<GeoPoint?> = _userPos

    private val _selectedLocation = MutableLiveData<EachLocation>()
    val selectedLocation: LiveData<EachLocation> = _selectedLocation

    private var _databaseHelper: DatabaseHelper? = null

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // add wifi Navi
    private val _wifiPosition = MutableLiveData<GeoPoint?>()
    val wifiPosition: LiveData<GeoPoint?> = _wifiPosition

    // In SharedViewModel.kt
    private val _wifiPositionWithConfidence = MutableLiveData<Pair<GeoPoint, Double>?>()
    val wifiPositionWithConfidence: LiveData<Pair<GeoPoint, Double>?> = _wifiPositionWithConfidence

    fun updateWifiPositionWithConfidence(position: GeoPoint, confidence: Double) {
        _wifiPositionWithConfidence.value = Pair(position, confidence)
    }


    private lateinit var wifiNaviManager: WifiNaviManager

    fun initWifiNavigation(context: Context) {
        val wifiDbHelper = WifiNaviDatabaseHelper(context.applicationContext)
        wifiDbHelper.databaseCreate()
        wifiNaviManager = WifiNaviManager(context, wifiDbHelper)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun updateWifiPosition() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (wifiNaviManager.hasRequiredPermissions()) {
                    _wifiPosition.postValue(wifiNaviManager.estimatePosition())
                } else {
                    _error.postValue("WiFi navigation requires location and WiFi permissions")
                }
            } catch (e: Exception) {
                _error.postValue("WiFi navigation error: ${e.localizedMessage}")
            }
        }
    }

    fun initDatabaseHelper(context: Context) {
        if (_databaseHelper == null) {
            _databaseHelper = DatabaseHelper(context.applicationContext)
            _databaseHelper?.databaseCreate()
            fetchLocationsFromDB()
        }
    }

    @SuppressLint("Range")
    fun fetchLocationsFromDB(filter: String? = null) {
        _isLoading.postValue(true)
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
//          open DB
                val db = _databaseHelper?.readableDatabase
                    ?: throw IllegalStateException("Database not initialized")
                val locations = mutableListOf<EachLocation>()
//          base query
                val sqlQuery = buildString {
                    append("SELECT * FROM ${DatabaseHelper.TABLE_NAME}")
                    filter?.let { append(" WHERE ${DatabaseHelper.COLUMN_NAME} LIKE ?") }
                    append(" ORDER BY ${DatabaseHelper.COLUMN_NAME} ASC")
                }
//          execute query
                db.rawQuery(sqlQuery, filter?.let { arrayOf(it) }).use { cursor ->
                    if (cursor.moveToFirst()) {
                        do {
                            locations.add(EachLocation(
                                buildingId = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COLUMN_BUILDING_ID)),
                                name = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_NAME)),
                                type = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_TYPE)),
                                latitude = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_LATITUDE)),
                                longitude = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_LONGITUDE)),
                                openHours = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_OPEN_HOURS)),
                                imagePath = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_IMAGE_PATH)))
                            )
                        } while (cursor.moveToNext())
                    }
                }
                _locations.postValue(locations)
                _error.postValue(null)
            } catch (e: Exception) {
                Log.e("LocationsFragment", "Error loading locations", e)
                _error.postValue("Failed to load locations: ${e.message}")
                _locations.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun setLocations(locations: List<EachLocation>) {
        _locations.value = locations
    }
    fun updateUserMarkerPosition(position: GeoPoint?) {
        _userPos.value = position
    }
    fun setSelectedLocations(location: EachLocation) {
        _selectedLocation.value = location
    }
    override fun onCleared() {
        _databaseHelper?.close()
        super.onCleared()
    }

}