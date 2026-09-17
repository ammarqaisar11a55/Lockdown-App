package com.example.focuslock.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.domain.model.ThemeMode
import com.example.focuslock.ui.components.ChoiceChip
import com.example.focuslock.ui.components.ConfirmDialog
import com.example.focuslock.ui.components.DangerButton
import com.example.focuslock.ui.components.FieldLabel
import com.example.focuslock.ui.components.FlCard
import com.example.focuslock.ui.components.FlSnackbarHost
import com.example.focuslock.ui.components.FlTextField
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.OutlineButton
import com.example.focuslock.ui.components.ScreenTitle
import com.example.focuslock.ui.components.SectionLabel
import com.example.focuslock.ui.components.TextAction
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Radii
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens

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
        containerColor = Tokens.bg,
        // Insets are applied explicitly by the content (the app bar lives in the parent scaffold).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { FlSnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = 6.dp, bottom = 30.dp),
        ) {
            ScreenTitle(stringResource(R.string.settings_title), Modifier.padding(top = 16.dp, bottom = 20.dp))

            SectionLabel(stringResource(R.string.settings_appearance))
            AppearanceOptions(state.settings.themeMode, viewModel::setThemeMode)

            SectionLabel(stringResource(R.string.settings_sessions), Modifier.padding(top = Spacing.section))
            FlCard(Modifier.padding(top = 11.dp)) {
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
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            if (!state.loading) {
                SectionLabel(stringResource(R.string.settings_lockdown_screen), Modifier.padding(top = Spacing.section))
                FocusTextsCard(state.settings, viewModel::saveTexts)
            }

            SectionLabel(stringResource(R.string.settings_device), Modifier.padding(top = Spacing.section))
            FlCard(Modifier.padding(top = 11.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                NavigationRow(stringResource(R.string.apps_title), onOpenApplications)
                HairlineDivider()
                NavigationRow(stringResource(R.string.setup_title), onOpenDeviceSetup)
                onOpenDeveloper?.let {
                    HairlineDivider()
                    NavigationRow(stringResource(R.string.developer_title), it)
                }
            }

            AboutSection()

            if (state.isDeviceOwner) {
                RemoveOwnerSection(enabled = !state.sessionActive, onRemove = viewModel::removeDeviceOwner)
            }
        }
    }
}

private data class ThemeChoice(val mode: ThemeMode, @StringRes val title: Int, @StringRes val subtitle: Int, val icon: ImageVector)

private val THEME_CHOICES = listOf(
    ThemeChoice(ThemeMode.LIGHT, R.string.theme_light, R.string.theme_light_sub, LucideIcons.Sun),
    ThemeChoice(ThemeMode.DARK, R.string.theme_dark, R.string.theme_dark_sub, LucideIcons.Moon),
    ThemeChoice(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_sub, LucideIcons.System),
)

@Composable
private fun AppearanceOptions(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(
        Modifier.padding(top = 11.dp).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        THEME_CHOICES.forEach { choice ->
            val isSelected = choice.mode == selected
            val colors = Tokens
            val fill by animateColorAsState(if (isSelected) colors.accentTint else colors.surface, label = "themeFill")
            val edge by animateColorAsState(if (isSelected) colors.accentSolid else colors.line, label = "themeEdge")
            val shape = RoundedCornerShape(Radii.card)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(fill, shape)
                    .border(1.dp, edge, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(choice.mode) }
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(choice.icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(choice.title), style = LockdownType.cardTitle.copy(fontSize = 16.5.sp), color = colors.text)
                    MutedText(stringResource(choice.subtitle), style = LockdownType.caption)
                }
                Box(
                    Modifier.size(17.dp).border(1.dp, colors.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(11.dp).background(if (isSelected) colors.accentSolid else Color.Transparent, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun OptionChips(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = LockdownType.body.copy(fontSize = 14.5.sp), color = Tokens.text)
        Row(
            Modifier.fillMaxWidth().padding(top = 11.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEach { option ->
                ChoiceChip(
                    label = if (option == 0) stringResource(R.string.option_off) else stringResource(R.string.minutes_short, option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    textStyle = LockdownType.bodySmall.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun FocusTextsCard(settings: AppSettings, onSave: (String, String) -> Unit) {
    var goal by rememberSaveable(settings.dailyGoal) { mutableStateOf(settings.dailyGoal) }
    var motivation by rememberSaveable(settings.motivationalMessage) { mutableStateOf(settings.motivationalMessage) }
    val dirty = goal != settings.dailyGoal || motivation != settings.motivationalMessage
    FlCard(Modifier.padding(top = 11.dp)) {
        FieldLabel(stringResource(R.string.settings_daily_goal))
        FlTextField(
            value = goal,
            onValueChange = { goal = it.take(AppSettings.MAX_TEXT_LENGTH) },
            placeholder = stringResource(R.string.settings_daily_goal_placeholder),
            background = Tokens.bg,
            minHeight = 46.dp,
        )
        FieldLabel(stringResource(R.string.settings_motivation), Modifier.padding(top = 14.dp))
        FlTextField(
            value = motivation,
            onValueChange = { motivation = it.take(AppSettings.MAX_TEXT_LENGTH) },
            background = Tokens.bg,
            minHeight = 46.dp,
        )
        OutlineButton(
            stringResource(R.string.action_save),
            { onSave(goal, motivation) },
            Modifier.padding(top = 14.dp),
            enabled = dirty,
            minHeight = 44.dp,
            textStyle = LockdownType.button.copy(fontSize = 12.5.sp),
        )
    }
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = LockdownType.body, color = Tokens.text, modifier = Modifier.weight(1f))
        Icon(LucideIcons.ChevronRight, null, tint = Tokens.faint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun AboutSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.settings_about), Modifier.padding(top = Spacing.section))
    Text(
        stringResource(R.string.settings_about_body),
        style = LockdownType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
        color = Tokens.text,
        modifier = Modifier.padding(top = 11.dp),
    )
    TextAction(
        stringResource(if (expanded) R.string.action_show_less else R.string.settings_limitations),
        { expanded = !expanded },
        color = Tokens.accent,
        style = LockdownType.body.copy(fontSize = 14.sp),
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (expanded) {
        MutedText(stringResource(R.string.settings_limitations_body), style = LockdownType.bodySmall.copy(fontSize = 13.sp))
    }
}

@Composable
private fun RemoveOwnerSection(enabled: Boolean, onRemove: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 28.dp)) {
        HairlineDivider(color = Tokens.line)
        Text(
            stringResource(R.string.settings_remove_owner),
            style = LockdownType.cardTitle.copy(fontSize = 16.sp),
            color = Tokens.text,
            modifier = Modifier.padding(top = 20.dp),
        )
        MutedText(stringResource(R.string.settings_remove_owner_body), Modifier.padding(top = 5.dp), LockdownType.bodySmall.copy(fontSize = 13.sp))
        DangerButton(
            stringResource(R.string.settings_remove_owner_action),
            { confirm = true },
            Modifier.padding(top = 13.dp),
            enabled = enabled,
        )
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
