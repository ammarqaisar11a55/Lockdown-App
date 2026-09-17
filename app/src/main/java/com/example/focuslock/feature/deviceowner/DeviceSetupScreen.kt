package com.example.focuslock.feature.deviceowner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.Capability
import com.example.focuslock.domain.model.CapabilityId
import com.example.focuslock.domain.model.CapabilityStatus
import com.example.focuslock.ui.components.FocusTopBar
import com.example.focuslock.ui.components.SectionCard
import com.example.focuslock.ui.components.StatusDot
import com.example.focuslock.ui.components.statusLabel
import com.example.focuslock.ui.theme.Spacing

@Composable
fun DeviceSetupRoute(onBack: () -> Unit, viewModel: DeviceSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(topBar = { FocusTopBar(stringResource(R.string.setup_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.capabilities?.let { capabilities ->
                SectionCard(title = stringResource(R.string.setup_status)) {
                    capabilities.capabilities.forEach { capability ->
                        CapabilityRow(
                            capability = capability,
                            onFix = fixAction(capability, context) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                    }
                    Text(
                        stringResource(if (capabilities.isReady) R.string.setup_ready else R.string.setup_limited),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(TAG_READINESS),
                    )
                    capabilities.manufacturerNote?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (capabilities.supportedRestrictions.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.setup_restrictions)) {
                        Text(
                            capabilities.supportedRestrictions.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.setup_how_title)) {
                Text(stringResource(R.string.setup_how_body), style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = stringResource(R.string.setup_dev_title)) {
                Text(stringResource(R.string.setup_dev_steps), style = MaterialTheme.typography.bodyMedium)
                CommandBlock(state.provisionCommand)
                Text(stringResource(R.string.setup_dev_verify), style = MaterialTheme.typography.bodyMedium)
                CommandBlock(state.verifyCommand)
                Text(
                    stringResource(R.string.setup_dev_notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = stringResource(R.string.setup_production_title)) {
                Text(stringResource(R.string.setup_production_body), style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = stringResource(R.string.setup_safety_title)) {
                Text(stringResource(R.string.setup_safety_body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun CapabilityRow(capability: Capability, onFix: (() -> Unit)?) {
    val title = capabilityTitle(capability.id)
    val status = statusLabel(capability.status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(capability.status)
        Column(
            Modifier
                .weight(1f)
                .padding(start = Spacing.sm)
                .clearAndSetSemantics { contentDescription = "$title, $status. ${capability.detail}" },
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(capability.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onFix != null && capability.status != CapabilityStatus.OK) {
            TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
        }
    }
}

@Composable
private fun CommandBlock(command: String) {
    val clipboard = LocalClipboardManager.current
    SelectionContainer {
        Text(
            command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(command)) }) {
        Text(stringResource(R.string.action_copy))
    }
}

@Composable
private fun capabilityTitle(id: CapabilityId): String = stringResource(
    when (id) {
        CapabilityId.DEVICE_OWNER -> R.string.cap_title_device_owner
        CapabilityId.LOCK_TASK -> R.string.cap_title_lock_task
        CapabilityId.BOOT_RECOVERY -> R.string.cap_title_boot
        CapabilityId.NOTIFICATIONS -> R.string.cap_title_notifications
        CapabilityId.EXACT_ALARMS -> R.string.cap_title_exact_alarms
        CapabilityId.APPLICATION_MANAGEMENT -> R.string.cap_title_app_management
        CapabilityId.AUTOMATIC_TIME -> R.string.cap_title_time
        CapabilityId.BATTERY -> R.string.cap_title_battery
    },
)

private fun fixAction(capability: Capability, context: Context, requestNotifications: () -> Unit): (() -> Unit)? =
    when (capability.id) {
        CapabilityId.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            {
                context.openSettings(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }
        } else {
            null
        }
        CapabilityId.NOTIFICATIONS -> {
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications()
                } else {
                    context.openSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
        }
        CapabilityId.BATTERY -> { { context.openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        else -> null
    }

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

const val TAG_READINESS = "setup_readiness"
