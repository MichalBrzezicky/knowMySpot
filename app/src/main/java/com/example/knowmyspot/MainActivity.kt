package com.example.knowmyspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.knowmyspot.data.LocationRecord
import com.example.knowmyspot.network.WeatherApi
import com.example.knowmyspot.network.WeatherResponse
import com.example.knowmyspot.network.WeatherService
import com.example.knowmyspot.location.LocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvWeather: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var geocoder: Geocoder
    private lateinit var locationProvider: LocationProvider

    private val repository by lazy { (application as LocationApplication).repository }

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastAddress: String? = null
    private var lastWeather: String? = null
    private val weatherApiKey = "daf995ddd2e62368f8cb8e6151c40a4e"
    private val weatherApi: WeatherApi by lazy { WeatherService.api }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationProvider = LocationProvider(this, fusedLocationClient)
        tvLocation = findViewById(R.id.tvLocation)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvWeather = findViewById(R.id.tvWeather)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        geocoder = Geocoder(this, Locale.getDefault())
        btnSave = findViewById(R.id.btnSave)

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            getLastLocation(false)
        }
        btnSave.setOnClickListener {
            saveCurrentLocation()
        }
        getLastLocation(true)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        // Znovu načte polohu a počasí pro případ, že se změnilo nastavení (např. jednotky)
        getLastLocation(false)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLastLocation(shouldSave: Boolean) {
        btnRefresh.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // žádost o oprávnění, aby uživatel mohl zvolit přesnou i přibližnou polohu
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }
        locationProvider.getLastOrFreshLocation { location: Location? ->
            if (location != null) {
                handleLocation(location, shouldSave)
            } else {
                tvLocation.text = "Poloha není dostupná"
                tvWeather.text = "Počasí: --"
                progressBar.visibility = View.GONE
                btnRefresh.visibility = View.VISIBLE
            }
        }
    }

    private fun handleLocation(location: Location, shouldSave: Boolean) {
        lastLat = location.latitude
        lastLon = location.longitude
        tvCoordinates.text = "Lat: %.5f, Lon: %.5f".format(location.latitude, location.longitude)
        lifecycleScope.launch {
            val address = getAddressFromLocation(location)
            tvLocation.text = address ?: "Adresa nenalezena"
            lastAddress = address
            getWeather(address, shouldSave)
        }
    }

    private fun getWeather(address: String?, shouldSave: Boolean) {
        val lat = lastLat
        val lon = lastLon
        if (lat == null || lon == null) {
            tvWeather.text = "Počasí: --"
            Log.e("Weather", "Souřadnice nejsou k dispozici")
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val unit = sharedPreferences.getString("unit", "metric") ?: "metric"

        weatherApi.getWeather(lat, lon, weatherApiKey, unit).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (!response.isSuccessful) {
                    tvWeather.text = "Počasí: --"
                    Log.e("Weather", "Chyba HTTP: ${response.code()} ${response.message()}")
                    progressBar.visibility = View.GONE
                    btnRefresh.visibility = View.VISIBLE
                    return
                }
                val body = response.body()
                if (body != null) {
                    val desc = body.weather.firstOrNull()?.description ?: "--"
                    val temp = body.main.temp
                    val unitSymbol = if (unit == "imperial") "°F" else "°C"
                    val weatherText = "Počasí: $temp$unitSymbol, $desc"
                    tvWeather.text = weatherText
                    lastWeather = weatherText
                    // Uložení do historie
                    if (shouldSave) {
                        val lat = lastLat
                        val lon = lastLon
                        if (lat != null && lon != null) {
                            val defaultNote = sharedPreferences.getString("default_note", "")
                            lifecycleScope.launch {
                                repository.insert(
                                    LocationRecord(
                                        latitude = lat,
                                        longitude = lon,
                                        timestamp = System.currentTimeMillis(),
                                        address = address ?: "Neznámá adresa",
                                        weather = weatherText,
                                        note = defaultNote
                                    )
                                )
                            }
                        }
                    }
                    Log.d("Weather", "Úspěch: $temp$unitSymbol, $desc")
                } else {
                    tvWeather.text = "Počasí: --"
                    Log.e("Weather", "Tělo odpovědi je null")
                }
                progressBar.visibility = View.GONE
                btnRefresh.visibility = View.VISIBLE
            }
            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                tvWeather.text = "Počasí: --"
                Log.e("Weather", "Chyba volání: ${t.message}", t)
                progressBar.visibility = View.GONE
                btnRefresh.visibility = View.VISIBLE
            }
        })
    }

    private fun saveCurrentLocation() {
        if (lastLat != null && lastLon != null && lastAddress != null) {
            val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
            val defaultNote = sharedPreferences.getString("default_note", "")
            val record = LocationRecord(
                latitude = lastLat!!,
                longitude = lastLon!!,
                timestamp = System.currentTimeMillis(),
                address = lastAddress!!,
                weather = lastWeather,
                note = defaultNote
            )
            lifecycleScope.launch {
                repository.insert(record)
                Toast.makeText(this@MainActivity, "Poloha uložena", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Nelze uložit, data o poloze nejsou k dispozici.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val fineGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                getLastLocation(true)
            } else {
                Toast.makeText(this, "Bez povolení polohy nelze získat souřadnice.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getAddressFromLocation(location: Location): String? {
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                addresses[0].getAddressLine(0)
            } else null
        } catch (e: IOException) {
            Log.e("Geocoder", "Nelze získat adresu", e)
            null
        }
    }
}
