// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDate
import java.time.LocalDateTime

/** The completion reads and writes [CompletionRepository] needs from storage. */
interface CompletionStore {
    suspend fun completedItems(date: LocalDate): Map<String, LocalDateTime>
    suspend fun completedItemsBatch(dates: List<LocalDate>): Map<LocalDate, Map<String, LocalDateTime>>
    suspend fun gymCountForWeek(date: LocalDate): Int
    suspend fun setCompletion(date: LocalDate, itemId: String, timestamp: LocalDateTime)
    suspend fun removeCompletion(date: LocalDate, itemId: String)
}

/**
 * Checklist completions plus the refresh signal that tells every reader a
 * completion changed.
 *
 * Today used to write completions straight to the database behind its own
 * private refresh flow, so the Week screen's summary stayed stale until the
 * schedule changed or the process died. A single instance of this repository
 * (on `ProtocolApp`) is the only path for completion writes.
 */
class CompletionRepository(private val store: CompletionStore) {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after every completion write. */
    val changes: Flow<Unit> = _changes.asSharedFlow()

    suspend fun completedItems(date: LocalDate) = store.completedItems(date)

    suspend fun completedItemsBatch(dates: List<LocalDate>) = store.completedItemsBatch(dates)

    suspend fun gymCountForWeek(date: LocalDate) = store.gymCountForWeek(date)

    suspend fun setCompletion(date: LocalDate, itemId: String, timestamp: LocalDateTime) {
        store.setCompletion(date, itemId, timestamp)
        _changes.emit(Unit)
    }

    suspend fun removeCompletion(date: LocalDate, itemId: String) {
        store.removeCompletion(date, itemId)
        _changes.emit(Unit)
    }
}
