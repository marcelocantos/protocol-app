// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.plan

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.ParkingStatus
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class TypeOption(val type: DayType?, val label: String, val emoji: String)

private val TYPE_OPTIONS = listOf(
    TypeOption(null, "", "\u2753"),
    TypeOption(DayType.OFFICE, DayType.OFFICE.label, DayType.OFFICE.emoji),
    TypeOption(DayType.WFH, DayType.WFH.label, DayType.WFH.emoji),
    TypeOption(DayType.REST, DayType.REST.label, DayType.REST.emoji),
)

@Composable
fun PlanScreen(vm: PlanViewModel) {
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Plan Office Days", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Week of ${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.viewThisWeek() }) { Text("This Week") }
                Button(onClick = { vm.viewNextWeek() }) { Text("Next Week") }
            }
            Spacer(Modifier.height(12.dp))
        }

        items(days, key = { it.day }) { row ->
            PlanDayCard(
                row = row,
                onSetType = { vm.setPlannedType(row.day, it) },
                onSetParking = { vm.setParkingStatus(row.day, it) },
                onOpenWilson = {
                    val wilsonPackage = "au.com.wilsonone"
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(wilsonPackage)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$wilsonPackage"),
                            )
                        )
                    }
                },
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PlanDayCard(
    row: PlanDayRow,
    onSetType: (DayType?) -> Unit,
    onSetParking: (ParkingStatus) -> Unit,
    onOpenWilson: () -> Unit,
) {
    val isOffice = row.effectiveType == DayType.OFFICE
    val bg = when {
        !row.isDecided -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        isOffice -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            // Day label + type selector on one line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${row.date.dayOfMonth} ${row.day.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(52.dp),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    TYPE_OPTIONS.forEachIndexed { index, opt ->
                        val weight = if (opt.type == null) 0.5f else 1f
                        SegmentedButton(
                            selected = row.plan.dayType == opt.type,
                            onClick = { onSetType(opt.type) },
                            shape = SegmentedButtonDefaults.itemShape(index, TYPE_OPTIONS.size),
                            icon = {},
                            modifier = Modifier.weight(weight),
                        ) {
                            Text(
                                text = if (opt.label.isEmpty()) opt.emoji else "${opt.emoji} ${opt.label}",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // Parking section (only when decided as Office)
            if (row.isDecided && isOffice) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\uD83C\uDD7F\uFE0F Parking",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ParkingStatus.entries.forEach { status ->
                        FilterChip(
                            selected = row.plan.parkingStatus == status,
                            onClick = { onSetParking(status) },
                            label = { Text("${status.emoji} ${status.label}") },
                        )
                    }
                }
                if (row.plan.parkingStatus != ParkingStatus.BOOKED) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenWilson,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Text("\uD83D\uDE97 Open Wilson Parking")
                    }
                }
            }
        }
    }
}
