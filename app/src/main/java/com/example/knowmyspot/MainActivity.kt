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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.*
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvWeather: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var geocoder: Geocoder

    private val repository by lazy { (application as LocationApplication).repository }

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastAddress: String? = null
    private var lastWeather: String? = null
    private val weatherApiKey = "daf995ddd2e62368f8cb8e6151c40a4e"
    private val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val isRecent = isRecentLocation(location)
                val accText = if (location.hasAccuracy()) " acc=${location.accuracy}" else ""
                Log.d("Location", "lastLocation t=${location.time}$accText recent=$isRecent lat=${location.latitude} lon=${location.longitude}")
                if (isRecent) {
                    handleLocation(location, shouldSave)
                } else {
                    fetchCurrentLocation(shouldSave)
                }
            } else {
                // Fallback: získání aktuální polohy (lastLocation bývá null na čerstvě spuštěném zařízení/emulátoru)
                fetchCurrentLocation(shouldSave)
            }
        }.addOnFailureListener { e ->
            Log.e("Location", "lastLocation selhalo: ${e.message}", e)
            fetchCurrentLocation(shouldSave)
        }
    }

    private fun isRecentLocation(location: Location, maxAgeMs: Long = 10_000L): Boolean {
        return System.currentTimeMillis() - location.time <= maxAgeMs
    }

    private fun effectivePriority(): Int {
        val fineGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (fineGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }

    private fun fetchCurrentLocation(shouldSave: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }
        val priority = effectivePriority()
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(priority, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d("Location", "getCurrentLocation lat=${location.latitude} lon=${location.longitude} t=${location.time} acc=${location.accuracy}")
                    handleLocation(location, shouldSave)
                } else {
                    // Pokud nevrátí aktuální polohu, požádej o krátké aktivní updaty
                    requestSingleLocationUpdate(shouldSave)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Location", "getCurrentLocation selhalo: ${e.message}", e)
                requestSingleLocationUpdate(shouldSave)
            }
    }

    private fun requestSingleLocationUpdate(shouldSave: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }
        val priority = effectivePriority()
        val fineGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val request = LocationRequest.Builder(priority, 1000L)
            .setMinUpdateIntervalMillis(0L)
            .setMaxUpdateDelayMillis(1000L)
            .setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(fineGranted)
            .setDurationMillis(10_000L)
            .setMaxUpdates(3) // pár pokusů, aby se dohnala změna v emulátoru
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    Log.d("Location", "requestUpdate lat=${loc.latitude} lon=${loc.longitude} t=${loc.time} acc=${loc.accuracy}")
                    fusedLocationClient.removeLocationUpdates(this)
                    handleLocation(loc, shouldSave)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
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

// --- OpenWeatherMap API rozhraní ---
interface WeatherApi {
    @GET("weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String,
        @Query("lang") lang: String = "cz"
    ): Call<WeatherResponse>
}

data class WeatherResponse(
    val weather: List<WeatherDesc>,
    val main: MainWeather
)
data class WeatherDesc(val description: String)
data class MainWeather(val temp: Double)
