package com.example.focuslock.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onOpenApplications: () -> Unit,
    onOpenDeviceSetup: () -> Unit,
    onOpenDeveloper: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val snackbar = remember { SnackbarHostState() }
    val removalText = state.removalResult?.let {
        stringResource(if (it) R.string.settings_owner_removed else R.string.settings_owner_not_removed)
    }
    LaunchedEffect(state.removalResult) {
        if (removalText != null) {
            snackbar.showSnackbar(removalText)
            viewModel.removalResultShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
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
            SectionCard(title = stringResource(R.string.settings_sessions)) {
                OptionChips(
                    label = stringResource(R.string.settings_countdown),
                    options = AppSettings.COUNTDOWN_OPTIONS,
                    selected = state.settings.countdownMinutes,
                    onSelect = viewModel::setCountdownMinutes,
                )
                OptionChips(
                    label = stringResource(R.string.settings_reminder),
                    options = AppSettings.REMINDER_OPTIONS,
                    selected = state.settings.reminderMinutes,
                    onSelect = viewModel::setReminderMinutes,
                )
            }

            if (!state.loading) {
                FocusTextsCard(state.settings, viewModel::saveTexts)
            }

            SectionCard(title = stringResource(R.string.settings_device)) {
                NavigationRow(stringResource(R.string.apps_title), onOpenApplications)
                NavigationRow(stringResource(R.string.setup_title), onOpenDeviceSetup)
                onOpenDeveloper?.let { NavigationRow(stringResource(R.string.developer_title), it) }
            }

            AboutCard()

            if (state.isDeviceOwner) {
                RemoveOwnerCard(enabled = !state.sessionActive, onRemove = viewModel::removeDeviceOwner)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        if (option == 0) {
                            stringResource(R.string.option_off)
                        } else {
                            stringResource(R.string.minutes_short, option)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun FocusTextsCard(settings: AppSettings, onSave: (String, String) -> Unit) {
    var goal by rememberSaveable(settings.dailyGoal) { mutableStateOf(settings.dailyGoal) }
    var motivation by rememberSaveable(settings.motivationalMessage) { mutableStateOf(settings.motivationalMessage) }
    val dirty = goal != settings.dailyGoal || motivation != settings.motivationalMessage
    SectionCard(title = stringResource(R.string.settings_lockdown_screen)) {
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it.take(AppSettings.MAX_TEXT_LENGTH) },
            label = { Text(stringResource(R.string.settings_daily_goal)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = motivation,
            onValueChange = { motivation = it.take(AppSettings.MAX_TEXT_LENGTH) },
            label = { Text(stringResource(R.string.settings_motivation)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { onSave(goal, motivation) }, enabled = dirty) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.md),
    )
}

@Composable
private fun AboutCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.action_show_less else R.string.settings_limitations))
        }
        if (expanded) {
            Text(
                stringResource(R.string.settings_limitations_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemoveOwnerCard(enabled: Boolean, onRemove: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.settings_remove_owner)) {
        Text(
            stringResource(R.string.settings_remove_owner_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { confirm = true }, enabled = enabled) {
            Text(stringResource(R.string.settings_remove_owner_action), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_remove_owner),
            message = stringResource(R.string.settings_remove_owner_confirm),
            confirmLabel = stringResource(R.string.settings_remove_owner_action),
            destructive = true,
            onConfirm = {
                confirm = false
                onRemove()
            },
            onDismiss = { confirm = false },
        )
    }
}
