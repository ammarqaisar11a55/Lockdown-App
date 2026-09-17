package com.example.focuslock.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.ui.components.BackHeader
import com.example.focuslock.ui.components.ChoiceChip
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.FieldLabel
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.FlDialog
import com.example.focuslock.ui.components.FlTextField
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.IconAction
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.OutlineButton
import com.example.focuslock.ui.components.PrimaryButton
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.components.SwitchRow
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens
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
    onOpenDeviceSetup: () -> Unit,
    viewModel: ScheduleEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDeviceOwner() }
    ScheduleEditorScreen(
        state = state,
        onBack = onBack,
        onConfigureApps = onConfigureApps,
        onOpenDeviceSetup = onOpenDeviceSetup,
        onFormChange = viewModel::updateForm,
        onToggleDay = viewModel::toggleDay,
        onSave = viewModel::requestSave,
        onConfirmSave = viewModel::confirmSave,
        onDismissReview = viewModel::dismissReview,
        onDelete = viewModel::delete,
    )
}

private enum class PickerTarget { START, END }

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
    onOpenDeviceSetup: () -> Unit = {},
) {
    var timePicker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showStrictConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showStrictUnavailable by rememberSaveable { mutableStateOf(false) }
    val form = state.form

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        BackHeader(
            title = stringResource(if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_create),
            onBack = onBack,
        ) {
            if (state.isEditing) {
                IconAction(LucideIcons.Trash, stringResource(R.string.action_delete), { showDeleteConfirm = true }, tint = Tokens.err)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                FieldLabel(stringResource(R.string.editor_name))
                FlTextField(
                    value = form.name,
                    onValueChange = { value -> onFormChange { it.copy(name = value.take(NAME_INPUT_LIMIT)) } },
                    placeholder = stringResource(R.string.editor_name_placeholder),
                    modifier = Modifier.testTag(TAG_NAME),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            FlCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                TimeRow(stringResource(R.string.editor_start), form.start) { timePicker = PickerTarget.START }
                HairlineDivider()
                TimeRow(stringResource(R.string.editor_end), form.end) { timePicker = PickerTarget.END }
                if (!form.end.isAfter(form.start) && form.end != form.start) {
                    MutedText(stringResource(R.string.editor_overnight), Modifier.padding(bottom = 12.dp), LockdownType.caption)
                }
            }

            RepeatSection(
                form = form,
                onRepeatChange = { option -> onFormChange { it.copy(repeat = option) } },
                onPickDate = { showDatePicker = true },
                onToggleDay = onToggleDay,
            )

            FlCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_allowed_apps), style = LockdownType.body, color = Tokens.text)
                        MutedText(
                            pluralStringResource(R.plurals.editor_allowed_apps_count, state.allowedAppCount, state.allowedAppCount),
                            Modifier.padding(top = 2.dp),
                            LockdownType.caption,
                        )
                    }
                    OutlineButton(
                        stringResource(R.string.action_configure),
                        onConfigureApps,
                        minHeight = 44.dp,
                        textStyle = LockdownType.button.copy(fontSize = 12.5.sp),
                    )
                }
                HairlineDivider()
                SwitchRow(
                    title = stringResource(R.string.editor_strict),
                    subtitle = stringResource(
                        if (state.strictAvailable) R.string.editor_strict_subtitle else R.string.editor_strict_needs_owner,
                    ),
                    checked = form.strictMode,
                    onCheckedChange = { enable ->
                        when {
                            !enable -> onFormChange { it.copy(strictMode = false) }
                            state.strictAvailable -> showStrictConfirm = true
                            else -> showStrictUnavailable = true
                        }
                    },
                    modifier = Modifier.testTag(TAG_STRICT),
                )
                HairlineDivider()
                SwitchRow(
                    title = stringResource(R.string.editor_auto_start),
                    subtitle = stringResource(R.string.editor_auto_start_subtitle),
                    checked = form.autoStart,
                    onCheckedChange = { value -> onFormChange { it.copy(autoStart = value) } },
                )
            }

            state.error?.let {
                Text(it.message(), style = LockdownType.bodySmall, color = Tokens.err, modifier = Modifier.testTag(TAG_ERROR))
            }
            if (state.lockedBySession) {
                Text(stringResource(R.string.editor_locked_by_session), style = LockdownType.bodySmall, color = Tokens.err)
            }

            PrimaryButton(
                text = stringResource(if (state.isEditing) R.string.action_save_session else R.string.action_create_session),
                onClick = onSave,
                enabled = !state.loading && !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(TAG_SAVE),
                textStyle = LockdownType.button.copy(fontSize = 14.sp),
            )
        }
    }

    timePicker?.let { target ->
        StepperTimeDialog(
            title = stringResource(if (target == PickerTarget.START) R.string.editor_start_time else R.string.editor_end_time),
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
    if (showStrictUnavailable) {
        ConfirmDialog(
            title = stringResource(R.string.strict_unavailable_title),
            message = stringResource(R.string.strict_unavailable_body),
            confirmLabel = stringResource(R.string.action_open_device_setup),
            onConfirm = {
                showStrictUnavailable = false
                onOpenDeviceSetup()
            },
            onDismiss = { showStrictUnavailable = false },
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
    state.review?.let { ReviewDialog(it, state.reviewStartsNow, onConfirmSave, onDismissReview) }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = LockdownType.body, color = Tokens.text, modifier = Modifier.weight(1f))
        OutlineButton(
            text = formatTime(time),
            onClick = onClick,
            minHeight = 44.dp,
            modifier = Modifier.widthIn(min = 104.dp),
            textStyle = LockdownType.cardTitle.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun RepeatSection(
    form: ScheduleForm,
    onRepeatChange: (RepeatOption) -> Unit,
    onPickDate: () -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    FlCard {
        SectionLabel(stringResource(R.string.editor_repeat))
        Column(
            Modifier.padding(top = 12.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RepeatOption.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        ChoiceChip(
                            label = repeatOptionLabel(option),
                            selected = form.repeat == option,
                            onClick = { onRepeatChange(option) },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
        when (form.repeat) {
            RepeatOption.ONCE -> OutlineButton(
                text = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(form.onceDate),
                onClick = onPickDate,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minHeight = 44.dp,
                textStyle = LockdownType.cardTitle.copy(fontSize = 15.sp),
            )
            RepeatOption.CUSTOM -> Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    ChoiceChip(
                        label = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        selected = day in form.customDays,
                        onClick = { onToggleDay(day) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                        minHeight = 42.dp,
                        textStyle = LockdownType.cardTitle.copy(fontSize = 12.sp),
                        role = Role.Checkbox,
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
private fun ReviewDialog(schedule: FocusSchedule, startsNow: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FlDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.review_title),
        confirmLabel = stringResource(R.string.action_confirm),
        onConfirm = onConfirm,
        modifier = Modifier.testTag(TAG_REVIEW),
    ) {
        val body = LockdownType.bodySmall.copy(fontSize = 14.sp, lineHeight = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (startsNow) {
                Text(
                    stringResource(R.string.review_starts_now, formatTime(schedule.endTime)),
                    style = body,
                    color = Tokens.err,
                    modifier = Modifier.testTag(TAG_STARTS_NOW),
                )
            }
            Text(stringResource(R.string.review_body), style = body, color = Tokens.muted)
            if (schedule.strictMode) Text(stringResource(R.string.review_strict), style = body, color = Tokens.warn)
            Text(schedule.name, style = LockdownType.cardTitle, color = Tokens.text)
            Text(stringResource(R.string.review_start, formatTime(schedule.startTime)), style = body, color = Tokens.text)
            Text(stringResource(R.string.review_end, formatTime(schedule.endTime)), style = body, color = Tokens.text)
            Text(schedule.repeat.label(), style = body, color = Tokens.text)
        }
    }
}

/** Hour/minute steppers from the design; minutes move in 5-minute steps. */
@Composable
private fun StepperTimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    var time by rememberSaveable { mutableStateOf(initial) }
    FlDialog(onDismiss = onDismiss, confirmLabel = stringResource(R.string.action_ok), onConfirm = { onConfirm(time) }) {
        SectionLabel(title)
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperColumn(
                value = "%02d".format(Locale.ROOT, time.hour),
                label = stringResource(R.string.editor_hours),
                onUp = { time = time.plusHours(1) },
                onDown = { time = time.minusHours(1) },
            )
            StepperColumn(
                value = "%02d".format(Locale.ROOT, time.minute),
                label = stringResource(R.string.editor_minutes),
                onUp = { time = time.plusMinutes(MINUTE_STEP) },
                onDown = { time = time.minusMinutes(MINUTE_STEP) },
            )
        }
        MutedText(
            formatTime(time),
            Modifier.fillMaxWidth().padding(top = 14.dp),
            LockdownType.bodySmall.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun StepperColumn(value: String, label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperButton(LucideIcons.ChevronUp, stringResource(R.string.editor_increase, label), onUp)
        Text(
            value,
            style = LockdownType.bigNumber.copy(fontSize = 44.sp, fontFeatureSettings = "tnum"),
            color = Tokens.text,
            modifier = Modifier.semantics { contentDescription = "$label $value" },
        )
        StepperButton(LucideIcons.ChevronDown, stringResource(R.string.editor_decrease, label), onDown)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 44.dp)
            .border(1.dp, Tokens.line, RoundedCornerShape(5.dp))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.text, modifier = Modifier.size(16.dp))
    }
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
        colors = DatePickerDefaults.colors(containerColor = Tokens.surface),
        confirmButton = {
            TextAction(
                stringResource(R.string.action_ok).uppercase(),
                {
                    pickerState.selectedDateMillis
                        ?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        ?: onDismiss()
                },
                color = Tokens.accent,
                style = LockdownType.button,
            )
        },
        dismissButton = { TextAction(stringResource(R.string.action_cancel), onDismiss) },
    ) {
        DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = Tokens.surface))
    }
}

private const val NAME_INPUT_LIMIT = 40
private const val MINUTE_STEP = 5L
const val TAG_NAME = "editor_name"
const val TAG_SAVE = "editor_save"
const val TAG_STRICT = "editor_strict"
const val TAG_ERROR = "editor_error"
const val TAG_REVIEW = "editor_review"
const val TAG_STARTS_NOW = "editor_starts_now"
