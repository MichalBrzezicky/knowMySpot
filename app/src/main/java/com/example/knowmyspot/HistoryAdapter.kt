package com.example.knowmyspot

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.knowmyspot.data.LocationRecord
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val context: Context,
    private val viewModel: HistoryViewModel
) : ListAdapter<LocationRecord, HistoryAdapter.HistoryViewHolder>(LocationRecordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = getItem(position)
        holder.bind(record, viewModel)
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val address: TextView = itemView.findViewById(R.id.tvHistoryItemAddress)
        private val timestamp: TextView = itemView.findViewById(R.id.tvHistoryItemTimestamp)
        private val weather: TextView = itemView.findViewById(R.id.tvHistoryItemWeather)
        private val coordinates: TextView = itemView.findViewById(R.id.tvHistoryItemCoordinates)
        private val note: TextView = itemView.findViewById(R.id.tvHistoryItemNote)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditNote)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteRecord)
        private val etNote: EditText = itemView.findViewById(R.id.etNote)
        private val btnSave: Button = itemView.findViewById(R.id.btnSaveNote)

        fun bind(record: LocationRecord, viewModel: HistoryViewModel) {
            address.text = record.address
            timestamp.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            weather.text = record.weather ?: "Počasí: --"
            coordinates.text = "Lat: %.5f, Lon: %.5f".format(record.latitude, record.longitude)
            note.text = "Poznámka: ${record.note ?: "--"}"

            etNote.visibility = View.GONE
            btnSave.visibility = View.GONE

            btnEdit.setOnClickListener {
                if (etNote.visibility == View.GONE) {
                    etNote.setText(record.note ?: "")
                    etNote.visibility = View.VISIBLE
                    btnSave.visibility = View.VISIBLE
                    etNote.requestFocus()
                } else {
                    etNote.visibility = View.GONE
                    btnSave.visibility = View.GONE
                }
            }

            btnDelete.setOnClickListener {
                viewModel.delete(record)
            }

            btnSave.setOnClickListener {
                val updatedRecord = record.copy(note = etNote.text.toString())
                viewModel.update(updatedRecord)
                etNote.visibility = View.GONE
                btnSave.visibility = View.GONE
            }
        }
    }
}

class LocationRecordDiffCallback : DiffUtil.ItemCallback<LocationRecord>() {
    override fun areItemsTheSame(oldItem: LocationRecord, newItem: LocationRecord): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: LocationRecord, newItem: LocationRecord): Boolean {
        return oldItem == newItem
    }
}
