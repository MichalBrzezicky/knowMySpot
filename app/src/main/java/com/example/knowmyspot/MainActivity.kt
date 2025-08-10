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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.knowmyspot.data.LocationRecord
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvWeather: TextView
    private lateinit var btnRefresh: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var geocoder: Geocoder

    private val repository by lazy { (application as LocationApplication).repository }

    private var lastLat: Double? = null
    private var lastLon: Double? = null
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

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            getLastLocation()
        }
        getLastLocation()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLastLocation() {
        btnRefresh.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                lastLat = location.latitude
                lastLon = location.longitude
                tvCoordinates.text = "Lat: %.5f, Lon: %.5f".format(location.latitude, location.longitude)
                lifecycleScope.launch {
                    val address = getAddressFromLocation(location)
                    tvLocation.text = address ?: "Adresa nenalezena"
                    getWeather(address)
                }
            } else {
                tvLocation.text = "Poloha není dostupná"
                tvWeather.text = "Počasí: --"
                progressBar.visibility = View.GONE
                btnRefresh.visibility = View.VISIBLE
            }
        }
    }

    private fun getWeather(address: String?) {
        val lat = lastLat
        val lon = lastLon
        if (lat == null || lon == null) {
            tvWeather.text = "Počasí: --"
            Log.e("Weather", "Souřadnice nejsou k dispozici")
            progressBar.visibility = View.GONE
            btnRefresh.visibility = View.VISIBLE
            return
        }
        weatherApi.getWeather(lat, lon, weatherApiKey).enqueue(object : Callback<WeatherResponse> {
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
                    val weatherText = "Počasí: $temp°C, $desc"
                    tvWeather.text = weatherText
                    lastWeather = weatherText
                    // Uložení do historie
                    val lat = lastLat
                    val lon = lastLon
                    if (lat != null && lon != null) {
                        lifecycleScope.launch {
                            repository.insert(
                                LocationRecord(
                                    latitude = lat,
                                    longitude = lon,
                                    timestamp = System.currentTimeMillis(),
                                    address = address ?: "Neznámá adresa",
                                    weather = weatherText
                                )
                            )
                        }
                    }
                    Log.d("Weather", "Úspěch: $temp°C, $desc")
                } else {
                    tvWeather.text = "Počas��: --"
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

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation()
        }
    }

    private fun getAddressFromLocation(location: Location): String? {
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                addresses[0].getAddressLine(0)
            } else {
                null
            }
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
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "cz"
    ): Call<WeatherResponse>
}

data class WeatherResponse(
    val weather: List<WeatherDesc>,
    val main: MainWeather
)
data class WeatherDesc(val description: String)
data class MainWeather(val temp: Double)
