// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class EffectiveDayTypeTest {

    private val tuesday = LocalDate.of(2026, 9, 8)

    @Test
    fun weekPlanOverrideWinsOverSchedule() {
        val plan = mapOf(
            DayOfWeek.TUESDAY to OfficeDayPlan(DayType.OFFICE, ParkingStatus.UNPLANNED),
        )
        assertEquals(
            DayType.OFFICE,
            effectiveDayType(tuesday, ScheduleRepository.DEFAULT_SCHEDULE, plan),
        )
    }

    @Test
    fun scheduleAppliesWhenPlanHasNoOverride() {
        val plan = mapOf(
            DayOfWeek.TUESDAY to OfficeDayPlan(null, ParkingStatus.UNPLANNED),
        )
        assertEquals(
            DayType.WFH,
            effectiveDayType(tuesday, ScheduleRepository.DEFAULT_SCHEDULE, plan),
        )
        assertEquals(
            DayType.WFH,
            effectiveDayType(tuesday, ScheduleRepository.DEFAULT_SCHEDULE, emptyMap()),
        )
    }

    @Test
    fun fallbackAppliesWhenNeitherTableKnowsTheDay() {
        assertEquals(
            FALLBACK_DAY_TYPE,
            effectiveDayType(tuesday, emptyMap(), emptyMap()),
        )
    }
}
