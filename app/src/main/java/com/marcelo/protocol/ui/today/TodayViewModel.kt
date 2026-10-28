// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.ProtocolDatabase
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.ChecklistItem
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.checklistFor
import com.marcelo.protocol.nag.MissingCapability
import com.marcelo.protocol.nag.NagEngine
import com.marcelo.protocol.nag.NagPermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Duration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ChecklistRow(
    val item: ChecklistItem,
    val checked: Boolean,
    val completedAt: LocalDateTime? = null,
    /** Unchecked, on today's page, and past its due time. */
    val overdueMinutes: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProtocolApp
    private val db: ProtocolDatabase = app.db
    private val scheduleRepo = ScheduleRepository(app.db)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val isToday: StateFlow<Boolean> = _selectedDate
        .map { it == LocalDate.now() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val editable: StateFlow<Boolean> = combine(isToday, _unlocked) { today, unlocked ->
        today || unlocked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dayType: StateFlow<DayType> = _selectedDate
        .flatMapLatest { scheduleRepo.dayTypeFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayType.REST)

    private val completions = merge(_selectedDate, _refresh)
        .map { db.completedItems(_selectedDate.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Ticks every 30s so overdue labels stay current. */
    private val clock = flow {
        while (true) { emit(LocalDateTime.now()); delay(30_000) }
    }

    val checklist: StateFlow<List<ChecklistRow>> = combine(
        dayType,
        completions,
        clock,
    ) { type, completed, now ->
        val date = _selectedDate.value
        checklistFor(type).map { item ->
            val checked = item.id in completed
            val due = item.dueTime?.let { date.atTime(it) }
            val overdue = if (!checked && date == now.toLocalDate() && due != null && now.isAfter(due))
                Duration.between(due, now).toMinutes() else null
            ChecklistRow(item, checked, completed[item.id], overdue)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gymCount: StateFlow<Int> = merge(_selectedDate, _refresh)
        .map { db.gymCountForWeek(_selectedDate.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _missingCapabilities = MutableStateFlow(NagPermissions.missing(app))
    val missingCapabilities: StateFlow<List<MissingCapability>> = _missingCapabilities.asStateFlow()

    /** Re-check permissions (call on resume; users grant them in system settings). */
    fun refreshCapabilities() {
        _missingCapabilities.value = NagPermissions.missing(app)
        _refresh.tryEmit(Unit) // also picks up completions made from notifications
    }

    fun goToDate(date: LocalDate) {
        _selectedDate.value = date
        _unlocked.value = false
    }

    fun toggleLock() {
        _unlocked.value = !_unlocked.value
    }

    fun refreshDate() {
        _selectedDate.value = LocalDate.now()
        _unlocked.value = false
    }

    fun toggle(itemId: String) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val isCompleted = completions.value.containsKey(itemId)
            if (isCompleted) {
                db.removeCompletion(date, itemId)
            } else {
                db.setCompletion(date, itemId, LocalDateTime.now())
            }
            _refresh.emit(Unit)
            NagEngine.onCompletionChanged(app, date, itemId, completed = !isCompleted)
        }
    }

    fun updateCompletionTime(itemId: String, time: LocalTime) {
        viewModelScope.launch {
            val date = _selectedDate.value
            db.setCompletion(date, itemId, date.atTime(time))
            _refresh.emit(Unit)
        }
    }
}
