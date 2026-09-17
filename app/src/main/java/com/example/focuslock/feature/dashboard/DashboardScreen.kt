package com.example.focuslock.feature.dashboard

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.feature.lockdown.LockdownActivity
import com.example.focuslock.ui.components.ChoiceChip
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.FieldLabel
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.FlDialog
import com.example.focuslock.ui.components.FlSnackbarHost
import com.example.focuslock.ui.components.FlTextField
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.OutlineButton
import com.example.focuslock.ui.components.OutlineTag
import com.example.focuslock.ui.components.PrimaryButton
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.components.SessionItem
import com.example.focuslock.ui.components.SwitchRow
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.components.TintButton
import com.example.focuslock.ui.components.blueprint
import com.example.focuslock.ui.components.dayLabel
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Radii
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens
import java.time.Duration

@Composable
fun DashboardRoute(
    onCreateSchedule: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenDeviceSetup: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    DashboardScreen(
        state = state,
        message = message,
        onMessageShown = viewModel::messageShown,
        onCreateSchedule = onCreateSchedule,
        onOpenSchedules = onOpenSchedules,
        onOpenDeviceSetup = onOpenDeviceSetup,
        onOpenFocusScreen = { context.startActivity(Intent(context, LockdownActivity::class.java)) },
        onSkip = viewModel::skipUpcoming,
        onStartPending = viewModel::startPending,
        onStartFocusNow = viewModel::startFocusNow,
    )
}

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
    onOpenFocusScreen: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var showFocusNow by rememberSaveable { mutableStateOf(false) }
    var skipCandidate by remember { mutableStateOf<SessionItem?>(null) }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = 6.dp, bottom = 30.dp),
        ) {
            Greeting(state)
            ModeLine(state, onOpenDeviceSetup)
            state.countdown?.let { CountdownCard(it) { skipCandidate = it.item } }
            state.active?.let { ActiveCard(it, onOpenFocusScreen) }
            state.awaitingStart?.let { AwaitingStartCard(it, onStartPending) }
            FocusTodayCard(state)
            NextLockdown(state, onOpenSchedules, onCreateSchedule) { skipCandidate = it }
            QuickActions(
                focusEnabled = state.mode != AppMode.LOCKDOWN,
                onFocusNow = { showFocusNow = true },
                onOpenSchedules = onOpenSchedules,
            )
            TodayList(state.today)
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
    skipCandidate?.let { item ->
        val counting = state.countdown?.item?.occurrenceKey == item.occurrenceKey
        ConfirmDialog(
            title = stringResource(if (counting) R.string.countdown_cancel_confirm_title else R.string.skip_confirm_title),
            message = if (counting) {
                stringResource(R.string.countdown_cancel_confirm_body, item.name)
            } else {
                stringResource(R.string.skip_confirm_body, item.name, item.dayLabel(), item.timeRange)
            },
            confirmLabel = stringResource(if (counting) R.string.countdown_cancel_confirm_action else R.string.action_skip_session),
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
private fun Greeting(state: DashboardUiState) {
    Column(Modifier.padding(top = 14.dp, bottom = 20.dp)) {
        Text(
            stringResource(
                when (state.dayPart) {
                    DayPart.MORNING -> R.string.greeting_morning
                    DayPart.AFTERNOON -> R.string.greeting_afternoon
                    DayPart.EVENING -> R.string.greeting_evening
                },
            ),
            style = LockdownType.screenTitle,
            color = Tokens.text,
        )
        Text(state.dateText, style = LockdownType.bodySmall, color = Tokens.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ModeLine(state: DashboardUiState, onOpenDeviceSetup: () -> Unit) {
    val (title, body) = when (state.mode) {
        AppMode.UNPROVISIONED -> R.string.mode_unprovisioned_title to R.string.mode_unprovisioned_body
        AppMode.NORMAL -> R.string.mode_normal_title to R.string.mode_normal_body
        AppMode.SCHEDULED -> R.string.mode_scheduled_title to R.string.mode_scheduled_body
        AppMode.COUNTDOWN -> R.string.mode_countdown_title to R.string.mode_countdown_body
        AppMode.LOCKDOWN -> R.string.mode_lockdown_title to R.string.mode_lockdown_body
        AppMode.COMPLETED -> R.string.mode_completed_title to R.string.mode_completed_body
    }
    Column(Modifier.testTag(TAG_STATUS)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (state.mode == AppMode.UNPROVISIONED) Tokens.warn else Tokens.accentSolid, CircleShape),
            )
            Text(stringResource(title), style = LockdownType.cardTitle.copy(fontSize = 17.sp), color = Tokens.text)
        }
        MutedText(stringResource(body), Modifier.padding(top = 5.dp), LockdownType.body.copy(fontSize = 14.sp))
        if (!state.loading && !state.isDeviceOwner) {
            OutlineButton(
                stringResource(R.string.action_open_device_setup),
                onOpenDeviceSetup,
                Modifier.padding(top = 12.dp),
                minHeight = 44.dp,
            )
        }
    }
}

@Composable
private fun CountdownCard(info: CountdownInfo, onCancel: () -> Unit) {
    FlCard(Modifier.padding(top = 18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        SectionLabel(stringResource(R.string.countdown_title))
        ClockText(info.remaining, Modifier.padding(top = 8.dp), compact = true)
        Text("${info.item.name} · ${info.item.timeRange}", style = LockdownType.body.copy(fontSize = 14.sp), color = Tokens.text, modifier = Modifier.padding(top = 6.dp))
        MutedText(stringResource(R.string.countdown_prepare), Modifier.padding(top = 3.dp), LockdownType.caption)
        OutlineButton(stringResource(R.string.countdown_cancel), onCancel, Modifier.fillMaxWidth().padding(top = 15.dp))
    }
}

@Composable
private fun ActiveCard(info: ActiveInfo, onOpen: () -> Unit) {
    FlCard(
        Modifier.padding(top = 18.dp),
        borderColor = Tokens.accentSolid,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(LucideIcons.Lock, null, tint = Tokens.accent, modifier = Modifier.size(14.dp))
            SectionLabel(stringResource(R.string.mode_lockdown_title), color = Tokens.accent)
        }
        ClockText(info.remaining, Modifier.padding(top = 8.dp))
        Text(
            stringResource(R.string.dashboard_active_line, info.name, info.endTime),
            style = LockdownType.body.copy(fontSize = 14.sp),
            color = Tokens.text,
            modifier = Modifier.padding(top = 6.dp),
        )
        PrimaryButton(stringResource(R.string.action_open_focus_screen), onOpen, Modifier.fillMaxWidth().padding(top = 15.dp), minHeight = 48.dp)
    }
}

@Composable
private fun ClockText(remaining: Duration, modifier: Modifier = Modifier, compact: Boolean = false) {
    val text = if (compact && remaining < Duration.ofHours(1)) DurationFormatter.minutesSeconds(remaining) else DurationFormatter.clock(remaining)
    val spoken = DurationFormatter.spoken(remaining)
    Text(
        text,
        style = LockdownType.clock,
        color = Tokens.text,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

@Composable
private fun AwaitingStartCard(item: SessionItem, onStart: (String) -> Unit) {
    FlCard(Modifier.padding(top = 18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        SectionLabel(stringResource(R.string.awaiting_title))
        Text("${item.name} · ${item.timeRange}", style = LockdownType.body, color = Tokens.text, modifier = Modifier.padding(top = 8.dp))
        MutedText(stringResource(R.string.awaiting_body), Modifier.padding(top = 3.dp), LockdownType.caption)
        PrimaryButton(stringResource(R.string.action_start_session), { onStart(item.occurrenceKey) }, Modifier.fillMaxWidth().padding(top = 15.dp), minHeight = 48.dp)
    }
}

@Composable
private fun FocusTodayCard(state: DashboardUiState) {
    val stats = state.stats
    val percent = (stats.todayProgress * PERCENT).toInt()
    Column(
        Modifier
            .padding(top = Spacing.section)
            .fillMaxWidth()
            .blueprint(Tokens.line, Tokens.faint)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
    ) {
        SectionLabel(stringResource(R.string.dashboard_focus_today))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(DurationFormatter.short(stats.todayFocused), style = LockdownType.bigNumber.copy(fontFeatureSettings = "tnum"), color = Tokens.text)
            MutedText(stringResource(R.string.metric_focused), Modifier.padding(bottom = 5.dp), LockdownType.bodySmall.copy(fontSize = 13.sp))
            Spacer(Modifier.weight(1f))
            Text("$percent%", style = LockdownType.cardTitle.copy(fontSize = 19.sp), color = Tokens.accent, modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(
            Modifier
                .padding(top = 17.dp)
                .fillMaxWidth()
                .height(8.dp)
                .background(Tokens.surface2)
                .semantics { contentDescription = "$percent%" },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(stats.todayProgress).background(Tokens.accentSolid))
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = LockdownType.caption.copy(fontSize = 12.sp)
            if (stats.todayPlanned.isZero) {
                MutedText(stringResource(R.string.dashboard_no_plan_today), style = style)
            } else {
                MutedText(stringResource(R.string.dashboard_goal, DurationFormatter.short(stats.todayPlanned)), style = style)
                val left = stats.todayPlanned.minus(stats.todayFocused)
                MutedText(
                    if (left.isNegative || left.isZero) {
                        stringResource(R.string.dashboard_goal_reached)
                    } else {
                        stringResource(R.string.dashboard_to_go, DurationFormatter.short(left))
                    },
                    style = style,
                )
            }
        }
    }
}

@Composable
private fun NextLockdown(
    state: DashboardUiState,
    onOpenSchedules: () -> Unit,
    onCreateSchedule: () -> Unit,
    onSkip: (SessionItem) -> Unit,
) {
    Column(Modifier.padding(top = Spacing.section)) {
        SectionLabel(stringResource(R.string.dashboard_next))
        FlCard(Modifier.padding(top = 10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(17.dp)) {
            val next = state.next
            if (next == null) {
                MutedText(
                    stringResource(if (state.hasSchedules) R.string.dashboard_no_upcoming else R.string.dashboard_no_schedules),
                    style = LockdownType.body.copy(fontSize = 14.sp),
                )
                PrimaryButton(
                    stringResource(R.string.action_new_schedule),
                    onCreateSchedule,
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    minHeight = 48.dp,
                    icon = LucideIcons.Plus,
                )
                return@FlCard
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(next.name, style = LockdownType.cardTitle.copy(fontSize = 19.sp), color = Tokens.text)
                    Text(
                        next.timeRange,
                        style = LockdownType.clock.copy(fontSize = 27.sp, lineHeight = 32.sp),
                        color = Tokens.text,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (next.strictMode) OutlineTag(stringResource(R.string.label_strict))
            }
            HairlineDivider(Modifier.padding(top = 13.dp))
            val startsIn = state.nextStartsIn
            Text(
                when {
                    next.daysFromToday == 0L && startsIn != null ->
                        stringResource(R.string.dashboard_starts_in, DurationFormatter.short(startsIn.plusSeconds(SECONDS_ROUND_UP)))
                    else -> next.dayLabel()
                },
                style = LockdownType.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = Tokens.accent,
                modifier = Modifier.padding(top = 13.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TintButton(stringResource(R.string.action_view_schedule), onOpenSchedules, Modifier.weight(1f))
                TextAction(stringResource(R.string.action_skip_once), { onSkip(next) }, minHeight = 48.dp)
            }
        }
    }
}

@Composable
private fun QuickActions(focusEnabled: Boolean, onFocusNow: () -> Unit, onOpenSchedules: () -> Unit) {
    Column(Modifier.padding(top = Spacing.section)) {
        SectionLabel(stringResource(R.string.dashboard_quick_actions))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton(
                stringResource(R.string.dashboard_focus_now),
                onFocusNow,
                Modifier.weight(1f),
                enabled = focusEnabled,
                icon = LucideIcons.Plus,
            )
            OutlineButton(stringResource(R.string.nav_schedules), onOpenSchedules, Modifier.weight(1f), minHeight = 52.dp)
        }
    }
}

@Composable
private fun TodayList(items: List<TodayItem>) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 28.dp)) {
        SectionLabel(stringResource(R.string.day_today))
        Column(Modifier.padding(top = 4.dp)) {
            items.forEach { item ->
                val stateLabel = stringResource(
                    when (item.state) {
                        TodayItemState.DONE -> R.string.today_state_done
                        TodayItemState.ACTIVE -> R.string.today_state_active
                        TodayItemState.UPCOMING -> R.string.today_state_upcoming
                    },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 13.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${item.name}, $stateLabel, ${DurationFormatter.spoken(item.duration)}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when (item.state) {
                            TodayItemState.DONE -> "✓"
                            TodayItemState.ACTIVE -> "●"
                            TodayItemState.UPCOMING -> "○"
                        },
                        style = LockdownType.bodySmall.copy(fontSize = 13.sp),
                        color = if (item.state == TodayItemState.ACTIVE) Tokens.accent else Tokens.muted,
                        modifier = Modifier.width(14.dp),
                    )
                    Text(item.name, style = LockdownType.body, color = Tokens.text, modifier = Modifier.weight(1f))
                    Text(
                        DurationFormatter.short(item.duration),
                        style = LockdownType.cardTitle.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
                        color = Tokens.muted,
                    )
                }
                HairlineDivider()
            }
        }
    }
}

@Composable
private fun FocusNowDialog(onDismiss: () -> Unit, onConfirm: (Int, Boolean, String) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(FOCUS_NOW_OPTIONS[1]) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    FlDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dashboard_focus_now),
        confirmLabel = stringResource(R.string.action_start_now),
        onConfirm = { onConfirm(minutes, strict, name) },
    ) {
        FieldLabel(stringResource(R.string.editor_name), Modifier.padding(top = 6.dp))
        FlTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            placeholder = stringResource(R.string.focus_now_placeholder),
            background = Tokens.bg,
            minHeight = 46.dp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FOCUS_NOW_OPTIONS.forEach { option ->
                ChoiceChip(
                    label = stringResource(R.string.minutes_short, option),
                    selected = minutes == option,
                    onClick = { minutes = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SwitchRow(stringResource(R.string.editor_strict), strict, { strict = it })
        MutedText(stringResource(R.string.focus_now_warning), style = LockdownType.caption)
    }
}

private val FOCUS_NOW_OPTIONS = listOf(25, 50, 90)
private const val MAX_NAME = 40
private const val PERCENT = 100

/** "Starts in 31m" should not read one minute short while seconds tick. */
private const val SECONDS_ROUND_UP = 59L
const val TAG_STATUS = "dashboard_status"
