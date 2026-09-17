package com.example.focuslock.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.usecase.FocusStats
import com.example.focuslock.ui.components.EmptyState
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.MetricText
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.ScreenTitle
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state)
}

@Composable
fun HistoryScreen(state: HistoryUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 6.dp, bottom = 30.dp),
    ) {
        item {
            ScreenTitle(stringResource(R.string.history_title), Modifier.padding(top = 16.dp, bottom = 20.dp))
            Summary(state.stats)
        }
        if (!state.loading && state.days.isEmpty()) {
            item { EmptyState(stringResource(R.string.history_empty)) }
        }
        items(state.days, key = { it.key }) { day ->
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel(
                    when (day.relative) {
                        RelativeDay.TODAY -> stringResource(R.string.day_today)
                        RelativeDay.YESTERDAY -> stringResource(R.string.day_yesterday)
                        RelativeDay.OTHER -> day.title
                    },
                    Modifier.padding(bottom = 1.dp),
                )
                day.entries.forEach { HistoryCard(it) }
            }
        }
    }
}

@Composable
private fun Summary(stats: FocusStats) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricText(stringResource(R.string.metric_week), DurationFormatter.short(stats.weekFocused))
            MetricText(stringResource(R.string.metric_completed), stats.weekCompletedSessions.toString())
            MetricText(stringResource(R.string.metric_exit_attempts), stats.weekExitAttempts.toString())
        }
        HairlineDivider(color = Tokens.line)
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    FlCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.name, style = LockdownType.cardTitle.copy(fontSize = 17.sp), color = Tokens.text, modifier = Modifier.weight(1f))
            Text(entry.duration, style = LockdownType.cardTitle.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"), color = Tokens.text)
        }
        MutedText(entry.timeRange, Modifier.padding(top = 3.dp), LockdownType.bodySmall.copy(fontSize = 13.sp))
        val status = when (entry.status) {
            SessionStatus.COMPLETED -> stringResource(R.string.history_completed)
            SessionStatus.INTERRUPTED -> stringResource(R.string.history_interrupted)
            SessionStatus.IN_PROGRESS -> stringResource(R.string.history_in_progress)
        }
        val details = buildList {
            add(status)
            if (entry.strictMode) add(stringResource(R.string.label_strict))
            if (entry.recoveries > 0) add(stringResource(R.string.history_recovered, entry.recoveries))
            if (entry.exitAttempts > 0) {
                add(pluralStringResource(R.plurals.history_exit_attempts, entry.exitAttempts, entry.exitAttempts))
            }
        }
        Text(
            details.joinToString(" · "),
            style = LockdownType.caption,
            color = if (entry.status == SessionStatus.COMPLETED) Tokens.ok else Tokens.muted,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
