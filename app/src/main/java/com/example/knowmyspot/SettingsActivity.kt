package com.example.knowmyspot

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.knowmyspot.data.AppDatabase

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgUnits: RadioGroup
    private lateinit var rbMetric: RadioButton
    private lateinit var rbImperial: RadioButton
    private lateinit var etDefaultNote: EditText
    private lateinit var btnClearHistory: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)

        rgUnits = findViewById(R.id.rgUnits)
        rbMetric = findViewById(R.id.rbMetric)
        rbImperial = findViewById(R.id.rbImperial)
        etDefaultNote = findViewById(R.id.etDefaultNote)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        loadSettings()

        rgUnits.setOnCheckedChangeListener { _, checkedId ->
            val editor = sharedPreferences.edit()
            when (checkedId) {
                R.id.rbMetric -> editor.putString("unit", "metric")
                R.id.rbImperial -> editor.putString("unit", "imperial")
            }
            editor.apply()
        }

        btnClearHistory.setOnClickListener {
            showClearHistoryConfirmationDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        saveDefaultNote()
    }

    private fun loadSettings() {
        val unit = sharedPreferences.getString("unit", "metric")
        if (unit == "metric") {
            rbMetric.isChecked = true
        } else {
            rbImperial.isChecked = true
        }

        val defaultNote = sharedPreferences.getString("default_note", "")
        etDefaultNote.setText(defaultNote)
    }

    private fun saveDefaultNote() {
        val editor = sharedPreferences.edit()
        editor.putString("default_note", etDefaultNote.text.toString())
        editor.apply()
    }

    private fun showClearHistoryConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Vymazat historii")
            .setMessage("Opravdu si přejete vymazat celou historii vyhledávání?")
            .setPositiveButton("Vymazat") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun clearHistory() {
        val db = AppDatabase(applicationContext)
        db.clearHistory()
        Toast.makeText(this, "Historie vymazána", Toast.LENGTH_SHORT).show()
    }
}
