package io.github.mycampusmaptst1.new_wifi_navi

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class FingerprintDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION)
{
    companion object {
        private const val  DB_NAME = "MyDatabase15.db"
        private const val DB_VERSION = 1

        const val TABLE_FINGERPRINTS = "fingerprints"
        const val COLUMN_POINT_ID = "point_id"
        const val COLUMN_BUILDING_ID = "building_id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_ACCESS_POINTS = "access_points"
    }
    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = """
            CREATE TABLE $TABLE_FINGERPRINTS (
                $COLUMN_POINT_ID TEXT PRIMARY KEY,
                $COLUMN_BUILDING_ID INTEGER,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_ACCESS_POINTS TEXT NOT NULL
            )
        """.trimIndent()
        db?.execSQL(createTableQuery)
        Log.d("FingerprintDB", "Database table created")
    }
    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int
    ) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_FINGERPRINTS")
        onCreate(db)
    }

    fun insertFingerprint(fingerprint: EachWifiFingerprint): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_POINT_ID, fingerprint.pointId)
                put(COLUMN_BUILDING_ID, fingerprint.buildingId)
                put(COLUMN_LATITUDE, fingerprint.latitude)
                put(COLUMN_LONGITUDE, fingerprint.longitude)
                put(COLUMN_TIMESTAMP, fingerprint.timestamp)
                // Convert the AP map to a JSON string for storage
                put(COLUMN_ACCESS_POINTS, fingerprint.accessPointsToJsonString())
            }
            val result = db.insertWithOnConflict(
                TABLE_FINGERPRINTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.close()
            result != 1L // true if insert was successful
        } catch (e: Exception) {
            Log.e("FingerprintDB", "Error inserting fingerprint", e)
            false
        }
    }

    fun getAllFingerprints(): List<EachWifiFingerprint> {
        val fingerprints = mutableListOf<EachWifiFingerprint>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FINGERPRINTS,
            null,
            null,
            null,
            null,
            null,
            null
        )

        cursor.use {
            val pointIdIndex = it.getColumnIndex(COLUMN_POINT_ID)
            val buildingIdIndex = it.getColumnIndex(COLUMN_BUILDING_ID)
            val latIndex = it.getColumnIndex(COLUMN_LATITUDE)
            val lonIndex = it.getColumnIndex(COLUMN_LONGITUDE)
            val timeIndex = it.getColumnIndex(COLUMN_TIMESTAMP)
            val apIndex = it.getColumnIndex(COLUMN_ACCESS_POINTS)

            while (cursor.moveToNext()) {
                try {
                    val apMap = EachWifiFingerprint.parseAccessPointsFromJson(it.getString(apIndex))
                    val fingerprint = EachWifiFingerprint(
                        pointId = it.getString(pointIdIndex),
                        buildingId = it.getInt(buildingIdIndex),
                        latitude = it.getDouble(latIndex),
                        longitude = it.getDouble(lonIndex),
                        timestamp = it.getLong(timeIndex),
                        accessPoints = apMap
                    )
                    fingerprints.add(fingerprint)
                } catch (e: Exception) {
                    Log.e("FingerprintDB", "Error parsing fingerprint from DB", e)
                }
            }
        }
        db.close()
        Log.d("FingerprintDB", "Loaded ${fingerprints.size} fingerprints from database")
        return fingerprints
    }

}