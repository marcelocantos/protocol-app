// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.model

const val GYM_WEEKLY_TARGET = 3

/**
 * How insistently an item reminds you when it falls due.
 *
 * - [NONE]: no reminder (the item just sits in the checklist).
 * - [NAG]: persistent notification that re-alerts at increasing intervals
 *   until done, skipped, or the give-up window passes.
 * - [ALARM]: like [NAG] but alarm-style: alarm channel, alarm-clock
 *   scheduling (Doze-exempt), and a full-screen alert over the lock screen.
 */
enum class NagLevel { NONE, NAG, ALARM }

data class ChecklistItem(
    val id: String,
    val label: String,
    val timeHint: String,
    val nag: NagLevel = NagLevel.NONE,
) {
    /** Due time parsed from [timeHint] ("HH:mm"), or null if unparseable. */
    val dueTime: java.time.LocalTime?
        get() = runCatching { java.time.LocalTime.parse(timeHint) }.getOrNull()
}
