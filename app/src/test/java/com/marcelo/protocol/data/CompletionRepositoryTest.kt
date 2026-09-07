// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

private class FakeCompletionStore : CompletionStore {
    val rows = mutableMapOf<Pair<LocalDate, String>, LocalDateTime>()

    override suspend fun completedItems(date: LocalDate) =
        rows.filterKeys { it.first == date }.mapKeys { it.key.second }

    override suspend fun completedItemsBatch(dates: List<LocalDate>) =
        dates.associateWith { completedItemsFor(it) }

    private fun completedItemsFor(date: LocalDate) =
        rows.filterKeys { it.first == date }.mapKeys { it.key.second }

    override suspend fun gymCountForWeek(date: LocalDate) =
        rows.keys.count { it.second == "gym" }

    override suspend fun setCompletion(date: LocalDate, itemId: String, timestamp: LocalDateTime) {
        rows[date to itemId] = timestamp
    }

    override suspend fun removeCompletion(date: LocalDate, itemId: String) {
        rows.remove(date to itemId)
    }
}

class CompletionRepositoryTest {

    private val date = LocalDate.of(2026, 9, 7)
    private val noon = LocalDateTime.of(2026, 9, 7, 12, 0)

    /**
     * The Week screen is a second reader of completions Today writes. It must
     * see the write without being recreated.
     */
    @Test
    fun anIndependentReaderSeesAWriteFromAnotherScreen() = runBlocking {
        val repo = CompletionRepository(FakeCompletionStore())
        val observed = mutableListOf<Int>()
        val collector = launch(Dispatchers.Unconfined) {
            repo.changes.collect { observed += repo.gymCountForWeek(date) }
        }

        repo.setCompletion(date, "gym", noon)
        repo.removeCompletion(date, "gym")

        collector.cancel()
        assertEquals(listOf(1, 0), observed)
    }
}
