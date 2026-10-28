// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.nag

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** A capability nags need that the user hasn't granted, with where to fix it. */
data class MissingCapability(val label: String, val intent: Intent)

object NagPermissions {
    fun missing(context: Context): List<MissingCapability> {
        val pkg = Uri.parse("package:${context.packageName}")
        val out = mutableListOf<MissingCapability>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            out += MissingCapability(
                "Notifications are off — reminders can't show",
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        if (!NagEngine.canScheduleExact(context)) {
            @Suppress("InlinedApi") // canScheduleExact is only false on API 31+.
            out += MissingCapability(
                "Exact alarms not allowed — reminders may be late",
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            out += MissingCapability(
                "Full-screen alerts not allowed — alarm items show as banners",
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
            )
        }
        return out
    }
}
