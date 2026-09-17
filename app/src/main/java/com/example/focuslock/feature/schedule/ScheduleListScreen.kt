package com.example.focuslock.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.EmptyState
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.FlSnackbarHost
import com.example.focuslock.ui.components.FlSwitch
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.PrimaryButton
import com.example.focuslock.ui.components.ScreenTitle
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens
import java.time.LocalTime

@Composable
fun ScheduleListRoute(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ScheduleListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val copySuffix = stringResource(R.string.schedule_copy_suffix)
    ScheduleListScreen(
        state = state,
        message = message,
        onMessageShown = viewModel::messageShown,
        onCreate = onCreate,
        onEdit = onEdit,
        onToggle = viewModel::setEnabled,
        onDuplicate = { viewModel.duplicate(it, copySuffix) },
        onDelete = viewModel::delete,
    )
}

@Composable
fun ScheduleListScreen(
    state: ScheduleListUiState,
    message: ScheduleListMessage?,
    onMessageShown: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDuplicate: (FocusSchedule) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var deleteCandidate by remember { mutableStateOf<FocusSchedule?>(null) }
    val messageText = message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Scaffold(
        containerColor = Tokens.bg,
        // Insets are applied explicitly by the content (the app bar lives in the parent scaffold).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { FlSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 6.dp, bottom = 30.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScreenTitle(stringResource(R.string.schedules_title))
                    PrimaryButton(
                        stringResource(R.string.action_new),
                        onCreate,
                        minHeight = 44.dp,
                        icon = LucideIcons.Plus,
                        textStyle = LockdownType.button.copy(fontSize = 13.sp),
                    )
                }
            }
            if (!state.loading && state.schedules.isEmpty()) {
                item { EmptyState(stringResource(R.string.schedules_empty)) }
            }
            listOf(
                R.string.day_today to state.today,
                R.string.schedules_other_days to state.otherDays,
            ).filter { it.second.isNotEmpty() }.forEach { (title, schedules) ->
                item(key = "header-$title") {
                    SectionLabel(stringResource(title), Modifier.padding(bottom = 10.dp))
                }
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        enforced = schedule.id == state.enforcedScheduleId,
                        onEdit = { onEdit(schedule.id) },
                        onToggle = { onToggle(schedule.id, it) },
                        onDuplicate = { onDuplicate(schedule) },
                        onDelete = { deleteCandidate = schedule },
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                }
                item(key = "gap-$title") { Box(Modifier.height(13.dp)) }
            }
        }
    }
    deleteCandidate?.let { schedule ->
        ConfirmDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = stringResource(R.string.delete_confirm_body, schedule.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                deleteCandidate = null
                onDelete(schedule.id)
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun messageText(message: ScheduleListMessage): String = when (message) {
    is ScheduleListMessage.Error -> message.error.message()
    ScheduleListMessage.Duplicated -> stringResource(R.string.message_duplicated)
    ScheduleListMessage.Deleted -> stringResource(R.string.message_deleted)
    ScheduleListMessage.Locked -> stringResource(R.string.editor_locked_by_session)
}

@Composable
private fun ScheduleCard(
    schedule: FocusSchedule,
    enforced: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dim = if (schedule.enabled) 1f else DISABLED_ALPHA
    FlCard(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 17.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = !enforced, onClickLabel = stringResource(R.string.action_edit), onClick = onEdit),
            ) {
                Text(schedule.name, style = LockdownType.cardTitle, color = Tokens.text, modifier = Modifier.alpha(dim))
                val details = buildList {
                    add(schedule.repeat.label())
                    if (schedule.strictMode) add(stringResource(R.string.label_strict))
                    if (!schedule.autoStart) add(stringResource(R.string.label_manual_start))
                    if (enforced) add(stringResource(R.string.label_active_now))
                }
                Text(
                    details.joinToString(" · "),
                    style = LockdownType.caption,
                    color = Tokens.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            val stateText = stringResource(if (schedule.enabled) R.string.state_enabled else R.string.state_disabled)
            FlSwitch(
                schedule.enabled,
                Modifier
                    .toggleable(value = schedule.enabled, enabled = !enforced, role = Role.Switch, onValueChange = onToggle)
                    .semantics { stateDescription = stateText }
                    .alpha(if (enforced) DISABLED_ALPHA else 1f),
            )
        }
        Timeline(schedule, dim, Modifier.padding(top = 15.dp))
        HairlineDivider(Modifier.padding(top = 11.dp))
        Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val actionStyle = LockdownType.bodySmall.copy(fontSize = 13.sp)
            if (!enforced) {
                TextAction(stringResource(R.string.action_edit), onEdit, color = Tokens.accent, style = actionStyle, minHeight = 36.dp)
            }
            TextAction(stringResource(R.string.action_duplicate), onDuplicate, color = Tokens.accent, style = actionStyle, minHeight = 36.dp)
            Box(Modifier.weight(1f))
            if (!enforced) {
                TextAction(stringResource(R.string.action_delete), onDelete, color = Tokens.err, style = actionStyle, minHeight = 36.dp)
            }
        }
    }
}

/** 24-hour ruler with the session drawn as a bar; overnight sessions wrap to the start. */
@Composable
private fun Timeline(schedule: FocusSchedule, alpha: Float, modifier: Modifier = Modifier) {
    val labelStyle = LockdownType.cardTitle.copy(fontSize = 13.sp, fontFeatureSettings = "tnum")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(formatShortTime(schedule.startTime), style = labelStyle, color = Tokens.text, modifier = Modifier.width(TIME_LABEL_WIDTH).alpha(alpha))
        BoxWithConstraints(Modifier.weight(1f).height(11.dp)) {
            val full = maxWidth
            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(1.dp).background(Tokens.line))
            listOf(0.25f, 0.5f, 0.75f).forEach { tick ->
                Box(Modifier.offset(x = full * tick).width(1.dp).height(11.dp).background(Tokens.lineSoft))
            }
            val start = fractionOfDay(schedule.startTime)
            val end = fractionOfDay(schedule.endTime)
            val segments = if (schedule.crossesMidnight) listOf(start to 1f, 0f to end) else listOf(start to end)
            segments.filter { it.second > it.first }.forEach { (from, to) ->
                Box(
                    Modifier
                        .offset(x = full * from, y = 3.dp)
                        .width(full * (to - from))
                        .height(5.dp)
                        .alpha(alpha)
                        .background(Tokens.accentSolid),
                )
            }
        }
        Text(
            formatShortTime(schedule.endTime),
            style = labelStyle,
            color = Tokens.text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(TIME_LABEL_WIDTH).alpha(alpha),
        )
    }
}

private fun fractionOfDay(time: LocalTime): Float = time.toSecondOfDay() / SECONDS_PER_DAY

private const val SECONDS_PER_DAY = 86_400f
private const val DISABLED_ALPHA = 0.45f
private val TIME_LABEL_WIDTH = 46.dp
