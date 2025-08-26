package io.github.mycampusmaptst1

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION){
    companion object {
        private const val DB_NAME = "MyDatabase15.db"
        private const val DB_VERSION = 1
        const val TABLE_NAME = "allCampusLocationsTable"
        const val COLUMN_BUILDING_ID = "building_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_TYPE = "type"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_OPEN_HOURS = "open_hours"
        const val COLUMN_IMAGE_PATH = "image_path"
    }

    private val dbPath: String = context.getDatabasePath(DB_NAME).path
    private val myContext: Context = context.applicationContext

    private fun isDatabaseExists(): Boolean {
        val dbFile = File(dbPath)
        return dbFile.exists()
    }


    fun copyDatabaseFromAssets() {
        val file = File(dbPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            try {
                myContext.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbPath).use { output ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                        output.flush()
                    }
                }
                Log.d("DatabaseHelper", "Database copied to $dbPath")
            } catch (ex: IOException) {
                Log.d("DatabaseHelper", ex.message ?: "Unknown error!")
                throw RuntimeException("Database copy failed")
            }
        }
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        if (!isDatabaseExists()) {
            copyDatabaseFromAssets()
        }
        return super.getReadableDatabase()
    }
    override fun getWritableDatabase(): SQLiteDatabase {
        if (!isDatabaseExists()) {
            copyDatabaseFromAssets()
        }
        return super.getWritableDatabase()
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}


}
