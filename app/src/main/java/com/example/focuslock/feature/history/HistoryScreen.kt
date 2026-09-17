package com.example.focuslock.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.ui.components.EmptyState
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.components.SectionTitle
import com.example.focuslock.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        if (!state.loading && state.days.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(state.days, key = { it.key }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionTitle(day.title)
                    day.entries.forEach { HistoryCard(it) }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(entry.duration, style = MaterialTheme.typography.titleMedium)
        }
        Text(entry.timeRange, style = MaterialTheme.typography.bodyMedium)
        val status = when (entry.status) {
            SessionStatus.COMPLETED -> stringResource(R.string.history_completed)
            SessionStatus.INTERRUPTED -> stringResource(R.string.history_interrupted)
            SessionStatus.IN_PROGRESS -> stringResource(R.string.history_in_progress)
        }
        val details = buildList {
            add(status)
            if (entry.strictMode) add(stringResource(R.string.label_strict))
            if (entry.recoveries > 0) add(stringResource(R.string.history_recovered, entry.recoveries))
            if (entry.exitAttempts > 0) add(pluralStringResource(R.plurals.history_exit_attempts, entry.exitAttempts, entry.exitAttempts))
        }
        Text(
            details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.status == SessionStatus.COMPLETED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
