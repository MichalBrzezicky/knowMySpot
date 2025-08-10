package com.example.knowmyspot.data

data class LocationRecord(
    val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val address: String,
    val weather: String?,
    val note: String? = null
)
