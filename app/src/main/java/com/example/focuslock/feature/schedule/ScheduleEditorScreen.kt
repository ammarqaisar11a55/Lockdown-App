package com.example.focuslock.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.components.SwitchRow
import com.example.focuslock.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleEditorRoute(
    onBack: () -> Unit,
    onConfigureApps: () -> Unit,
    viewModel: ScheduleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    ScheduleEditorScreen(
        state = state,
        onBack = onBack,
        onConfigureApps = onConfigureApps,
        onFormChange = viewModel::updateForm,
        onToggleDay = viewModel::toggleDay,
        onSave = viewModel::requestSave,
        onConfirmSave = viewModel::confirmSave,
        onDismissReview = viewModel::dismissReview,
        onDelete = viewModel::delete,
    )
}

private enum class PickerTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorUiState,
    onBack: () -> Unit,
    onConfigureApps: () -> Unit,
    onFormChange: ((ScheduleForm) -> ScheduleForm) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
    onConfirmSave: () -> Unit,
    onDismissReview: () -> Unit,
    onDelete: () -> Unit,
) {
    var timePicker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStrictConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val form = state.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_create))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onFormChange { it.copy(name = value.take(NAME_INPUT_LIMIT)) } },
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TAG_NAME),
            )

            SectionCard {
                TimeRow(stringResource(R.string.editor_start), form.start) { timePicker = PickerTarget.START }
                TimeRow(stringResource(R.string.editor_end), form.end) { timePicker = PickerTarget.END }
                if (!form.end.isAfter(form.start) && form.end != form.start) {
                    Text(
                        stringResource(R.string.editor_overnight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RepeatSection(
                form = form,
                onRepeatChange = { option -> onFormChange { it.copy(repeat = option) } },
                onPickDate = { showDatePicker = true },
                onToggleDay = onToggleDay,
            )

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_allowed_apps), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.editor_allowed_apps_count, state.allowedAppCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onConfigureApps) { Text(stringResource(R.string.action_configure)) }
                }
                SwitchRow(
                    title = stringResource(R.string.editor_strict),
                    subtitle = stringResource(R.string.editor_strict_subtitle),
                    checked = form.strictMode,
                    onCheckedChange = { enable ->
                        if (enable) showStrictConfirm = true else onFormChange { it.copy(strictMode = false) }
                    },
                    modifier = Modifier.testTag(TAG_STRICT),
                )
                SwitchRow(
                    title = stringResource(R.string.editor_auto_start),
                    subtitle = stringResource(R.string.editor_auto_start_subtitle),
                    checked = form.autoStart,
                    onCheckedChange = { value -> onFormChange { it.copy(autoStart = value) } },
                )
            }

            state.error?.let {
                Text(it.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(TAG_ERROR))
            }
            if (state.lockedBySession) {
                Text(stringResource(R.string.editor_locked_by_session), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = onSave,
                enabled = !state.loading && !state.saving,
                modifier = Modifier.fillMaxWidth().testTag(TAG_SAVE),
            ) {
                Text(stringResource(if (state.isEditing) R.string.action_save_session else R.string.action_create_session))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    timePicker?.let { target ->
        TimePickerDialog(
            initial = if (target == PickerTarget.START) form.start else form.end,
            onDismiss = { timePicker = null },
            onConfirm = { time ->
                timePicker = null
                onFormChange { if (target == PickerTarget.START) it.copy(start = time) else it.copy(end = time) }
            },
        )
    }
    if (showDatePicker) {
        OnceDatePickerDialog(
            initial = form.onceDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                showDatePicker = false
                onFormChange { it.copy(onceDate = date) }
            },
        )
    }
    if (showStrictConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.strict_confirm_title),
            message = stringResource(R.string.strict_confirm_body),
            confirmLabel = stringResource(R.string.strict_confirm_action),
            onConfirm = {
                showStrictConfirm = false
                onFormChange { it.copy(strictMode = true) }
            },
            onDismiss = { showStrictConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = stringResource(R.string.delete_confirm_body, form.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    state.review?.let { ReviewDialog(it, onConfirmSave, onDismissReview) }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick, modifier = Modifier.defaultMinSize(minWidth = TIME_BUTTON_MIN_WIDTH)) {
            Text(formatTime(time), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(
    form: ScheduleForm,
    onRepeatChange: (RepeatOption) -> Unit,
    onPickDate: () -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    SectionCard(title = stringResource(R.string.editor_repeat)) {
        Column(Modifier.selectableGroup()) {
            RepeatOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Spacing.touchTarget)
                        .selectable(selected = form.repeat == option, role = Role.RadioButton) { onRepeatChange(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = form.repeat == option, onClick = null)
                    Text(repeatOptionLabel(option), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
        when (form.repeat) {
            RepeatOption.ONCE -> OutlinedButton(onClick = onPickDate) {
                Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(form.onceDate))
            }
            RepeatOption.CUSTOM -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in form.customDays,
                        onClick = { onToggleDay(day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            RepeatOption.DAILY, RepeatOption.WEEKDAYS -> Unit
        }
    }
}

@Composable
private fun repeatOptionLabel(option: RepeatOption): String = stringResource(
    when (option) {
        RepeatOption.ONCE -> R.string.repeat_once
        RepeatOption.DAILY -> R.string.repeat_daily
        RepeatOption.WEEKDAYS -> R.string.repeat_weekdays
        RepeatOption.CUSTOM -> R.string.repeat_custom
    },
)

@Composable
private fun ReviewDialog(schedule: FocusSchedule, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TAG_REVIEW),
        title = { Text(stringResource(R.string.review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.review_body))
                if (schedule.strictMode) Text(stringResource(R.string.review_strict), color = MaterialTheme.colorScheme.tertiary)
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.review_start, formatTime(schedule.startTime)))
                Text(stringResource(R.string.review_end, formatTime(schedule.endTime)))
                Text(schedule.repeat.label())
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnceDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            // DatePicker reports dates as UTC midnight.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton onDismiss()
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

private const val NAME_INPUT_LIMIT = 40
private val TIME_BUTTON_MIN_WIDTH = Spacing.touchTarget * 2
const val TAG_NAME = "editor_name"
const val TAG_SAVE = "editor_save"
const val TAG_STRICT = "editor_strict"
const val TAG_ERROR = "editor_error"
const val TAG_REVIEW = "editor_review"
