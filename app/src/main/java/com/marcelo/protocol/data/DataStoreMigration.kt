package com.marcelo.protocol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One-time migration from DataStore to SQLite.
 * Reads all known keys from the old store and inserts into the database.
 * Sets a flag so it only runs once.
 */
object DataStoreMigration {

    private val MIGRATED_KEY = booleanPreferencesKey("migrated_to_sqlite")

    suspend fun migrateIfNeeded(dataStore: DataStore<Preferences>, db: ProtocolDatabase) {
        val prefs = dataStore.data.first()
        if (prefs[MIGRATED_KEY] == true) return

        // Migrate checklist completions.
        // Keys are "checklist_YYYY-MM-DD" -> Set<String> of item IDs.
        // Old store didn't record times; use midnight on that date.
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith("checklist_") && value is Set<*>) {
                val dateStr = name.removePrefix("checklist_")
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                @Suppress("UNCHECKED_CAST")
                val items = value as Set<String>
                items.forEach { itemId ->
                    db.setCompletion(date, itemId, date.atStartOfDay())
                }
            }
        }

        // Migrate schedule.
        val scheduleJson = prefs[PrefsKeys.SCHEDULE_JSON]
        if (scheduleJson != null) {
            val raw: Map<String, DayType> = Json.decodeFromString(scheduleJson)
            raw.forEach { (dayName, type) ->
                db.setScheduleDay(DayOfWeek.valueOf(dayName), type)
            }
        }

        // Migrate week plans.
        // Keys are "week_plan_YYYY-MM-DD" -> JSON map of day name to OfficeDayPlan.
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith("week_plan_") && value is String) {
                val dateStr = name.removePrefix("week_plan_")
                val weekStart = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                val raw: Map<String, OfficeDayPlan> = runCatching {
                    Json.decodeFromString<Map<String, OfficeDayPlan>>(value)
                }.getOrNull() ?: return@forEach
                raw.forEach { (dayName, plan) ->
                    db.setWeekPlanDay(weekStart, DayOfWeek.valueOf(dayName), plan)
                }
            }
        }

        // Migrate selected tab.
        val tabKey = prefs.asMap().keys.find { it.name == "selected_tab" }
        if (tabKey != null) {
            val tabValue = prefs[tabKey]
            if (tabValue is Int) {
                db.setSetting("selected_tab", tabValue.toString())
            }
        }

        // Mark as migrated.
        dataStore.edit { it[MIGRATED_KEY] = true }
    }
}
