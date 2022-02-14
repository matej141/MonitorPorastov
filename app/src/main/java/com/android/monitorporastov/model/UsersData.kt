package com.android.monitorporastov.model

import com.android.monitorporastov.model.Crs
import com.android.monitorporastov.model.Feature

data class UsersData(
    val crs: Crs,
    val features: List<Feature>,
    val numberMatched: Int,
    val numberReturned: Int,
    val timeStamp: String,
    val totalFeatures: Int,
    val type: String
)