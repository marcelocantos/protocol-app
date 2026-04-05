package com.marcelo.protocol.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

object PrefsKeys {
    val SCHEDULE_JSON = stringPreferencesKey("schedule_json")

    fun checklistKey(date: LocalDate) =
        stringSetPreferencesKey("checklist_$date")

    /** New key: stores JSON map of itemId -> "HH:mm" completion times. */
    fun checklistTimesKey(date: LocalDate) =
        stringPreferencesKey("checklist_times_$date")

    fun gymCountKey(date: LocalDate) =
        intPreferencesKey("gym_${isoWeekId(date)}")

    fun isoWeekId(date: LocalDate): String {
        val wf = WeekFields.of(Locale.getDefault())
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }
}
