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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProtocolApp
    private val scheduleRepo = ScheduleRepository(app.db)
    private val planningRepo = PlanningRepository(app.db)

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
            val defaultType = schedule[day] ?: DayType.REST
            val plan = plans[day] ?: OfficeDayPlan()
            PlanDayRow(day, date, defaultType, plan)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun viewThisWeek() {
        _weekStart.value = planningRepo.weekStartFor(LocalDate.now())
    }

    fun viewNextWeek() {
        _weekStart.value = planningRepo.weekStartFor(LocalDate.now().plusWeeks(1))
    }

    fun setPlannedType(day: DayOfWeek, type: DayType?) {
        viewModelScope.launch {
            val ws = _weekStart.value
            val current = days.value.find { it.day == day }?.plan ?: OfficeDayPlan()
            val newPlan = if (type == null) {
                current.copy(dayType = null, parkingStatus = ParkingStatus.UNPLANNED)
            } else {
                current.copy(dayType = type)
            }
            planningRepo.updateDay(ws, day, newPlan)
        }
    }

    fun setParkingStatus(day: DayOfWeek, status: ParkingStatus) {
        viewModelScope.launch {
            val ws = _weekStart.value
            val current = days.value.find { it.day == day }?.plan ?: OfficeDayPlan()
            planningRepo.updateDay(ws, day, current.copy(parkingStatus = status))
        }
    }
}
