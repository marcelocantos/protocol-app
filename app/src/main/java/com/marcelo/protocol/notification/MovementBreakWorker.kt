// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalTime

class MovementBreakWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalTime.now()
        val workStart = LocalTime.of(9, 0)
        val workEnd = LocalTime.of(17, 0)

        if (now.isAfter(workStart) && now.isBefore(workEnd)) {
            NotificationHelper.notify(
                applicationContext,
                NotificationHelper.CHANNEL_MOVEMENT,
                "\uD83E\uDDD1\u200D\uD83E\uDD3C Stand up and move!",
                "Time for a 2-minute stretch or walk. Clear that cortisol.",
                1001,
            )
        }

        // Schedule the next one.
        NotificationScheduler.scheduleMovementFollowUp(applicationContext)

        return Result.success()
    }
}
