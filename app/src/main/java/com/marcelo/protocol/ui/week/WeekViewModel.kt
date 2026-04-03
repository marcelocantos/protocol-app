package com.marcelo.protocol.ui.week

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.ChecklistRepository
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.checklistFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class DaySummary(
    val date: LocalDate,
    val dayType: DayType,
    val completionFraction: Float,
)

class WeekViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProtocolApp
    private val scheduleRepo = ScheduleRepository(app.dataStore)
    private val checklistRepo = ChecklistRepository(app.dataStore)

    val schedule: StateFlow<Map<DayOfWeek, DayType>> = scheduleRepo.schedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val gymCount: StateFlow<Int> = checklistRepo.gymCount(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekSummary: StateFlow<List<DaySummary>> = run {
        val today = LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val flows = days.map { date ->
            combine(
                scheduleRepo.dayTypeFor(date),
                checklistRepo.completedItems(date),
            ) { type, completed ->
                val total = checklistFor(type).size
                val done = checklistFor(type).count { it.id in completed }
                DaySummary(date, type, if (total > 0) done.toFloat() / total else 0f)
            }
        }
        combine(flows) { it.toList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setDayType(day: DayOfWeek, type: DayType) {
        viewModelScope.launch { scheduleRepo.setDayType(day, type) }
    }
}
