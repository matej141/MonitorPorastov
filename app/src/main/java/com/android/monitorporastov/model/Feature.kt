package com.android.monitorporastov.model

import com.android.monitorporastov.model.DamageData
import com.android.monitorporastov.model.Geometry

data class Feature(
    val geometry: Geometry,
    val geometry_name: String,
    val id: String,
    val properties: DamageData,
    val type: String
)