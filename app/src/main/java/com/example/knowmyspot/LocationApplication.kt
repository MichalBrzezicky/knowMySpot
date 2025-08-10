package com.example.knowmyspot

import android.app.Application
import com.example.knowmyspot.data.AppDatabase
import com.example.knowmyspot.data.LocationRepository

class LocationApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase(this) }
    val repository: LocationRepository by lazy { LocationRepository(database) }
}
