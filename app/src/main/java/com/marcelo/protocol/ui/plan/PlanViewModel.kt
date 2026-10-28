// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.PlanningRepository
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class PlanDayRow(
    val day: DayOfWeek,
    val date: LocalDate,
    val defaultType: DayType,
    val plan: OfficeDayPlan,
) {
    val effectiveType: DayType get() = plan.dayType ?: defaultType
    val isDecided: Boolean get() = plan.dayType != null
    /** PLANNED is a legacy state; only BOOKED counts. */
    val isBooked: Boolean get() = plan.parkingStatus == ParkingStatus.BOOKED
    val needsBooking: Boolean get() = plan.dayType == DayType.OFFICE && !isBooked
    /** Booked, but no longer an office day: a paid booking to cancel. */
    val staleBooking: Boolean get() = isBooked && plan.dayType != DayType.OFFICE
}

/** The one thing the week needs next, in priority order. */
sealed interface PlanStatus {
    data class Decide(val days: List<PlanDayRow>) : PlanStatus
    data class Book(val days: List<PlanDayRow>) : PlanStatus
    data class AllSet(val booked: List<PlanDayRow>) : PlanStatus
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProtocolApp
    private val scheduleRepo = ScheduleRepository(app.db)
    private val planningRepo = PlanningRepository(app.db)

    private val thisWeek get() = planningRepo.weekStartFor(LocalDate.now())

    private val _weekStart = MutableStateFlow(planningRepo.weekStartFor(LocalDate.now().plusWeeks(1)))
    val weekStart: StateFlow<LocalDate> = _weekStart

    val days: StateFlow<List<PlanDayRow>> = combine(
        _weekStart,
        scheduleRepo.schedule,
        _weekStart.flatMapLatest { planningRepo.weekPlan(it) },
    ) { start, schedule, plans ->
        (0L..4L).map { offset ->
            val date = start.plusDays(offset)
            val day = date.dayOfWeek
            PlanDayRow(day, date, schedule[day] ?: DayType.REST, plans[day] ?: OfficeDayPlan())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val status: StateFlow<PlanStatus?> = days.map { rows ->
        if (rows.isEmpty()) return@map null
        val upcoming = rows.filter { !it.date.isBefore(LocalDate.now()) }
        val undecided = upcoming.filter { !it.isDecided }
        val toBook = upcoming.filter { it.needsBooking }
        when {
            undecided.isNotEmpty() -> PlanStatus.Decide(undecided)
            toBook.isNotEmpty() -> PlanStatus.Book(toBook)
            else -> PlanStatus.AllSet(rows.filter { it.isBooked && it.plan.dayType == DayType.OFFICE })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Set when the user jumps to Wilson; on return we ask what they booked. */
    private val _awaitingWilsonReturn = MutableStateFlow(false)
    val awaitingWilsonReturn: StateFlow<Boolean> = _awaitingWilsonReturn.asStateFlow()

    fun weekOffset(): Long = java.time.temporal.ChronoUnit.WEEKS.between(thisWeek, _weekStart.value)

    fun shiftWeek(by: Long) {
        val next = _weekStart.value.plusWeeks(by)
        if (!next.isBefore(thisWeek)) _weekStart.value = next
    }

    fun showNextWeek() { _weekStart.value = thisWeek.plusWeeks(1) }

    fun setPlannedType(day: DayOfWeek, type: DayType) = update(day) { it.copy(dayType = type) }

    /** Accept the usual schedule for every undecided day in one tap. */
    fun confirmUsual() {
        viewModelScope.launch {
            days.value.filter { !it.isDecided }.forEach { row ->
                planningRepo.updateDay(_weekStart.value, row.day, row.plan.copy(dayType = row.defaultType))
            }
        }
    }

    fun setBooked(day: DayOfWeek, booked: Boolean) = update(day) {
        it.copy(parkingStatus = if (booked) ParkingStatus.BOOKED else ParkingStatus.UNPLANNED)
    }

    fun markBooked(days: Collection<DayOfWeek>) {
        _awaitingWilsonReturn.value = false
        days.forEach { setBooked(it, true) }
    }

    fun wilsonLaunched() { _awaitingWilsonReturn.value = true }
    fun dismissReturnPrompt() { _awaitingWilsonReturn.value = false }

    private fun update(day: DayOfWeek, f: (OfficeDayPlan) -> OfficeDayPlan) {
        viewModelScope.launch {
            val current = days.value.find { it.day == day }?.plan ?: OfficeDayPlan()
            planningRepo.updateDay(_weekStart.value, day, f(current))
        }
    }
}
