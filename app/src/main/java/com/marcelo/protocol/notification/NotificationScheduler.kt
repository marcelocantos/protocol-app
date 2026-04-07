// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

object NotificationScheduler {

    private const val WORK_MOVEMENT = "protocol_movement"
    private const val WORK_WIND_DOWN = "protocol_wind_down"
    private const val WORK_BEDTIME = "protocol_bedtime"
    private const val WORK_PLANNING = "protocol_planning"

    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniqueWork(
            WORK_MOVEMENT,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MovementBreakWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(9, 0)))
                .build(),
        )
        wm.enqueueUniqueWork(
            WORK_WIND_DOWN,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WindDownWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(22, 0)))
                .build(),
        )
        wm.enqueueUniqueWork(
            WORK_BEDTIME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BedtimeWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(23, 0)))
                .build(),
        )

        schedulePlanningReminders(context, ExistingWorkPolicy.KEEP)
    }

    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniqueWork(
            WORK_MOVEMENT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MovementBreakWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(9, 0)))
                .build(),
        )
        wm.enqueueUniqueWork(
            WORK_WIND_DOWN,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WindDownWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(22, 0)))
                .build(),
        )
        wm.enqueueUniqueWork(
            WORK_BEDTIME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BedtimeWorker>()
                .setInitialDelay(delayUntil(LocalTime.of(23, 0)))
                .build(),
        )
    }

    /**
     * Schedule planning reminders for the weekend.
     * Saturday 10am: "Book early for cheaper parking!"
     * Saturday 5pm: follow-up if still unbooked
     * Sunday 10am: "Last chance before Monday!"
     */
    fun schedulePlanningReminders(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Find next Saturday and Sunday.
        val nextSat = if (today.dayOfWeek == DayOfWeek.SATURDAY && now.toLocalTime().isBefore(LocalTime.of(17, 0)))
            today
        else
            today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val nextSun = nextSat.plusDays(1)

        // Pick the next upcoming planning reminder time.
        val candidates = listOf(
            nextSat.atTime(10, 0),
            nextSat.atTime(17, 0),
            nextSun.atTime(10, 0),
        )
        val nextReminder = candidates.firstOrNull { it.isAfter(now) }
            ?: nextSat.plusWeeks(1).atTime(10, 0) // All passed, schedule next week.

        val delay = Duration.between(now, nextReminder)
        wm.enqueueUniqueWork(
            WORK_PLANNING,
            policy,
            OneTimeWorkRequestBuilder<PlanningReminderWorker>()
                .setInitialDelay(delay)
                .build(),
        )
    }

    fun scheduleMovementFollowUp(context: Context) {
        val now = LocalTime.now()
        val endOfWork = LocalTime.of(17, 0)

        if (now.isBefore(endOfWork)) {
            val nextTime = now.plusMinutes(90)
            if (nextTime.isBefore(endOfWork)) {
                val delay = Duration.between(LocalTime.now(), nextTime)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_MOVEMENT,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<MovementBreakWorker>()
                        .setInitialDelay(delay)
                        .build(),
                )
            }
        }
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var target = LocalDate.now().atTime(time)
        if (target.isBefore(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }
}
