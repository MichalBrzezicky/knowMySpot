package com.example.knowmyspot.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource

class LocationProvider(
    private val context: Context,
    private val client: FusedLocationProviderClient
) {
    private fun hasAnyPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun effectivePriority(): Int {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }

    private fun isRecent(location: Location, maxAgeMs: Long = 10_000L): Boolean =
        System.currentTimeMillis() - location.time <= maxAgeMs

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getLastOrFreshLocation(onResult: (Location?) -> Unit) {
        if (!hasAnyPermission()) {
            onResult(null)
            return
        }
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null && isRecent(loc)) {
                    onResult(loc)
                } else {
                    fetchCurrent(onResult)
                }
            }
            .addOnFailureListener { fetchCurrent(onResult) }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun fetchCurrent(onResult: (Location?) -> Unit) {
        if (!hasAnyPermission()) {
            onResult(null)
            return
        }
        val cts = CancellationTokenSource()
        client.getCurrentLocation(effectivePriority(), cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) onResult(loc) else requestSingleUpdate(onResult)
            }
            .addOnFailureListener { requestSingleUpdate(onResult) }
    }

    private fun requestSingleUpdate(onResult: (Location?) -> Unit) {
        if (!hasAnyPermission()) {
            onResult(null)
            return
        }
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val request = LocationRequest.Builder(effectivePriority(), 1000L)
            .setMinUpdateIntervalMillis(0L)
            .setMaxUpdateDelayMillis(1000L)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(fine)
            .setDurationMillis(10_000L)
            .setMaxUpdates(3)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                client.removeLocationUpdates(this)
                onResult(loc)
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }
}
