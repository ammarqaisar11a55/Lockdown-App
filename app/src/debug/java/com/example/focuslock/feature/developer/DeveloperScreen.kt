package com.example.focuslock.feature.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.ui.components.FocusTopBar
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.theme.Spacing

// Developer screen strings are intentionally not localized: debug builds only.
@Composable
fun DeveloperScreen(onBack: () -> Unit, viewModel: DeveloperViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Scaffold(topBar = { FocusTopBar("Developer tools", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard(title = "Device management") {
                val policy = state.policy
                Mono("Device Owner: ${yesNo(policy?.isDeviceOwner)}")
                Mono("Admin active: ${yesNo(policy?.isAdminActive)}")
                Mono("Lock task permitted: ${yesNo(state.lockTaskPermitted)}")
                Mono("Lock task active: ${yesNo(policy?.lockTaskActive)}")
                Mono("HOME override: ${yesNo(policy?.homeOverrideEnabled)}")
                Mono("Uninstall blocked: ${yesNo(policy?.uninstallBlocked)}")
                Mono("Lock task packages: ${policy?.lockTaskPackages?.sorted()?.joinToString().orEmpty()}")
                Mono("Restrictions: ${policy?.activeRestrictions?.sorted()?.joinToString().orEmpty()}")
            }
            SectionCard(title = "Engine state") {
                val lockdown = state.lockdownState
                Mono("Phase: ${lockdown?.phase}")
                Mono("Session end: ${lockdown?.session?.end}")
                Mono("Enforcement: ${lockdown?.enforcement}")
                Mono("Owned restrictions: ${lockdown?.appliedRestrictions?.sorted()?.joinToString().orEmpty()}")
                state.lastResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            SectionCard(title = "Actions") {
                Action("Start 60 sec test lockdown", viewModel::startTestLockdown)
                Action("Simulate boot recovery", viewModel::simulateBoot)
                Action("Trigger scheduled transition", viewModel::triggerAlarm)
                Action("Create schedule starting in ~2 min", viewModel::scheduleSoon)
                Action("Clear local data", viewModel::reset)
                Action("Refresh", viewModel::refresh)
            }
            SectionCard(title = "Recent log") {
                state.logs.forEach { Mono(it) }
            }
        }
    }
}

@Composable
private fun Mono(text: String) = Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)

@Composable
private fun Action(label: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }

private fun yesNo(value: Boolean?) = when (value) {
    true -> "YES"
    false -> "NO"
    null -> "…"
}
