// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.week

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.data.RoutineRepository
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.RoutineItem
import com.marcelo.protocol.model.RoutineSchedule
import com.marcelo.protocol.notification.RoutineReminders
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.checklistFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val db: ProtocolDatabase = app.db
    private val scheduleRepo = ScheduleRepository(app.db)

    val schedule: StateFlow<Map<DayOfWeek, DayType>> = scheduleRepo.schedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _gymCount = MutableStateFlow(0)
    val gymCount: StateFlow<Int> = _gymCount

    private val _weekSummary = MutableStateFlow<List<DaySummary>>(emptyList())
    val weekSummary: StateFlow<List<DaySummary>> = _weekSummary

    private val routineRepo = RoutineRepository(app.db)

    val routine: StateFlow<List<RoutineItem>> = routineRepo.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Choose (or clear) the days for an "N times a week" item. */
    fun toggleRoutineDay(item: RoutineItem, day: DayOfWeek) {
        val s = item.schedule as? RoutineSchedule.PerWeek ?: return
        val days = if (day.name in s.days) s.days - day.name else s.days + day.name
        viewModelScope.launch {
            routineRepo.update(item.copy(schedule = s.copy(days = DayOfWeek.entries.map { it.name }.filter { it in days })))
            RoutineReminders.schedule(app)
        }
    }

    fun setDayType(day: DayOfWeek, type: DayType) {
        viewModelScope.launch { scheduleRepo.setDayType(day, type) }
    }

    init {
        viewModelScope.launch {
            _gymCount.value = db.gymCountForWeek(LocalDate.now())

            scheduleRepo.schedule.collect { sched ->
                val today = LocalDate.now()
                val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val allCompletions = db.completedItemsBatch(days)
                _weekSummary.value = days.map { date ->
                    val type = sched[date.dayOfWeek] ?: DayType.REST
                    val completed = allCompletions[date] ?: emptyMap()
                    val total = checklistFor(type).size
                    val done = checklistFor(type).count { it.id in completed }
                    DaySummary(date, type, if (total > 0) done.toFloat() / total else 0f)
                }
            }
        }
    }
}
