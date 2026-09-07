// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek

class ScheduleDefaultsTest {

    @Test
    fun defaultScheduleCoversEveryWeekday() {
        // effectiveDayType falls back to REST for any missing day, so a gap here
        // would silently show a rest checklist on a working day.
        for (day in DayOfWeek.entries) {
            assertNotNull("DEFAULT_SCHEDULE has no entry for $day", ScheduleRepository.DEFAULT_SCHEDULE[day])
        }
    }

    @Test
    fun storedDaysOverrideDefaultsAndUnsetDaysFallBack() {
        // Mirrors ScheduleRepository.schedule's merge: DEFAULT_SCHEDULE + stored.
        val stored = mapOf(DayOfWeek.TUESDAY to DayType.OFFICE)
        val merged = ScheduleRepository.DEFAULT_SCHEDULE + stored

        assertEquals(DayType.OFFICE, merged[DayOfWeek.TUESDAY])
        assertEquals(ScheduleRepository.DEFAULT_SCHEDULE[DayOfWeek.MONDAY], merged[DayOfWeek.MONDAY])
        assertEquals(ScheduleRepository.DEFAULT_SCHEDULE.keys, merged.keys)
    }

    @Test
    fun weekendsDefaultToRest() {
        assertEquals(DayType.REST, ScheduleRepository.DEFAULT_SCHEDULE[DayOfWeek.SATURDAY])
        assertEquals(DayType.REST, ScheduleRepository.DEFAULT_SCHEDULE[DayOfWeek.SUNDAY])
    }
}
