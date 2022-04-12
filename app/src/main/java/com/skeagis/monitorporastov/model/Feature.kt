package com.skeagis.monitorporastov.model

data class Feature(
    val geometry: Geometry,
    val geometry_name: String,
    val id: String,
    val properties: DamageData,
    val type: String
)