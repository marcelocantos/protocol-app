package com.marcelo.protocol.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.ChecklistRepository
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.ChecklistItem
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.checklistFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChecklistRow(
    val item: ChecklistItem,
    val checked: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProtocolApp
    private val scheduleRepo = ScheduleRepository(app.dataStore)
    private val checklistRepo = ChecklistRepository(app.dataStore)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** True when viewing a past day and the user hasn't unlocked editing. */
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

    val checklist: StateFlow<List<ChecklistRow>> = combine(
        _selectedDate,
        dayType,
        _selectedDate.flatMapLatest { checklistRepo.completedItems(it) },
    ) { date, type, completed ->
        checklistFor(type).map { item ->
            ChecklistRow(item, item.id in completed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gymCount: StateFlow<Int> = _selectedDate
        .flatMapLatest { checklistRepo.gymCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            checklistRepo.toggleItem(date, itemId)

            // Sync gym count when the gym item is toggled.
            if (itemId == "gym") {
                val current = gymCount.value
                val completed = checklist.value.find { it.item.id == "gym" }?.checked ?: false
                // If it was checked and we toggled it, it's now unchecked (decrement).
                // If it was unchecked and we toggled it, it's now checked (increment).
                val newCount = if (completed) current - 1 else current + 1
                checklistRepo.setGymCount(date, newCount)
            }
        }
    }

    init {
        viewModelScope.launch { checklistRepo.pruneOldEntries() }
    }
}
