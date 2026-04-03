package com.marcelo.protocol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class PlanningRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private fun weekPlanKey(weekStart: LocalDate) =
            stringPreferencesKey("week_plan_${weekStart}")
    }

    /** Get the Monday of the week containing [date]. */
    fun weekStartFor(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Plan for the week starting at [weekStart] (Monday). Map of day-of-week to plan. */
    fun weekPlan(weekStart: LocalDate): Flow<Map<DayOfWeek, OfficeDayPlan>> =
        dataStore.data.map { prefs ->
            val json = prefs[weekPlanKey(weekStart)]
            if (json != null) {
                val raw: Map<String, OfficeDayPlan> = Json.decodeFromString(json)
                raw.mapKeys { DayOfWeek.valueOf(it.key) }
            } else {
                emptyMap()
            }
        }

    suspend fun updateDay(weekStart: LocalDate, day: DayOfWeek, plan: OfficeDayPlan) {
        dataStore.edit { prefs ->
            val key = weekPlanKey(weekStart)
            val current = prefs[key]?.let { json ->
                val raw: Map<String, OfficeDayPlan> = Json.decodeFromString(json)
                raw.mapKeys { DayOfWeek.valueOf(it.key) }.toMutableMap()
            } ?: mutableMapOf()
            current[day] = plan
            prefs[key] = Json.encodeToString(current.mapKeys { it.key.name })
        }
    }

    /** Count of days needing attention: undecided, or office without booking. */
    fun unplannedDays(weekStart: LocalDate, schedule: Map<DayOfWeek, com.marcelo.protocol.model.DayType>): Flow<Int> =
        weekPlan(weekStart).map { plans ->
            val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            weekdays.count { day ->
                val plan = plans[day]
                val effectiveType = plan?.dayType ?: schedule[day] ?: com.marcelo.protocol.model.DayType.REST
                // Undecided days, or office days without a booking.
                plan?.dayType == null ||
                    (effectiveType == com.marcelo.protocol.model.DayType.OFFICE &&
                        plan.parkingStatus != ParkingStatus.BOOKED)
            }
        }
}
