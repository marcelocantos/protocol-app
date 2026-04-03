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

    suspend fun pruneOldEntries() {
        val cutoff = LocalDate.now().minusDays(14)
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { key ->
                val name = key.name
                if (name.startsWith("checklist_")) {
                    val dateStr = name.removePrefix("checklist_")
                    runCatching { LocalDate.parse(dateStr) < cutoff }.getOrDefault(false)
                } else false
            }
            toRemove.forEach { prefs.remove(it) }
        }
    }
}
