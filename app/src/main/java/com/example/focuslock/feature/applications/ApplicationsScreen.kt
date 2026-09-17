package com.example.focuslock.feature.applications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.InstalledApplication
import com.example.focuslock.ui.components.FocusTopBar
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.theme.Spacing

@Composable
fun ApplicationsRoute(onBack: () -> Unit, viewModel: ApplicationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Scaffold(
        topBar = { FocusTopBar(stringResource(R.string.apps_title), onBack) },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = state.dirty,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) { Text(stringResource(R.string.action_save)) }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item {
                SectionCard {
                    Text(stringResource(R.string.apps_explanation), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.apps_essential_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    label = { Text(stringResource(R.string.apps_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                )
            }
            items(state.visible, key = { it.packageName }) { app ->
                AppRow(app, checked = app.essential || app.packageName in state.selected) {
                    viewModel.toggle(app.packageName)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApplication, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .toggleable(value = checked, enabled = !app.essential, role = Role.Checkbox) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = !app.essential)
        Column(Modifier.padding(start = Spacing.sm)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            if (app.essential) {
                Text(
                    stringResource(R.string.apps_always_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
