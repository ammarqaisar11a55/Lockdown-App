package com.example.focuslock.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.MetricText
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.components.SessionItem
import com.example.focuslock.ui.components.SwitchRow
import com.example.focuslock.ui.components.dayLabel
import com.example.focuslock.ui.theme.Spacing

@Composable
fun DashboardRoute(
    onCreateSchedule: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenDeviceSetup: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    DashboardScreen(
        state = state,
        message = message,
        onMessageShown = viewModel::messageShown,
        onCreateSchedule = onCreateSchedule,
        onOpenSchedules = onOpenSchedules,
        onOpenDeviceSetup = onOpenDeviceSetup,
        onSkip = viewModel::skipUpcoming,
        onStartPending = viewModel::startPending,
        onStartFocusNow = viewModel::startFocusNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    message: DashboardMessage?,
    onMessageShown: () -> Unit,
    onCreateSchedule: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenDeviceSetup: () -> Unit,
    onSkip: (String) -> Unit,
    onStartPending: (String) -> Unit,
    onStartFocusNow: (minutes: Int, strict: Boolean, name: String) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var showFocusNow by rememberSaveable { mutableStateOf(false) }
    val messageText = message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StatusCard(state, onOpenDeviceSetup)
            state.countdown?.let { CountdownCard(it, onSkip) }
            state.awaitingStart?.let { AwaitingStartCard(it, onStartPending) }
            TodayCard(state)
            UpcomingCard(state, onSkip, onCreateSchedule, onOpenSchedules)
            SectionCard(title = stringResource(R.string.dashboard_focus_now)) {
                Text(
                    stringResource(R.string.dashboard_focus_now_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { showFocusNow = true },
                    enabled = state.mode != AppMode.LOCKDOWN,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.dashboard_focus_now_action)) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }

    if (showFocusNow) {
        FocusNowDialog(
            onDismiss = { showFocusNow = false },
            onConfirm = { minutes, strict, name ->
                showFocusNow = false
                onStartFocusNow(minutes, strict, name)
            },
        )
    }
}

@Composable
private fun messageText(message: DashboardMessage): String = stringResource(
    when (message) {
        DashboardMessage.FOCUS_STARTED -> R.string.message_focus_started
        DashboardMessage.FOCUS_REFUSED -> R.string.message_focus_refused
        DashboardMessage.SKIPPED -> R.string.message_skipped
        DashboardMessage.SKIP_REFUSED -> R.string.message_skip_refused
        DashboardMessage.START_REFUSED -> R.string.message_start_refused
    },
)

@Composable
private fun StatusCard(state: DashboardUiState, onOpenDeviceSetup: () -> Unit) {
    val (title, body) = when (state.mode) {
        AppMode.UNPROVISIONED -> R.string.mode_unprovisioned_title to R.string.mode_unprovisioned_body
        AppMode.NORMAL -> R.string.mode_normal_title to R.string.mode_normal_body
        AppMode.SCHEDULED -> R.string.mode_scheduled_title to R.string.mode_scheduled_body
        AppMode.COUNTDOWN -> R.string.mode_countdown_title to R.string.mode_countdown_body
        AppMode.LOCKDOWN -> R.string.mode_lockdown_title to R.string.mode_lockdown_body
        AppMode.COMPLETED -> R.string.mode_completed_title to R.string.mode_completed_body
    }
    SectionCard(modifier = Modifier.testTag(TAG_STATUS)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.loading && !state.isDeviceOwner) {
            OutlinedButton(onClick = onOpenDeviceSetup) { Text(stringResource(R.string.action_open_device_setup)) }
        }
    }
}

