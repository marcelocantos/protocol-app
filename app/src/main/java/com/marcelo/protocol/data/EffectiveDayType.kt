// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The day type a date actually has, resolved from the two tables that can
 * answer the question.
 *
 * `week_plans` (the Plan screen) holds per-week overrides and wins when it
 * carries one; `schedule` (the Week screen) holds the recurring default;
 * [FALLBACK_DAY_TYPE] applies when neither table knows the day.
 *
 * Every screen and worker must resolve day type through this function. Today
 * and Week previously read `schedule` directly, so a Plan override never
 * reached the checklist.
 */
val FALLBACK_DAY_TYPE = DayType.REST

fun effectiveDayType(
    date: LocalDate,
    schedule: Map<DayOfWeek, DayType>,
    weekPlan: Map<DayOfWeek, OfficeDayPlan>,
): DayType =
    weekPlan[date.dayOfWeek]?.dayType
        ?: schedule[date.dayOfWeek]
        ?: FALLBACK_DAY_TYPE
