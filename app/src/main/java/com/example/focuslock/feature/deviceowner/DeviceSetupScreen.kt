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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.Capability
import com.example.focuslock.domain.model.CapabilityId
import com.example.focuslock.domain.model.CapabilityStatus
import com.example.focuslock.ui.components.BackHeader
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.FlSnackbarHost
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.OutlineButton
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.components.StatusDot
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.components.statusLabel
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Radii
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens
import kotlinx.coroutines.launch

@Composable
fun DeviceSetupRoute(onBack: () -> Unit, viewModel: DeviceSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.message_command_copied)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        containerColor = Tokens.bg,
        // Insets are applied explicitly by the content (the app bar lives in the parent scaffold).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { FlSnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            BackHeader(stringResource(R.string.setup_title), onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screen, end = Spacing.screen, top = 10.dp, bottom = 30.dp),
            ) {
                state.capabilities?.let { capabilities ->
                    SectionLabel(stringResource(R.string.setup_status))
                    FlCard(Modifier.padding(top = 11.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                        capabilities.capabilities.forEach { capability ->
                            CapabilityRow(
                                capability = capability,
                                onFix = fixAction(capability, context) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            )
                            HairlineDivider()
                        }
                        Text(
                            stringResource(if (capabilities.isReady) R.string.setup_ready else R.string.setup_limited),
                            style = LockdownType.cardTitle.copy(fontSize = 15.5.sp),
                            color = Tokens.text,
                            modifier = Modifier.padding(top = 14.dp).testTag(TAG_READINESS),
                        )
                        capabilities.manufacturerNote?.let {
                            Text(it, style = LockdownType.caption, color = Tokens.warn, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (capabilities.supportedRestrictions.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.setup_restrictions), Modifier.padding(top = 22.dp))
                        CodeBox(capabilities.supportedRestrictions.joinToString("\n"), color = Tokens.muted, modifier = Modifier.padding(top = 11.dp))
                    }
                }

                Heading(stringResource(R.string.setup_how_title))
                Paragraph(stringResource(R.string.setup_how_body))

                Heading(stringResource(R.string.setup_dev_title))
                Paragraph(stringResource(R.string.setup_dev_steps))
                val clipboard = LocalClipboardManager.current
                CodeBox(state.provisionCommand, modifier = Modifier.padding(top = 9.dp)) {
                    OutlineButton(
                        stringResource(R.string.action_copy),
                        {
                            clipboard.setText(AnnotatedString(state.provisionCommand))
                            scope.launch { snackbar.showSnackbar(copiedText) }
                        },
                        Modifier.padding(top = 10.dp),
                        minHeight = 38.dp,
                        icon = LucideIcons.Copy,
                        textStyle = LockdownType.button.copy(fontSize = 11.5.sp),
                    )
                }
                Paragraph(stringResource(R.string.setup_dev_verify), Modifier.padding(top = 8.dp))
                CodeBox(state.verifyCommand, modifier = Modifier.padding(top = 9.dp))
                MutedText(stringResource(R.string.setup_dev_notes), Modifier.padding(top = 12.dp), LockdownType.caption.copy(lineHeight = 20.sp))

                Heading(stringResource(R.string.setup_production_title))
                Paragraph(stringResource(R.string.setup_production_body))

                Heading(stringResource(R.string.setup_safety_title))
                Paragraph(stringResource(R.string.setup_safety_body))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = LockdownType.cardTitle.copy(fontSize = 16.5.sp), color = Tokens.text, modifier = Modifier.padding(top = 22.dp))
}

@Composable
private fun Paragraph(text: String, modifier: Modifier = Modifier) {
    MutedText(text, modifier.padding(top = 6.dp), LockdownType.bodySmall.copy(lineHeight = 22.sp))
}

@Composable
private fun CodeBox(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Tokens.accent,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Tokens.surface2, RoundedCornerShape(Radii.control))
            .border(1.dp, Tokens.lineSoft, RoundedCornerShape(Radii.control))
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        SelectionContainer { Text(text, style = LockdownType.mono, color = color) }
        footer()
    }
}

@Composable
private fun CapabilityRow(capability: Capability, onFix: (() -> Unit)?) {
    val title = capabilityTitle(capability.id)
    val status = statusLabel(capability.status)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        StatusDot(capability.status, Modifier.padding(top = 5.dp))
        Column(
            Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = "$title, $status. ${capability.detail}" },
        ) {
            Text(title, style = LockdownType.body.copy(fontSize = 14.5.sp), color = Tokens.text)
            MutedText(capability.detail, Modifier.padding(top = 2.dp), LockdownType.caption)
        }
        if (onFix != null && capability.status != CapabilityStatus.OK) {
            TextAction(
                stringResource(R.string.action_fix).uppercase(),
                onFix,
                color = Tokens.accent,
                style = LockdownType.button.copy(fontSize = 12.sp),
                minHeight = 36.dp,
            )
        }
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
        CapabilityId.PINNING_LOCK -> R.string.cap_title_pinning_lock
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
        CapabilityId.PINNING_LOCK -> { { context.openSettings(Intent(Settings.ACTION_SECURITY_SETTINGS)) } }
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
