package com.android.monitorporastov.placeholder

import android.graphics.Bitmap

/**
 * Trieda definujúca prvok obsahújúci údaje o poškodení.
 */
data class PlaceholderItem(
    val id: Int,
    val name: String,
    val damageType: String,
    val info: String,
    val photos: List<Bitmap> = emptyList(),
    val perimeter: Double = 0.0,
    val area: Double = 0.0
)