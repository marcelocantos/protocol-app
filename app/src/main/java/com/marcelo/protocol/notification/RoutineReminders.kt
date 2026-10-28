// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.model.RoutineItem
import com.marcelo.protocol.model.RoutineNag
import com.marcelo.protocol.model.RoutineSchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One reminder moment for a routine item. */
data class RoutineEvent(val item: RoutineItem, val date: LocalDate, val at: LocalDateTime, val message: String)

/**
 * Derives reminder moments from the routine data and drives a single
 * self-rescheduling worker. Escalation/nagging is layered on separately
 * (see the naggy-reminders branch); this provides the per-item timing rules.
 */
object RoutineReminders {

    private const val WORK = "protocol_routine"
    private const val LAST_CHECK = "routine_last_check"
    private val STALE = Duration.ofHours(2)

    fun eventsFor(item: RoutineItem, date: LocalDate): List<RoutineEvent> {
        if (item.nag == RoutineNag.NONE || !item.appliesOn(date)) return emptyList()
        return when (val s = item.schedule) {
            is RoutineSchedule.Daily, is RoutineSchedule.Weekly, is RoutineSchedule.PerWeek ->
                listOf(RoutineEvent(item, date, date.atTime(item.startTime), item.detail ?: "Time for this."))
            is RoutineSchedule.Window -> {
                val end = LocalTime.parse(s.end)
                listOf(
                    RoutineEvent(item, date, date.atTime(LocalTime.parse(s.start)), "Window open until ${s.end}."),
                    RoutineEvent(item, date, date.atTime(end.minusHours(1)), "Last hour — window closes at ${s.end}."),
                )
            }
        }
    }

    /**
     * Whether [event] should actually notify: not done today, and for
     * undecided-days `per_week` items, only when behind pace (sessions left
     * >= days left in the week, counting today).
     */
    suspend fun shouldFire(db: ProtocolDatabase, event: RoutineEvent): Boolean {
        val id = event.item.checklistId
        if (id in db.completedItems(event.date)) return false
        val s = event.item.schedule
        if (s is RoutineSchedule.PerWeek) {
            val remaining = s.times - db.countForWeek(id, event.date)
            if (remaining <= 0) return false
            if (s.days.isEmpty()) {
                val daysLeft = DayOfWeek.SUNDAY.value - event.date.dayOfWeek.value + 1
                return remaining >= daysLeft
            }
        }
        return true
    }

    suspend fun schedule(context: Context) {
        val db = (context.applicationContext as ProtocolApp).db
        val now = LocalDateTime.now()
        val routine = db.getRoutine()
        val next = (0L..7L).asSequence()
            .flatMap { d -> routine.flatMap { eventsFor(it, now.toLocalDate().plusDays(d)) } }
            .map { it.at }
            .filter { it.isAfter(now) }
            .minOrNull() ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RoutineReminderWorker>()
                .setInitialDelay(Duration.between(now, next))
                .build(),
        )
    }

    /** Fire every event in (last check, now], skipping stale ones; then reschedule. */
    suspend fun runDue(context: Context) {
        val db = (context.applicationContext as ProtocolApp).db
        val now = LocalDateTime.now()
        val last = db.getSetting(LAST_CHECK)?.let(LocalDateTime::parse) ?: now.minusMinutes(1)
        val from = maxOf(last, now.minus(STALE))
        val routine = db.getRoutine()
        var date = from.toLocalDate()
        while (!date.isAfter(now.toLocalDate())) {
            for (e in routine.flatMap { eventsFor(it, date) }) {
                if (e.at.isAfter(from) && !e.at.isAfter(now) && shouldFire(db, e)) {
                    NotificationHelper.notify(
                        context, NotificationHelper.CHANNEL_ROUTINE,
                        e.item.label.replace(Regex("\\s{2,}"), " ").trim(), e.message,
                        "routine/${e.item.id}/${e.date}".hashCode(),
                    )
                }
            }
            date = date.plusDays(1)
        }
        db.setSetting(LAST_CHECK, now.truncatedTo(ChronoUnit.SECONDS).toString())
        schedule(context)
    }
}

class RoutineReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RoutineReminders.runDue(applicationContext)
        return Result.success()
    }
}
