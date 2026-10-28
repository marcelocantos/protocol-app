// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * When a routine item applies. Days are `java.time.DayOfWeek` names
 * ("MONDAY", ...) so the JSON stays readable.
 */
@Serializable
sealed interface RoutineSchedule {

    /** Every day at [time]. */
    @Serializable @SerialName("daily")
    data class Daily(val time: String) : RoutineSchedule

    /** Fixed days at a fixed [time]. */
    @Serializable @SerialName("weekly")
    data class Weekly(val days: List<String>, val time: String) : RoutineSchedule

    /** Fixed days, any time between [start] and [end] (flexible window). */
    @Serializable @SerialName("window")
    data class Window(val days: List<String>, val start: String, val end: String) : RoutineSchedule

    /**
     * [times] sessions per Mon–Sun week. If [days] is empty, the days are
     * undecided: the item appears every day until the weekly target is met,
     * and nudges at [time] only when you fall behind pace.
     */
    @Serializable @SerialName("per_week")
    data class PerWeek(
        val times: Int,
        val days: List<String> = emptyList(),
        val time: String = "17:00",
    ) : RoutineSchedule
}

/** Reminder intensity. Mirrors the checklist NagLevel (NONE / NAG / ALARM). */
@Serializable
enum class RoutineNag { NONE, NAG, ALARM }

@Serializable
data class RoutineItem(
    val id: String,
    val label: String,
    val schedule: RoutineSchedule,
    /** Shown under the label, e.g. heart-rate target. */
    val detail: String? = null,
    val nag: RoutineNag = RoutineNag.NAG,
    val enabled: Boolean = true,
) {
    /** Checklist/completion id; namespaced so it can't collide with built-ins. */
    val checklistId: String get() = "routine:$id"

    fun appliesOn(date: LocalDate): Boolean = enabled && when (val s = schedule) {
        is RoutineSchedule.Daily -> true
        is RoutineSchedule.Weekly -> date.dayOfWeek.name in s.days
        is RoutineSchedule.Window -> date.dayOfWeek.name in s.days
        is RoutineSchedule.PerWeek -> s.days.isEmpty() || date.dayOfWeek.name in s.days
    }

    /** Sort key / primary reminder time. */
    val startTime: LocalTime get() = LocalTime.parse(
        when (val s = schedule) {
            is RoutineSchedule.Daily -> s.time
            is RoutineSchedule.Weekly -> s.time
            is RoutineSchedule.Window -> s.start
            is RoutineSchedule.PerWeek -> s.time
        }
    )

    val timeHint: String get() = when (val s = schedule) {
        is RoutineSchedule.Window -> "${s.start}–${s.end}"
        is RoutineSchedule.PerWeek -> if (s.days.isEmpty()) "${s.times}×/wk" else s.time
        else -> startTime.toString()
    }

    fun describe(): String = when (val s = schedule) {
        is RoutineSchedule.Daily -> "Daily at ${s.time}"
        is RoutineSchedule.Weekly -> "${shortDays(s.days)} at ${s.time}"
        is RoutineSchedule.Window -> "${shortDays(s.days)}, any time ${s.start}–${s.end}"
        is RoutineSchedule.PerWeek ->
            if (s.days.isEmpty()) "${s.times}× a week, days not chosen yet"
            else "${s.times}× a week: ${shortDays(s.days)}"
    }
}

private fun shortDays(days: List<String>): String =
    DayOfWeek.entries.filter { it.name in days }.joinToString(", ") {
        it.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    }

val ROUTINE_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Initial routine, seeded into the `routine_items` table once. Edit there, not here. */
val DEFAULT_ROUTINE = listOf(
    RoutineItem(
        id = "weigh_in",
        label = "\u2696\uFE0F  Morning weigh-in",
        schedule = RoutineSchedule.Daily("07:00"),
        detail = "After the bathroom, before eating",
    ),
    RoutineItem(
        id = "row_z2",
        label = "\uD83D\uDEA3  Easy zone-2 row",
        schedule = RoutineSchedule.Weekly(listOf("MONDAY", "FRIDAY"), "07:00"),
        detail = "Keep HR 100–117 bpm",
    ),
    RoutineItem(
        id = "row_wed",
        label = "\uD83D\uDEA3  Row",
        schedule = RoutineSchedule.Window(listOf("WEDNESDAY"), "14:00", "17:00"),
        detail = "Mid-afternoon, whenever suits",
    ),
    RoutineItem(
        id = "strength",
        label = "\uD83C\uDFCB\uFE0F  Strength session",
        schedule = RoutineSchedule.PerWeek(times = 2),
    ),
    RoutineItem(
        id = "walk",
        label = "\uD83D\uDEB6  Walk after dinner",
        schedule = RoutineSchedule.Daily("18:00"),
    ),
)
