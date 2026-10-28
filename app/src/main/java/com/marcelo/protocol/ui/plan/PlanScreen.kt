// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.plan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcelo.protocol.model.DayType
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val WILSON_PACKAGE = "au.com.wilsonone"

fun openWilson(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(WILSON_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$WILSON_PACKAGE"))
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun DayOfWeek.short() = getDisplayName(TextStyle.SHORT, Locale.getDefault())
private fun List<PlanDayRow>.dayList() = joinToString(", ") { it.day.short() }

@Composable
fun PlanScreen(vm: PlanViewModel) {
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val awaiting by vm.awaitingWilsonReturn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showReturnPrompt by remember { mutableStateOf(false) }

    // Coming back from Wilson: ask what got booked instead of making the
    // user find and tap each day.
    LifecycleResumeEffect(awaiting) {
        if (awaiting) showReturnPrompt = true
        onPauseOrDispose {}
    }

    val bookWilson = {
        vm.wilsonLaunched()
        openWilson(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Office week", style = MaterialTheme.typography.titleLarge)
            WeekSwitcher(
                label = when (vm.weekOffset()) {
                    0L -> "This week"
                    1L -> "Next week"
                    else -> "In ${vm.weekOffset()} weeks"
                } + " · " + weekStart.format(DateTimeFormatter.ofPattern("d")) + "–" +
                    weekStart.plusDays(4).format(DateTimeFormatter.ofPattern("d MMM")),
                canGoBack = vm.weekOffset() > 0,
                onBack = { vm.shiftWeek(-1) },
                onForward = { vm.shiftWeek(1) },
            )
        }

        status?.let { s -> item { StatusCard(s, onConfirmUsual = vm::confirmUsual, onBook = bookWilson, onAlreadyBooked = { showReturnPrompt = true }) } }

        items(days.filter { it.staleBooking }, key = { "stale-${it.day}" }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "\u26A0\uFE0F ${row.day.short()} is booked but now ${row.plan.dayType?.label ?: "undecided"}. Cancel it in Wilson?",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { openWilson(context) }) { Text("Open Wilson") }
                        TextButton(onClick = { vm.setBooked(row.day, false) }) { Text("I've cancelled it") }
                    }
                }
            }
        }

        items(days, key = { it.day }) { row ->
            DayRow(
                row = row,
                onSetType = { vm.setPlannedType(row.day, it) },
                onToggleBooked = { vm.setBooked(row.day, !row.isBooked) },
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showReturnPrompt) {
        val candidates = days.filter { it.needsBooking }
        if (candidates.isEmpty()) {
            showReturnPrompt = false
            vm.dismissReturnPrompt()
        } else {
            ReturnFromWilsonDialog(
                candidates = candidates,
                onConfirm = { vm.markBooked(it); showReturnPrompt = false },
                onDismiss = { vm.dismissReturnPrompt(); showReturnPrompt = false },
            )
        }
    }
}

@Composable
private fun WeekSwitcher(label: String, canGoBack: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = onForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Following week")
        }
    }
}

@Composable
private fun StatusCard(
    status: PlanStatus,
    onConfirmUsual: () -> Unit,
    onBook: () -> Unit,
    onAlreadyBooked: () -> Unit,
) {
    val (bg, fg) = when (status) {
        is PlanStatus.AllSet -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                is PlanStatus.Decide -> {
                    Text("Step 1 · Confirm the week", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${status.days.size} day${if (status.days.size > 1) "s" else ""} undecided",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Usual: " + status.days.joinToString(" · ") { "${it.day.short()} ${it.defaultType.emoji} ${it.defaultType.label}" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onConfirmUsual, modifier = Modifier.fillMaxWidth()) { Text("Use my usual week") }
                    Text("…or change days below.", style = MaterialTheme.typography.bodySmall)
                }
                is PlanStatus.Book -> {
                    Text("Step 2 · Book parking", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "\uD83C\uDD7F\uFE0F Needed: ${status.days.dayList()}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = onBook, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("\uD83D\uDE97 Book in Wilson")
                    }
                    OutlinedButton(onClick = onAlreadyBooked, modifier = Modifier.fillMaxWidth()) {
                        Text("Already booked")
                    }
                }
                is PlanStatus.AllSet -> {
                    Text("\u2705 All set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (status.booked.isEmpty()) "No office days this week."
                        else "Parking booked: ${status.booked.dayList()}",
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRow(row: PlanDayRow, onSetType: (DayType) -> Unit, onToggleBooked: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (row.effectiveType == DayType.OFFICE && row.isDecided)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${row.day.short()} ${row.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp),
                )
                if (!row.isDecided) {
                    Text(
                        "usual: ${row.defaultType.emoji} ${row.defaultType.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (row.plan.dayType == DayType.OFFICE) {
                    FilterChip(
                        selected = row.isBooked,
                        onClick = onToggleBooked,
                        label = { Text(if (row.isBooked) "\uD83C\uDD7F\uFE0F Booked" else "\uD83C\uDD7F\uFE0F Not booked") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DayType.entries.forEach { type ->
                    FilterChip(
                        selected = row.plan.dayType == type,
                        onClick = { onSetType(type) },
                        label = { Text("${type.emoji} ${type.label}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReturnFromWilsonDialog(
    candidates: List<PlanDayRow>,
    onConfirm: (List<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember(candidates) { mutableStateListOf(*candidates.map { it.day }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Did you book parking?") },
        text = {
            Column {
                candidates.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = row.day in checked,
                            onCheckedChange = { if (it) checked += row.day else checked -= row.day },
                        )
                        Text("${row.day.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${row.date.dayOfMonth}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked.toList()) }, enabled = checked.isNotEmpty()) { Text("Mark booked") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } },
    )
}
