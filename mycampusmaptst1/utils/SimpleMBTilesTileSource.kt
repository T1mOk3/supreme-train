package io.github.mycampusmaptst1.utils

import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import android.database.sqlite.SQLiteDatabase
import android.util.Log

class SimpleMBTilesTileSource(private val mbtilesFile: File) :
    BitmapTileSourceBase("MBTiles", 10, 20, 256, ".png") {
    private var customMinZoom = 10
    private var customMaxZoom = 20


    init {
        determineZoomLevels()
    }

    private fun determineZoomLevels() {
        try {
            val db = SQLiteDatabase.openDatabase(
                mbtilesFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val cursor = db.rawQuery(
                "SELECT MIN(zoom_level) as min_zoom, MAX(zoom_level) as max_zoom FROM tiles",
                null
            )

            if (cursor.moveToFirst()) {
                customMinZoom = cursor.getInt(0)
                customMaxZoom = cursor.getInt(1)
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            // Use defaults if we can't read the DB
            customMinZoom = 10
            customMaxZoom = 20
        }
    }

    fun getTileInputStream(tileIndex: Long): InputStream? {
        return try {
            val db = SQLiteDatabase.openDatabase(
                mbtilesFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val zoom = MapTileIndex.getZoom(tileIndex)
            val x = MapTileIndex.getX(tileIndex)
            val y = (1 shl zoom) - 1 - MapTileIndex.getY(tileIndex) // MBTiles uses TMS y-axis

            val cursor = db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(zoom.toString(), x.toString(), y.toString())
            )

            val result = if (cursor.moveToFirst()) {
                ByteArrayInputStream(cursor.getBlob(0))
            } else {
                null
            }

            cursor.close()
            db.close()
            result
        } catch (e: Exception) {
            Log.e("MBTiles", "Error reading tile: ${e.message}")
            null
        }
    }

    // Override these methods to return our custom zoom levels
    override fun getMinimumZoomLevel(): Int {
        return customMinZoom
    }
    override fun getMaximumZoomLevel(): Int {
        return customMaxZoom
    }
}