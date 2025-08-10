package com.example.knowmyspot

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.knowmyspot.data.LocationRecord

class HistoryAdapter(
    context: Context,
    private val records: List<LocationRecord>,
    private val viewModel: HistoryViewModel
) : ArrayAdapter<LocationRecord>(context, 0, records) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        val record = getItem(position) ?: return view

        val info = view.findViewById<TextView>(R.id.tvHistoryItemInfo)
        val btnEdit = view.findViewById<ImageButton>(R.id.btnEditNote)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<Button>(R.id.btnSaveNote)

        info.text = "Lat: %.5f, Lon: %.5f\n%s\nPoznámka: %s".format(record.latitude, record.longitude, record.address, record.note ?: "--")
        etNote.visibility = View.GONE
        btnSave.visibility = View.GONE

        btnEdit.setOnClickListener {
            etNote.setText(record.note ?: "")
            etNote.visibility = View.VISIBLE
            btnSave.visibility = View.VISIBLE
            etNote.requestFocus()
        }

        btnSave.setOnClickListener {
            val updatedRecord = record.copy(note = etNote.text.toString())
            viewModel.update(updatedRecord)
            etNote.visibility = View.GONE
            btnSave.visibility = View.GONE
        }

        return view
    }
}

