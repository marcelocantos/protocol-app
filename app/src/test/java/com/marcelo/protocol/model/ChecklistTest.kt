// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** GYM_ITEM_ID is the item the weekly gym count keys off. */
private const val GYM_ITEM_ID = "gym"

class ChecklistTest {

    @Test
    fun everyDayTypeOffersTheGymItem() {
        // The weekly gym target counts completions of this exact id, so a
        // day type that omits it silently makes the target unreachable.
        for (type in DayType.entries) {
            val ids = checklistFor(type).map { it.id }
            assertTrue("$type checklist lacks '$GYM_ITEM_ID': $ids", GYM_ITEM_ID in ids)
        }
    }

    @Test
    fun itemIdsAreUniqueWithinADay() {
        // checklist_completions is keyed on (date, item_id): a duplicate id
        // within one day would make two rows collide.
        for (type in DayType.entries) {
            val ids = checklistFor(type).map { it.id }
            assertEquals("$type has duplicate item ids: $ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun itemsAreOrderedByTimeOfDay() {
        for (type in DayType.entries) {
            val times = checklistFor(type).map { LocalTime.parse(it.timeHint) }
            assertEquals("$type checklist is not in chronological order: $times", times.sorted(), times)
        }
    }

    @Test
    fun everyItemHasANonBlankIdLabelAndParsableTime() {
        for (type in DayType.entries) {
            for (item in checklistFor(type)) {
                assertTrue("$type has an item with a blank id", item.id.isNotBlank())
                assertTrue("$type item ${item.id} has a blank label", item.label.isNotBlank())
                LocalTime.parse(item.timeHint) // throws if malformed
            }
        }
    }

    @Test
    fun everyDayTypeHasABedtimeItem() {
        for (type in DayType.entries) {
            val ids = checklistFor(type).map { it.id }
            assertTrue("$type checklist lacks 'bed': $ids", "bed" in ids)
        }
    }
}
