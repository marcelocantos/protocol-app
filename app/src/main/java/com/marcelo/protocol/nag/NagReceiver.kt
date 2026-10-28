// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.nag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles nag alarms, notification actions, and system events that invalidate alarms. */
class NagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val date = intent.getStringExtra(NagEngine.EXTRA_DATE)?.let(LocalDate::parse)
        val item = intent.getStringExtra(NagEngine.EXTRA_ITEM)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NagEngine.ACTION_FIRE -> if (date != null && item != null) NagEngine.fire(app, date, item)
                    NagEngine.ACTION_DONE -> if (date != null && item != null) NagEngine.done(app, date, item)
                    NagEngine.ACTION_SNOOZE -> if (date != null && item != null) NagEngine.snooze(app, date, item)
                    NagEngine.ACTION_SKIP -> if (date != null && item != null) NagEngine.skip(app, date, item)
                    NagEngine.ACTION_DISMISSED -> if (date != null && item != null) NagEngine.dismissed(app, date, item)
                    // REFRESH, exact-alarm permission granted, clock/timezone change, app update.
                    else -> NagEngine.rescheduleAll(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
