// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BedtimeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.notify(
            applicationContext,
            NotificationHelper.CHANNEL_BEDTIME,
            "\uD83D\uDECF\uFE0F Bedtime",
            "In bed. Now. The vibe code will be there tomorrow.",
            1003,
        )

        // Schedule tomorrow's.
        NotificationScheduler.reschedule(applicationContext)

        return Result.success()
    }
}
