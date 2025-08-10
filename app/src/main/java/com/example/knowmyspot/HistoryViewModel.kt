package com.example.knowmyspot

import androidx.lifecycle.*
import com.example.knowmyspot.data.LocationRecord
import com.example.knowmyspot.data.LocationRepository
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: LocationRepository) : ViewModel() {

    private val _allRecords = MutableLiveData<List<LocationRecord>>()
    val allRecords: LiveData<List<LocationRecord>> = _allRecords

    init {
        loadRecords()
    }

    private fun loadRecords() {
        viewModelScope.launch {
            _allRecords.postValue(repository.getAllRecords())
        }
    }

    fun update(locationRecord: LocationRecord) = viewModelScope.launch {
        repository.update(locationRecord)
        loadRecords()
    }

    fun delete(locationRecord: LocationRecord) = viewModelScope.launch {
        repository.delete(locationRecord)
        loadRecords()
    }
}

class HistoryViewModelFactory(private val repository: LocationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
