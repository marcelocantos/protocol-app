// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.nag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.marcelo.protocol.ui.theme.ProtocolTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alarm-style full-screen alert shown over the lock screen for ALARM-level items. */
class NagAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_CAN_SNOOZE = "can_snooze"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val date = intent.getStringExtra(NagEngine.EXTRA_DATE)?.let(LocalDate::parse)
        val item = intent.getStringExtra(NagEngine.EXTRA_ITEM)
        if (date == null || item == null) { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty()
        val canSnooze = intent.getBooleanExtra(EXTRA_CAN_SNOOZE, false)

        fun act(block: suspend () -> Unit) {
            lifecycleScope.launch { block(); finish() }
        }

        setContent {
            ProtocolTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(subtitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = { act { NagEngine.done(applicationContext, date, item) } },
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        ) { Text("Done", style = MaterialTheme.typography.headlineSmall) }
                        Spacer(Modifier.height(16.dp))
                        if (canSnooze) {
                            OutlinedButton(
                                onClick = { act { NagEngine.snooze(applicationContext, date, item) } },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Snooze ${NagPolicy.SNOOZE_MINUTES} min") }
                        } else {
                            Text("No snoozes left", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { act { NagEngine.skip(applicationContext, date, item) } }) {
                            Text("Skip today")
                        }
                    }
                }
            }
        }
    }
}