@Composable
private fun CountdownCard(info: CountdownInfo, onSkip: (String) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.countdown_title)) {
        Text(
            DurationFormatter.clock(info.remaining),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics {
                contentDescription = DurationFormatter.spoken(info.remaining)
            },
        )
        Text("${info.item.name} · ${info.item.timeRange}", style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.countdown_prepare),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.countdown_cancel)) }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.countdown_cancel_confirm_title),
            message = stringResource(R.string.countdown_cancel_confirm_body, info.item.name),
            confirmLabel = stringResource(R.string.countdown_cancel_confirm_action),
            dismissLabel = stringResource(R.string.action_keep_session),
            onConfirm = {
                confirm = false
                onSkip(info.item.occurrenceKey)
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun AwaitingStartCard(item: SessionItem, onStart: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.awaiting_title)) {
        Text("${item.name} · ${item.timeRange}", style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.awaiting_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onStart(item.occurrenceKey) }) { Text(stringResource(R.string.action_start_session)) }
    }
}

@Composable
private fun TodayCard(state: DashboardUiState) {
    val stats = state.stats
    SectionCard(title = stringResource(R.string.dashboard_today)) {
        val percent = (stats.todayProgress * PERCENT).toInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { stats.todayProgress },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$percent%" },
            )
            Text("  $percent%", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricText(stringResource(R.string.metric_focused_today), DurationFormatter.short(stats.todayFocused))
            MetricText(stringResource(R.string.metric_planned_today), DurationFormatter.short(stats.todayPlanned))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricText(stringResource(R.string.metric_week), DurationFormatter.short(stats.weekFocused))
            MetricText(stringResource(R.string.metric_completed), stats.weekCompletedSessions.toString())
            MetricText(stringResource(R.string.metric_exit_attempts), stats.weekExitAttempts.toString())
        }
    }
}

@Composable
private fun UpcomingCard(
    state: DashboardUiState,
    onSkip: (String) -> Unit,
    onCreateSchedule: () -> Unit,
    onOpenSchedules: () -> Unit,
) {
    var skipCandidate by remember { mutableStateOf<SessionItem?>(null) }
    SectionCard(title = stringResource(R.string.dashboard_next)) {
        val next = state.next
        if (next == null) {
            Text(
                stringResource(if (state.hasSchedules) R.string.dashboard_no_upcoming else R.string.dashboard_no_schedules),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("${next.name} · ${next.dayLabel()}", style = MaterialTheme.typography.bodyLarge)
            Text(next.timeRange, style = MaterialTheme.typography.headlineSmall)
            if (next.strictMode) {
                Text(
                    stringResource(R.string.label_strict),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            TextButton(onClick = { skipCandidate = next }) { Text(stringResource(R.string.action_skip_once)) }
        }
        if (state.tomorrow.isNotEmpty()) {
            Text(stringResource(R.string.day_tomorrow), style = MaterialTheme.typography.labelLarge)
            state.tomorrow.forEach { Text(it.timeRange, style = MaterialTheme.typography.bodyMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onCreateSchedule) { Text(stringResource(R.string.action_new_schedule)) }
            OutlinedButton(onClick = onOpenSchedules) { Text(stringResource(R.string.action_all_schedules)) }
        }
    }
    skipCandidate?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.skip_confirm_title),
            message = stringResource(R.string.skip_confirm_body, item.name, item.dayLabel(), item.timeRange),
            confirmLabel = stringResource(R.string.action_skip_once),
            dismissLabel = stringResource(R.string.action_keep_session),
            onConfirm = {
                skipCandidate = null
                onSkip(item.occurrenceKey)
            },
            onDismiss = { skipCandidate = null },
        )
    }
}

@Composable
private fun FocusNowDialog(onDismiss: () -> Unit, onConfirm: (Int, Boolean, String) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(FOCUS_NOW_OPTIONS[1]) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_focus_now)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    label = { Text(stringResource(R.string.editor_name)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FOCUS_NOW_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text(stringResource(R.string.minutes_short, option)) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.editor_strict),
                    checked = strict,
                    onCheckedChange = { strict = it },
                )
                Text(
                    stringResource(R.string.focus_now_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes, strict, name) }) { Text(stringResource(R.string.action_start_now)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val FOCUS_NOW_OPTIONS = listOf(25, 50, 90)
private const val MAX_NAME = 40
private const val PERCENT = 100
const val TAG_STATUS = "dashboard_status"
