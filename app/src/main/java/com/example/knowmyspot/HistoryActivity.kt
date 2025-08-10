package com.example.knowmyspot

import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    private val historyViewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory((application as LocationApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val listView = findViewById<ListView>(R.id.listHistory)
        val emptyView = findViewById<TextView>(R.id.tvHistoryEmpty)
        listView.emptyView = emptyView

        historyViewModel.allRecords.observe(this) { records ->
            records?.let {
                val adapter = HistoryAdapter(this, it, historyViewModel)
                listView.adapter = adapter
            }
        }
    }
}
