// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class PlanningRepository(private val db: ProtocolDatabase) {

    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun weekStartFor(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekPlan(weekStart: LocalDate): Flow<Map<DayOfWeek, OfficeDayPlan>> =
        merge(flow { emit(Unit) }, _refresh).map {
            db.getWeekPlan(weekStart)
        }

    suspend fun updateDay(weekStart: LocalDate, day: DayOfWeek, plan: OfficeDayPlan) {
        db.setWeekPlanDay(weekStart, day, plan)
        _refresh.emit(Unit)
    }

    fun unplannedDays(weekStart: LocalDate, schedule: Map<DayOfWeek, DayType>): Flow<Int> =
        weekPlan(weekStart).map { plans ->
            val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            weekdays.count { day ->
                val plan = plans[day]
                val effectiveType = plan?.dayType ?: schedule[day] ?: DayType.REST
                plan?.dayType == null ||
                    (effectiveType == DayType.OFFICE &&
                        plan.parkingStatus != ParkingStatus.BOOKED)
            }
        }
}
