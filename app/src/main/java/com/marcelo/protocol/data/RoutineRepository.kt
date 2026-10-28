// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import com.marcelo.protocol.model.ChecklistItem
import com.marcelo.protocol.model.RoutineItem
import com.marcelo.protocol.model.RoutineSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.LocalDate

class RoutineRepository(private val db: ProtocolDatabase) {

    companion object {
        // Shared so edits on the Week screen refresh Today's checklist.
        private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    }

    val items: Flow<List<RoutineItem>> = merge(flow { emit(Unit) }, refresh).map { db.getRoutine() }

    suspend fun update(item: RoutineItem) {
        db.updateRoutineItem(item)
        refresh.emit(Unit)
    }

    /**
     * Checklist entries contributed by the routine on [date]. `per_week` items
     * show their weekly progress and drop off once the target is met (unless
     * done on [date] itself, so a tick never vanishes).
     */
    suspend fun checklistFor(date: LocalDate, completedToday: Set<String>): List<ChecklistItem> =
        db.getRoutine().filter { it.appliesOn(date) }.mapNotNull { item ->
            val s = item.schedule
            if (s is RoutineSchedule.PerWeek) {
                val done = db.countForWeek(item.checklistId, date)
                val doneToday = item.checklistId in completedToday
                if (done >= s.times && !doneToday) return@mapNotNull null
                ChecklistItem(item.checklistId, "${item.label} ($done/${s.times} this week)", item.timeHint, item.detail)
            } else {
                ChecklistItem(item.checklistId, item.label, item.timeHint, item.detail)
            }
        }

    /** Sort key that puts windows at their start and "N×/wk" items at their nudge time. */
    suspend fun startTimes(): Map<String, java.time.LocalTime> =
        db.getRoutine().associate { it.checklistId to it.startTime }
}
