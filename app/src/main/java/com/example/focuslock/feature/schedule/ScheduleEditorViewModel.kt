package com.example.focuslock.feature.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.core.scheduling.ScheduleValidator
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.usecase.DeleteScheduleUseCase
import com.example.focuslock.domain.usecase.SaveScheduleUseCase
import com.example.focuslock.domain.usecase.ScheduleChangeResult
import com.example.focuslock.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

enum class RepeatOption { ONCE, DAILY, WEEKDAYS, CUSTOM }

data class ScheduleForm(
    val name: String = "",
    val start: LocalTime = DEFAULT_START,
    val end: LocalTime = DEFAULT_END,
    val repeat: RepeatOption = RepeatOption.WEEKDAYS,
    val onceDate: LocalDate = LocalDate.now(),
    val customDays: Set<DayOfWeek> = RepeatRule.Weekly.WEEKDAYS,
    val strictMode: Boolean = false,
    val autoStart: Boolean = true,
) {
    fun repeatRule(): RepeatRule = when (repeat) {
        RepeatOption.ONCE -> RepeatRule.Once(onceDate)
        RepeatOption.DAILY -> RepeatRule.Daily
        RepeatOption.WEEKDAYS -> RepeatRule.Weekly(RepeatRule.Weekly.WEEKDAYS)
        RepeatOption.CUSTOM -> RepeatRule.Weekly(customDays)
    }

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(8, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(13, 0)

        fun from(schedule: FocusSchedule): ScheduleForm {
            val base = ScheduleForm(
                name = schedule.name,
                start = schedule.startTime,
                end = schedule.endTime,
                strictMode = schedule.strictMode,
                autoStart = schedule.autoStart,
            )
            return when (val rule = schedule.repeat) {
                is RepeatRule.Once -> base.copy(repeat = RepeatOption.ONCE, onceDate = rule.date)
                RepeatRule.Daily -> base.copy(repeat = RepeatOption.DAILY)
                is RepeatRule.Weekly -> if (rule.isWeekdays) {
                    base.copy(repeat = RepeatOption.WEEKDAYS)
                } else {
                    base.copy(repeat = RepeatOption.CUSTOM, customDays = rule.days)
                }
            }
        }
    }
}

private data class EditorState(
    val loading: Boolean = true,
    val existing: FocusSchedule? = null,
    val form: ScheduleForm = ScheduleForm(),
    val error: ScheduleValidationError? = null,
    val lockedBySession: Boolean = false,
    val review: FocusSchedule? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
)

data class ScheduleEditorUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val form: ScheduleForm = ScheduleForm(),
    val allowedAppCount: Int = 0,
    val error: ScheduleValidationError? = null,
    val lockedBySession: Boolean = false,
    val review: FocusSchedule? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
)

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    allowedAppsRepository: AllowedAppsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val saveSchedule: SaveScheduleUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val scheduleId: String? = savedStateHandle[Routes.ARG_SCHEDULE_ID]
    private val state = MutableStateFlow(EditorState())

    val uiState: StateFlow<ScheduleEditorUiState> = combine(
        state,
        allowedAppsRepository.observeAllowed().map { it.size },
    ) { s, appCount ->
        ScheduleEditorUiState(
            loading = s.loading,
            isEditing = s.existing != null,
            form = s.form,
            allowedAppCount = appCount,
            error = s.error,
            lockedBySession = s.lockedBySession,
            review = s.review,
            saving = s.saving,
            finished = s.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScheduleEditorUiState())

    init {
        viewModelScope.launch {
            val existing = scheduleId?.let { scheduleRepository.getSchedule(it) }
            state.update {
                it.copy(
                    loading = false,
                    existing = existing,
                    form = existing?.let(ScheduleForm::from) ?: ScheduleForm(onceDate = LocalDate.now(timeSource.zone())),
                )
            }
        }
    }

    fun updateForm(transform: (ScheduleForm) -> ScheduleForm) =
        state.update { it.copy(form = transform(it.form), error = null) }

    fun toggleDay(day: DayOfWeek) = updateForm { form ->
        val days = if (day in form.customDays) form.customDays - day else form.customDays + day
        form.copy(customDays = days)
    }

    /** Validates and opens the review step; nothing is saved without explicit confirmation. */
    fun requestSave() {
        viewModelScope.launch {
            val current = state.value
            val candidate = buildSchedule(current)
            val error = ScheduleValidator.validate(
                candidate,
                scheduleRepository.getSchedules(),
                timeSource.now(),
                timeSource.zone(),
            )
            state.update { if (error != null) it.copy(error = error) else it.copy(review = candidate) }
        }
    }

    fun dismissReview() = state.update { it.copy(review = null) }

    fun confirmSave() {
        val candidate = state.value.review ?: return
        state.update { it.copy(saving = true, review = null) }
        viewModelScope.launch {
            val result = saveSchedule(candidate)
            state.update { applyResult(it.copy(saving = false), result) }
        }
    }

    fun delete() {
        val id = state.value.existing?.id ?: return
        viewModelScope.launch {
            val result = deleteSchedule(id)
            state.update { applyResult(it, result) }
        }
    }

    private fun applyResult(current: EditorState, result: ScheduleChangeResult) = when (result) {
        ScheduleChangeResult.Success -> current.copy(finished = true)
        ScheduleChangeResult.LockedBySession -> current.copy(lockedBySession = true)
        is ScheduleChangeResult.Invalid -> current.copy(error = result.error)
    }

    private fun buildSchedule(current: EditorState): FocusSchedule {
        val form = current.form
        val existing = current.existing
        return FocusSchedule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = form.name.trim(),
            startTime = form.start,
            endTime = form.end,
            repeat = form.repeatRule(),
            strictMode = form.strictMode,
            enabled = existing?.enabled ?: true,
            autoStart = form.autoStart,
            createdAt = existing?.createdAt ?: timeSource.now().toEpochMilli(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
