package com.example.focuslock.feature.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.R
import com.example.focuslock.domain.model.InstalledApplication
import com.example.focuslock.ui.components.BackHeader
import com.example.focuslock.ui.components.FlCheckbox
import com.example.focuslock.ui.components.FlTextField
import com.example.focuslock.ui.components.HairlineDivider
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.components.MutedText
import com.example.focuslock.ui.components.PrimaryButton
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens

@Composable
fun ApplicationsRoute(onBack: () -> Unit, viewModel: ApplicationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        BackHeader(stringResource(R.string.apps_title), onBack)
        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tokens.accentSolid)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 10.dp, bottom = 20.dp),
            ) {
                item {
                    Text(stringResource(R.string.apps_explanation), style = LockdownType.body.copy(fontSize = 14.sp), color = Tokens.text)
                    MutedText(
                        stringResource(R.string.apps_essential_explanation),
                        Modifier.padding(top = 8.dp),
                        LockdownType.caption.copy(lineHeight = 20.sp),
                    )
                    FlTextField(
                        value = state.query,
                        onValueChange = viewModel::search,
                        placeholder = stringResource(R.string.apps_search),
                        leadingIcon = LucideIcons.Search,
                        minHeight = 48.dp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(state.visible, key = { it.packageName }) { app ->
                    AppRow(app, checked = app.essential || app.packageName in state.selected) {
                        viewModel.toggle(app.packageName)
                    }
                    HairlineDivider()
                }
            }
        }
        Column(Modifier.background(Tokens.bg)) {
            HairlineDivider()
            PrimaryButton(
                stringResource(R.string.action_save),
                viewModel::save,
                Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 12.dp, bottom = 18.dp),
                enabled = state.dirty,
                textStyle = LockdownType.button.copy(fontSize = 14.sp),
            )
        }
    }
}

@Composable
private fun AppRow(app: InstalledApplication, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = !app.essential, role = Role.Checkbox) { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        FlCheckbox(checked)
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = LockdownType.body,
                color = Tokens.text,
                modifier = Modifier.alpha(if (app.essential) ESSENTIAL_ALPHA else 1f),
            )
            Text(
                stringResource(
                    when {
                        app.essential -> R.string.apps_always_available
                        checked -> R.string.apps_allowed_during
                        else -> R.string.apps_blocked_during
                    },
                ),
                style = LockdownType.caption.copy(fontSize = 12.sp),
                color = if (app.essential) Tokens.accent else Tokens.muted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

private const val ESSENTIAL_ALPHA = 0.6f
