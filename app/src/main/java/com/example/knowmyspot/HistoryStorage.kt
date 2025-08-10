package com.example.knowmyspot

data class HistoryItem(
    val latitude: Double,
    val longitude: Double,
    val weather: String,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object HistoryStorage {
    val items = mutableListOf<HistoryItem>()
}

