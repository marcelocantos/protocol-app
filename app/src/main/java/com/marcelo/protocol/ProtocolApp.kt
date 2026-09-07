// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol

import android.app.Application
import com.marcelo.protocol.data.PlanningRepository
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.notification.NotificationHelper

class ProtocolApp : Application() {

    val db: ProtocolDatabase by lazy { ProtocolDatabase(this) }

    // One instance each: every repository carries a refresh signal, and
    // per-ViewModel copies cannot see each other's writes.
    val scheduleRepo: ScheduleRepository by lazy { ScheduleRepository(db) }
    val planningRepo: PlanningRepository by lazy { PlanningRepository(db) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
