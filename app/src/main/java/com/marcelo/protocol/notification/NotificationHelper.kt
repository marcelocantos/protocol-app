// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marcelo.protocol.MainActivity

object NotificationHelper {

    const val CHANNEL_MOVEMENT = "movement_break"
    const val CHANNEL_WIND_DOWN = "wind_down"
    const val CHANNEL_BEDTIME = "bedtime"
    const val CHANNEL_PLANNING = "planning"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MOVEMENT, "Movement Breaks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "90-minute movement reminders during work hours"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WIND_DOWN, "Wind Down", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "10pm screen-off reminder"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BEDTIME, "Bedtime", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "11pm bedtime reminder"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PLANNING, "Office Planning", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Weekend reminders to plan and book parking for office days"
            }
        )
    }

    fun notify(context: Context, channelId: String, title: String, body: String, notifId: Int) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Permission not granted — silently ignore.
        }
    }
}
