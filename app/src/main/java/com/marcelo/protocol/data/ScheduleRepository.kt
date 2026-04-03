package com.marcelo.protocol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.marcelo.protocol.model.DayType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val DEFAULT_SCHEDULE = mapOf(
            DayOfWeek.MONDAY to DayType.OFFICE,
            DayOfWeek.TUESDAY to DayType.WFH,
            DayOfWeek.WEDNESDAY to DayType.OFFICE,
            DayOfWeek.THURSDAY to DayType.WFH,
            DayOfWeek.FRIDAY to DayType.WFH,
            DayOfWeek.SATURDAY to DayType.REST,
            DayOfWeek.SUNDAY to DayType.REST,
        )
    }

    val schedule: Flow<Map<DayOfWeek, DayType>> = dataStore.data.map { prefs ->
        val json = prefs[PrefsKeys.SCHEDULE_JSON]
        if (json != null) {
            val raw: Map<String, DayType> = Json.decodeFromString(json)
            raw.mapKeys { DayOfWeek.valueOf(it.key) }
        } else {
            DEFAULT_SCHEDULE
        }
    }

    fun dayTypeFor(date: LocalDate): Flow<DayType> = schedule.map { s ->
        s[date.dayOfWeek] ?: DayType.REST
    }

    suspend fun setDayType(day: DayOfWeek, type: DayType) {
        dataStore.edit { prefs ->
            val current = prefs[PrefsKeys.SCHEDULE_JSON]?.let { json ->
                val raw: Map<String, DayType> = Json.decodeFromString(json)
                raw.mapKeys { DayOfWeek.valueOf(it.key) }.toMutableMap()
            } ?: DEFAULT_SCHEDULE.toMutableMap()
            current[day] = type
            prefs[PrefsKeys.SCHEDULE_JSON] =
                Json.encodeToString(current.mapKeys { it.key.name })
        }
    }
}
