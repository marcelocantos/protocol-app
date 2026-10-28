// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.PlanningRepository
import com.marcelo.protocol.data.ScheduleRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.ParkingStatus

private fun DayOfWeek.short() = getDisplayName(TextStyle.SHORT, Locale.getDefault())

class PlanningReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ProtocolApp
        val planningRepo = PlanningRepository(app.db)
        val scheduleRepo = ScheduleRepository(app.db)

        val today = LocalDate.now()
        val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val schedule = scheduleRepo.schedule.first()
        val plans = planningRepo.weekPlan(nextMonday).first()
        val weekdays = DayOfWeek.entries.take(5)
        val undecided = weekdays.filter { plans[it]?.dayType == null }
        val toBook = weekdays.filter {
            plans[it]?.dayType == DayType.OFFICE && plans[it]?.parkingStatus != ParkingStatus.BOOKED
        }
        // Undecided days that are usually office days will need parking too.
        val likelyOffice = undecided.filter { schedule[it] == DayType.OFFICE }

        if (undecided.isNotEmpty() || toBook.isNotEmpty()) {
            val urgency = when (today.dayOfWeek) {
                DayOfWeek.SATURDAY -> "Book early for cheaper parking."
                DayOfWeek.SUNDAY -> "\u26A0\uFE0F Last chance before Monday."
                else -> ""
            }
            val (title, body) = if (undecided.isNotEmpty()) {
                "Confirm next week" to buildString {
                    append("${undecided.size} day${if (undecided.size > 1) "s" else ""} undecided")
                    if (likelyOffice.isNotEmpty() || toBook.isNotEmpty()) append(", then book parking")
                    append(". $urgency")
                }
            } else {
                "\uD83C\uDD7F\uFE0F Book parking: ${toBook.joinToString(", ") { it.short() }}" to "$urgency Tap to book."
            }
            NotificationHelper.notify(
                applicationContext,
                NotificationHelper.CHANNEL_PLANNING,
                title, body.trim(), 1004,
                openTab = "plan",
            )
        }

        NotificationScheduler.schedulePlanningReminders(applicationContext)

        return Result.success()
    }
}
