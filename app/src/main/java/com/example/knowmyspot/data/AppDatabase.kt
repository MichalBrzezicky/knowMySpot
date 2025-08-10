package com.example.knowmyspot.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "location_history.db"
        private const val DATABASE_VERSION = 3 // zvýšení verze kvůli migraci

        const val TABLE_NAME = "location_records"
        const val COLUMN_ID = "id"
        const val COLUMN_LAT = "latitude"
        const val COLUMN_LNG = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_WEATHER = "weather"
        const val COLUMN_NOTE = "note"

        private const val SQL_CREATE_TABLE =
            "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_LAT REAL NOT NULL, " +
                    "$COLUMN_LNG REAL NOT NULL, " +
                    "$COLUMN_TIMESTAMP INTEGER NOT NULL, " +
                    "$COLUMN_ADDRESS TEXT NOT NULL, " +
                    "$COLUMN_WEATHER TEXT, " +
                    "$COLUMN_NOTE TEXT" +
            ")"
        private const val SQL_DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DROP_TABLE)
        onCreate(db)
    }

    fun insertRecord(record: LocationRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LAT, record.latitude)
            put(COLUMN_LNG, record.longitude)
            put(COLUMN_TIMESTAMP, record.timestamp)
            put(COLUMN_ADDRESS, record.address)
            put(COLUMN_WEATHER, record.weather)
            put(COLUMN_NOTE, record.note)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun updateRecord(record: LocationRecord): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LAT, record.latitude)
            put(COLUMN_LNG, record.longitude)
            put(COLUMN_TIMESTAMP, record.timestamp)
            put(COLUMN_ADDRESS, record.address)
            put(COLUMN_WEATHER, record.weather)
            put(COLUMN_NOTE, record.note)
        }
        return db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(record.id.toString()))
    }

    fun deleteRecord(record: LocationRecord) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(record.id.toString()))
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }

    fun getAllRecords(): List<LocationRecord> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID, COLUMN_LAT, COLUMN_LNG, COLUMN_TIMESTAMP, COLUMN_ADDRESS, COLUMN_WEATHER, COLUMN_NOTE),
            null, null, null, null,
            "$COLUMN_TIMESTAMP DESC"
        )
        val records = mutableListOf<LocationRecord>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)).toInt()
            val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT))
            val lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LNG))
            val ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS))
            val weather = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WEATHER))
            val note = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE))
            records.add(LocationRecord(id, lat, lng, ts, address, weather, note))
        }
        cursor.close()
        return records
    }
}
