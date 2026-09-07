// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.week

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.effectiveDayType
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.checklistFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
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
    private val completionRepo = app.completionRepo
    private val scheduleRepo = app.scheduleRepo
    private val planningRepo = app.planningRepo

    val schedule: StateFlow<Map<DayOfWeek, DayType>> = scheduleRepo.schedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _gymCount = MutableStateFlow(0)
    val gymCount: StateFlow<Int> = _gymCount

    private val _weekSummary = MutableStateFlow<List<DaySummary>>(emptyList())
    val weekSummary: StateFlow<List<DaySummary>> = _weekSummary

    fun setDayType(day: DayOfWeek, type: DayType) {
        viewModelScope.launch { scheduleRepo.setDayType(day, type) }
    }

    init {
        viewModelScope.launch {
            _gymCount.value = completionRepo.gymCountForWeek(LocalDate.now())

            combine(
                scheduleRepo.schedule,
                merge(flow { emit(Unit) }, planningRepo.changes, completionRepo.changes),
            ) { sched, _ -> sched }.collect { sched ->
                val today = LocalDate.now()
                _gymCount.value = completionRepo.gymCountForWeek(today)
                val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val allCompletions = completionRepo.completedItemsBatch(days)
                val weekPlans = days
                    .map { planningRepo.weekStartFor(it) }
                    .distinct()
                    .associateWith { planningRepo.weekPlanNow(it) }
                _weekSummary.value = days.map { date ->
                    val plans = weekPlans[planningRepo.weekStartFor(date)] ?: emptyMap()
                    val type = effectiveDayType(date, sched, plans)
                    val completed = allCompletions[date] ?: emptyMap()
                    val total = checklistFor(type).size
                    val done = checklistFor(type).count { it.id in completed }
                    DaySummary(date, type, if (total > 0) done.toFloat() / total else 0f)
                }
            }
        }
    }
}
