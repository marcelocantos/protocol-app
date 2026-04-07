// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.GYM_WEEKLY_TARGET
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekScreen(vm: WeekViewModel) {
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val gymCount by vm.gymCount.collectAsStateWithLifecycle()
    val weekSummary by vm.weekSummary.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Weekly Schedule", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }

        // Schedule config
        items(DayOfWeek.entries) { day ->
            val current = schedule[day] ?: DayType.REST
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayType.entries.forEach { type ->
                        FilterChip(
                            selected = current == type,
                            onClick = { vm.setDayType(day, type) },
                            label = { Text("${type.emoji} ${type.label}") },
                        )
                    }
                }
            }
        }

        // Gym tracker
        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "\uD83C\uDFCB\uFE0F Gym This Week",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "$gymCount / $GYM_WEEKLY_TARGET sessions",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    CircularProgressIndicator(
                        progress = { (gymCount.toFloat() / GYM_WEEKLY_TARGET).coerceAtMost(1f) },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 6.dp,
                    )
                }
            }
        }

        // Past 7 days summary
        item {
            Spacer(Modifier.height(16.dp))
            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }

        items(weekSummary) { day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${day.dayType.emoji}  ${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.date.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val pct = (day.completionFraction * 100).toInt()
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (pct == 100) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        pct == 100 -> MaterialTheme.colorScheme.primary
                        pct >= 50 -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}
