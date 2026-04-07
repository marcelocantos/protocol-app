// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol

import android.app.Application
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.notification.NotificationHelper

class ProtocolApp : Application() {

    val db: ProtocolDatabase by lazy { ProtocolDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
