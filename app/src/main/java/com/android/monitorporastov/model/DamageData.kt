package com.android.monitorporastov.model

import android.graphics.Bitmap
import org.osmdroid.util.GeoPoint
import java.time.chrono.ChronoLocalDateTime

data class DamageData(
    var id: Int = -1,
    var nazov: String = "",
    var pouzivatel: String = "",
    var typ_poskodenia: String = "",
    var popis_poskodenia: String = "",
    var obvod: Double = 0.0,
    var obsah: Double = 0.0,
    var foto: String = "",
    var coordinates: List<GeoPoint> = listOf(),
    var unique_id: String = "",
    var datetime: String? = "",
    var bitmaps: MutableList<Bitmap> = mutableListOf(),
    var indexesOfPhotos: MutableList<Int> = mutableListOf(),
    var isInGeoserver: Boolean = false,
    var changedShapeOfPolygon:Boolean = false,
    var bitmapsLoaded:Boolean = false,
    var isUpdatingDirectlyFromMap:Boolean = false,
)
