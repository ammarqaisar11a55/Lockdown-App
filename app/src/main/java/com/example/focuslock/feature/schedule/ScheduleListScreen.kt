package com.example.focuslock.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.ui.components.EmptyState
import com.example.focuslock.ui.theme.Spacing

@Composable
fun ScheduleListRoute(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ScheduleListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    ScheduleListScreen(
        state = state,
        error = error,
        onErrorShown = viewModel::errorShown,
        onCreate = onCreate,
        onEdit = onEdit,
        onToggle = viewModel::setEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    state: ScheduleListUiState,
    error: ScheduleValidationError?,
    onErrorShown: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = error?.message()
    LaunchedEffect(error) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            onErrorShown()
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.schedules_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_schedule)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && state.schedules.isEmpty()) {
            EmptyState(stringResource(R.string.schedules_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(state.schedules, key = { it.id }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    enforced = schedule.id == state.enforcedScheduleId,
                    onClick = { onEdit(schedule.id) },
                    onToggle = { onToggle(schedule.id, it) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: FocusSchedule,
    enforced: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !enforced, onClickLabel = stringResource(R.string.action_edit), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatTime(schedule.startTime)} → ${formatTime(schedule.endTime)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val details = buildList {
                    add(schedule.repeat.label())
                    if (schedule.strictMode) add(stringResource(R.string.label_strict))
                    if (!schedule.autoStart) add(stringResource(R.string.label_manual_start))
                    if (enforced) add(stringResource(R.string.label_active_now))
                }
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val stateText = stringResource(if (schedule.enabled) R.string.state_enabled else R.string.state_disabled)
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                enabled = !enforced,
                modifier = Modifier.semantics { stateDescription = stateText },
            )
        }
    }
}
