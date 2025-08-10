package com.example.knowmyspot

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val listView = findViewById<ListView>(R.id.listHistory)
        val emptyView = findViewById<TextView>(R.id.tvHistoryEmpty)

        val adapter = object : ArrayAdapter<HistoryItem>(this, R.layout.item_history, R.id.tvHistoryItemInfo, HistoryStorage.items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val inflater = layoutInflater
                val view = convertView ?: inflater.inflate(R.layout.item_history, parent, false)
                val item = getItem(position)
                val info = view.findViewById<TextView>(R.id.tvHistoryItemInfo)
                val btnEdit = view.findViewById<ImageButton>(R.id.btnEditNote)
                val etNote = view.findViewById<EditText>(R.id.etNote)
                val btnSave = view.findViewById<Button>(R.id.btnSaveNote)

                // Zobraz info o záznamu a poznámku pouze jako text
                info.text = "Lat: %.5f, Lon: %.5f\n%s\nPoznámka: %s".format(item?.latitude, item?.longitude, item?.weather, item?.note ?: "--")
                etNote.visibility = View.GONE
                btnSave.visibility = View.GONE

                btnEdit.setOnClickListener {
                    etNote.setText(item?.note ?: "")
                    etNote.visibility = View.VISIBLE
                    btnSave.visibility = View.VISIBLE
                    etNote.requestFocus()
                }
                btnSave.setOnClickListener {
                    item?.let {
                        val index = HistoryStorage.items.indexOf(it)
                        if (index != -1) {
                            HistoryStorage.items[index] = it.copy(note = etNote.text.toString())
                            notifyDataSetChanged()
                        }
                    }
                    etNote.visibility = View.GONE
                    btnSave.visibility = View.GONE
                }
                return view
            }
        }
        listView.adapter = adapter
        listView.emptyView = emptyView
    }
}
