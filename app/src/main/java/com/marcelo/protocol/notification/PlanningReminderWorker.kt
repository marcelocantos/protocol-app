package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.PlanningRepository
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.DayType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

class PlanningReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ProtocolApp
        val planningRepo = PlanningRepository(app.dataStore)
        val scheduleRepo = ScheduleRepository(app.dataStore)

        val today = LocalDate.now()
        val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val schedule = scheduleRepo.schedule.first()
        val unplanned = planningRepo.unplannedDays(nextMonday, schedule).first()

        if (unplanned > 0) {
            val urgency = when (today.dayOfWeek) {
                DayOfWeek.SATURDAY -> "Book early for cheaper parking!"
                DayOfWeek.SUNDAY -> "\u26A0\uFE0F Last chance before Monday!"
                else -> "Plan your office days"
            }

            NotificationHelper.notify(
                applicationContext,
                NotificationHelper.CHANNEL_PLANNING,
                "\u2753 $unplanned day${if (unplanned > 1) "s" else ""} need attention",
                "$urgency Tap to plan and book parking.",
                1004,
            )
        }

        // Re-schedule for the next reminder.
        NotificationScheduler.schedulePlanningReminders(applicationContext)

        return Result.success()
    }
}
