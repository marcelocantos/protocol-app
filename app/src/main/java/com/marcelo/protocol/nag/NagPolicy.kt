// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.nag

import java.time.Duration

/**
 * Escalation policy for nagging reminders. Kept in one place so the numbers
 * are easy to tune.
 */
object NagPolicy {
    /**
     * Minutes to wait before the next re-alert, indexed by how many times the
     * item has already fired (1st fire -> 5 min, 2nd -> 10, ...). The last
     * entry repeats until the item is done, skipped, or [GIVE_UP_AFTER].
     */
    val REALERT_MINUTES = listOf(5L, 10L, 15L, 30L)

    const val SNOOZE_MINUTES = 10L

    /** Snoozes allowed per item per day. After this, the Snooze action is removed. */
    const val MAX_SNOOZES = 3

    /** Stop nagging this long after the item's due time. */
    val GIVE_UP_AFTER: Duration = Duration.ofHours(3)

    fun realertDelay(fireCount: Int): Duration {
        val i = (fireCount - 1).coerceIn(0, REALERT_MINUTES.lastIndex)
        return Duration.ofMinutes(REALERT_MINUTES[i])
    }
}
