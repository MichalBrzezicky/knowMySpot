package com.example.knowmyspot.data

class LocationRepository(private val db: AppDatabase) {
    fun getAllRecords(): List<LocationRecord> = db.getAllRecords()

    fun insert(locationRecord: LocationRecord) {
        db.insertRecord(locationRecord)
    }

    fun update(locationRecord: LocationRecord) {
        db.updateRecord(locationRecord)
    }

    fun delete(locationRecord: LocationRecord) {
        db.deleteRecord(locationRecord)
    }
}
