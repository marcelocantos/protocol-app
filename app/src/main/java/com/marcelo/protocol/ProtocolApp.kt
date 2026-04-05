package com.marcelo.protocol

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.marcelo.protocol.data.DataStoreMigration
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Context.protocolDataStore: DataStore<Preferences> by preferencesDataStore(name = "protocol_prefs")

class ProtocolApp : Application() {

    val dataStore: DataStore<Preferences> by lazy { protocolDataStore }
    val db: ProtocolDatabase by lazy { ProtocolDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            DataStoreMigration.migrateIfNeeded(dataStore, db)
        }
    }
}
