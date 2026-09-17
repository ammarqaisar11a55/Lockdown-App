package com.example.focuslock.feature.lockdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.ui.theme.CountdownTextStyle
import com.example.focuslock.ui.theme.Spacing

@Composable
fun LockdownScreen(
    uiState: LockdownUiState,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (uiState) {
            LockdownUiState.Loading, LockdownUiState.NotLocked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LockdownUiState.Locked -> LockdownContent(uiState.display, onLaunchApp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockdownContent(display: LockdownDisplay, onLaunchApp: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusRow(display)
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = stringResource(R.string.lockdown_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.lg))

        val spokenRemaining = stringResource(R.string.lockdown_remaining_spoken, DurationFormatter.spoken(display.remaining))
        Text(
            text = DurationFormatter.clock(display.remaining),
            style = CountdownTextStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .testTag(TAG_REMAINING)
                // Speak a coarse summary instead of a value that changes every second.
                .clearAndSetSemantics { contentDescription = spokenRemaining },
        )
        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = display.sessionName.ifBlank { stringResource(R.string.lockdown_session_active) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.lockdown_ends_at, display.endTime),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (display.strictMode) {
            Text(
                text = stringResource(R.string.lockdown_strict),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        if (display.dailyGoal.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(R.string.lockdown_goal_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(display.dailyGoal, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        if (display.motivationalMessage.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = display.motivationalMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }

        if (display.allowedApps.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                text = stringResource(R.string.lockdown_allowed_apps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                display.allowedApps.forEach { app ->
                    OutlinedButton(onClick = { onLaunchApp(app.packageName) }) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        if (display.enforcement == EnforcementLevel.SCREEN_PINNING) {
            Text(
                text = stringResource(R.string.lockdown_pinning_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(
            text = stringResource(R.string.lockdown_emergency_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusRow(display: LockdownDisplay) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = display.currentTime,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        display.batteryPercent?.let {
            Text(
                text = stringResource(R.string.lockdown_battery, it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

const val TAG_REMAINING = "lockdown_remaining"
