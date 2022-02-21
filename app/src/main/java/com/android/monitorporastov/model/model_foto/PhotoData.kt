package com.android.monitorporastov.model.model_foto

data class PhotoData(
    val crs: Any,
    val features: List<Feature>,
    val numberMatched: Int,
    val numberReturned: Int,
    val timeStamp: String,
    val totalFeatures: Int,
    val type: String
)