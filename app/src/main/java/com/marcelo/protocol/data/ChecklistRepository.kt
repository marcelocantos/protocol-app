package com.marcelo.protocol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ChecklistRepository(private val dataStore: DataStore<Preferences>) {

    fun completedItems(date: LocalDate): Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PrefsKeys.checklistKey(date)] ?: emptySet()
    }

    suspend fun toggleItem(date: LocalDate, itemId: String) {
        dataStore.edit { prefs ->
            val key = PrefsKeys.checklistKey(date)
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (itemId in current) current.remove(itemId) else current.add(itemId)
            prefs[key] = current
        }
    }

    fun gymCount(date: LocalDate): Flow<Int> = dataStore.data.map { prefs ->
        prefs[PrefsKeys.gymCountKey(date)] ?: 0
    }

    suspend fun setGymCount(date: LocalDate, count: Int) {
        dataStore.edit { prefs ->
            prefs[PrefsKeys.gymCountKey(date)] = count.coerceAtLeast(0)
        }
    }

}
