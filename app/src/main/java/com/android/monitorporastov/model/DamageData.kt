package com.android.monitorporastov.model

import android.graphics.Bitmap
import org.osmdroid.util.GeoPoint

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
    var bitmaps: MutableList<Bitmap> = mutableListOf(),
    var indexesOfPhotos: MutableList<Int> = mutableListOf(),
    var isNew: Boolean = true,
    var bitmapsLoaded:Boolean = false,
    var isItemFromMap:Boolean = true
)
