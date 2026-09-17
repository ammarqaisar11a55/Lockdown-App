package com.example.focuslock.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focuslock.R
import com.example.focuslock.ui.theme.Spacing

@Composable
fun OnboardingRoute(
    onFinished: (openSetup: Boolean) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    OnboardingScreen(
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onFinish = { openSetup -> viewModel.complete { onFinished(openSetup) } },
    )
}

private data class OnboardingPage(@StringRes val title: Int, @StringRes val body: Int)

private val PAGES = listOf(
    OnboardingPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    OnboardingPage(R.string.onboarding_strong_title, R.string.onboarding_strong_body),
    OnboardingPage(R.string.onboarding_safety_title, R.string.onboarding_safety_body),
)

@Composable
fun OnboardingScreen(
    onRequestNotifications: () -> Unit,
    onFinish: (openSetup: Boolean) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showLearnMore by rememberSaveable { mutableStateOf(false) }
    val current = PAGES[page]
    val isLast = page == PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = stringResource(R.string.onboarding_step, page + 1, PAGES.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(current.title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(current.body), style = MaterialTheme.typography.bodyLarge)

        if (page == 1) {
            TextButton(onClick = { showLearnMore = !showLearnMore }) {
                Text(stringResource(if (showLearnMore) R.string.action_show_less else R.string.action_learn_more))
            }
            if (showLearnMore) {
                Text(
                    stringResource(R.string.onboarding_learn_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        when {
            page == 0 -> Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_get_started))
            }
            !isLast -> Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_continue))
            }
            else -> {
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_enable_notifications))
                }
                Button(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_set_up_device))
                }
                TextButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_skip_setup))
                }
            }
        }
        if (page > 0) {
            TextButton(onClick = { page-- }) { Text(stringResource(R.string.action_back)) }
        }
    }
}
