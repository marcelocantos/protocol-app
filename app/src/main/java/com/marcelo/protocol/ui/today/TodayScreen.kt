package com.marcelo.protocol.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Number of pages in each direction from today. */
private const val PAGE_OFFSET = 365
private const val TOTAL_PAGES = PAGE_OFFSET * 2 + 1

private fun pageToDate(page: Int): LocalDate =
    LocalDate.now().plusDays((page - PAGE_OFFSET).toLong())

@Composable
fun TodayScreen(vm: TodayViewModel) {
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val isToday by vm.isToday.collectAsStateWithLifecycle()
    val editable by vm.editable.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = PAGE_OFFSET,
        pageCount = { TOTAL_PAGES },
    )

    // Sync pager page -> ViewModel date.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.goToDate(pageToDate(page))
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        DayPage(
            vm = vm,
            date = pageToDate(page),
            isToday = pageToDate(page) == LocalDate.now(),
            editable = if (pageToDate(page) == selectedDate) editable else pageToDate(page) == LocalDate.now(),
            unlocked = if (pageToDate(page) == selectedDate) unlocked else false,
        )
    }
}

@Composable
private fun DayPage(
    vm: TodayViewModel,
    date: LocalDate,
    isToday: Boolean,
    editable: Boolean,
    unlocked: Boolean,
) {
    val dayType by vm.dayType.collectAsStateWithLifecycle()
    val checklist by vm.checklist.collectAsStateWithLifecycle()
    val gymCount by vm.gymCount.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()

    // Only render the active page's real data; off-screen pages show the date header.
    val isActivePage = date == selectedDate

    val done = if (isActivePage) checklist.count { it.checked } else 0
    val total = if (isActivePage) checklist.size else 0
    val progress = if (total > 0) done.toFloat() / total else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Header
        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            if (isToday) append("Today, ")
                            append(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (isActivePage) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${dayType.emoji}  ${dayType.label} Day",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!isToday) {
                    IconButton(onClick = { vm.toggleLock() }) {
                        Icon(
                            imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = if (unlocked) "Lock editing" else "Unlock editing",
                            tint = if (unlocked)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (isActivePage) {
            // Progress
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("$done / $total complete", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "\uD83C\uDFCB\uFE0F Gym this week: $gymCount / 3",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Checklist
            items(checklist, key = { it.item.id }) { row ->
                ChecklistCard(
                    row = row,
                    onToggle = { vm.toggle(row.item.id) },
                    enabled = editable,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) } // room for bottom nav
    }
}

@Composable
private fun ChecklistCard(row: ChecklistRow, onToggle: () -> Unit, enabled: Boolean) {
    val bg by animateColorAsState(
        targetValue = if (row.checked)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surface,
        label = "cardBg",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        onClick = onToggle,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = row.checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                )
            }
            Text(
                text = row.item.timeHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
