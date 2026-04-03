package com.marcelo.protocol

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.marcelo.protocol.notification.NotificationHelper

private val Context.protocolDataStore: DataStore<Preferences> by preferencesDataStore(name = "protocol_prefs")

class ProtocolApp : Application() {

    val dataStore: DataStore<Preferences> by lazy { protocolDataStore }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
