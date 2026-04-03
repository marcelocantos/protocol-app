package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WindDownWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.notify(
            applicationContext,
            NotificationHelper.CHANNEL_WIND_DOWN,
            "\uD83D\uDCF5 Time to wind down",
            "Screens off. Dim the lights. Coding stops now.",
            1002,
        )

        // Schedule tomorrow's.
        NotificationScheduler.reschedule(applicationContext)

        return Result.success()
    }
}
