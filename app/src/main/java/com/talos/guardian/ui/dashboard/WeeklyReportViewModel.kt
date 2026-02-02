package com.talos.guardian.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talos.guardian.data.WeeklyReport
import com.talos.guardian.data.WeeklyReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeeklyReportViewModel(private val repository: WeeklyReportRepository = WeeklyReportRepository()) : ViewModel() {

    private val _reports = MutableStateFlow<List<WeeklyReport>>(emptyList())
    val reports: StateFlow<List<WeeklyReport>> = _reports

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun fetchReports(parentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedReports = repository.getReportsForParent(parentId)
            _reports.value = fetchedReports
            _isLoading.value = false
        }
    }

    fun markAsRead(reportId: String) {
        viewModelScope.launch {
            repository.markReportAsRead(reportId)
            // Update local state to reflect read status
            _reports.value = _reports.value.map {
                if (it.id == reportId) it.copy(isRead = true) else it
            }
        }
    }
}