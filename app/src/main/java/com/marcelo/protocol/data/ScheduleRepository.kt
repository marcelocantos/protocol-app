// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.DayType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleRepository(private val db: ProtocolDatabase) {

    companion object {
        val DEFAULT_SCHEDULE = mapOf(
            DayOfWeek.MONDAY to DayType.OFFICE,
            DayOfWeek.TUESDAY to DayType.WFH,
            DayOfWeek.WEDNESDAY to DayType.OFFICE,
            DayOfWeek.THURSDAY to DayType.WFH,
            DayOfWeek.FRIDAY to DayType.WFH,
            DayOfWeek.SATURDAY to DayType.REST,
            DayOfWeek.SUNDAY to DayType.REST,
        )
    }

    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val schedule: Flow<Map<DayOfWeek, DayType>> =
        merge(flow { emit(Unit) }, _refresh).map {
            val stored = db.getSchedule()
            if (stored.isEmpty()) DEFAULT_SCHEDULE else DEFAULT_SCHEDULE + stored
        }

    fun dayTypeFor(date: LocalDate): Flow<DayType> = schedule.map { s ->
        s[date.dayOfWeek] ?: DayType.REST
    }

    suspend fun setDayType(day: DayOfWeek, type: DayType) {
        db.setScheduleDay(day, type)
        _refresh.emit(Unit)
    }
}
