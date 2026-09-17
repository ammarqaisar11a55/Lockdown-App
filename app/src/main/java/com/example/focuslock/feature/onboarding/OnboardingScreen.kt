package com.example.focuslock.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focuslock.R
import com.example.focuslock.ui.components.OutlineButton
import com.example.focuslock.ui.components.PrimaryButton
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Tokens

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
            .background(Tokens.bg)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 30.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = LockdownType.sectionLabel.copy(letterSpacing = 0.22.em),
                color = Tokens.accent,
            )
            Row(Modifier.fillMaxWidth().padding(top = 38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAGES.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (index <= page) Tokens.accentSolid else Tokens.line),
                    )
                }
            }
            Text(
                text = stringResource(R.string.onboarding_step, page + 1, PAGES.size),
                style = LockdownType.caption.copy(fontSize = 12.sp, letterSpacing = 0.04.em),
                color = Tokens.faint,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(current.title),
                style = LockdownType.screenTitle.copy(fontSize = 34.sp, lineHeight = 37.sp),
                color = Tokens.text,
                modifier = Modifier.padding(top = 20.dp).semantics { heading() },
            )
            Text(
                stringResource(current.body),
                style = LockdownType.body.copy(fontSize = 15.5.sp, lineHeight = 25.sp),
                color = Tokens.muted,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (page == 1) {
                TextAction(
                    stringResource(if (showLearnMore) R.string.action_show_less else R.string.action_learn_more),
                    { showLearnMore = !showLearnMore },
                    color = Tokens.accent,
                    style = LockdownType.body.copy(fontSize = 14.5.sp),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (showLearnMore) {
                    Text(
                        stringResource(R.string.onboarding_learn_more),
                        style = LockdownType.bodySmall.copy(lineHeight = 23.sp),
                        color = Tokens.muted,
                    )
                }
            }
        }

        Column(Modifier.padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (isLast) {
                OutlineButton(stringResource(R.string.action_enable_notifications), onRequestNotifications, Modifier.fillMaxWidth(), minHeight = 52.dp)
                PrimaryButton(stringResource(R.string.action_set_up_device), { onFinish(true) }, Modifier.fillMaxWidth())
                TextAction(stringResource(R.string.action_skip_setup), { onFinish(false) }, Modifier.fillMaxWidth(), minHeight = 48.dp, style = LockdownType.body.copy(fontSize = 14.sp))
            } else {
                PrimaryButton(
                    stringResource(if (page == 0) R.string.action_get_started else R.string.action_continue),
                    { page++ },
                    Modifier.fillMaxWidth(),
                )
            }
            if (page > 0) {
                TextAction(
                    stringResource(R.string.action_back),
                    {
                        page--
                        showLearnMore = false
                    },
                    Modifier.fillMaxWidth(),
                    style = LockdownType.body.copy(fontSize = 14.sp),
                )
            }
        }
    }
}
