package com.example.focuslock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.focuslock.R
import com.example.focuslock.domain.model.CapabilityStatus
import com.example.focuslock.ui.theme.BodyFont
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Radii
import com.example.focuslock.ui.theme.Spacing
import com.example.focuslock.ui.theme.Tokens

// ── Text ──────────────────────────────────────────────────────────────────────────────────────

/** Small uppercase heading used above every section ("NEXT LOCKDOWN"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Tokens.faint) {
    Text(
        text = text.uppercase(),
        style = LockdownType.sectionLabel,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/** Kept for existing call sites; same treatment as [SectionLabel]. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = SectionLabel(text, modifier)

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LockdownType.screenTitle,
        color = Tokens.text,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, style: TextStyle = LockdownType.bodySmall) {
    Text(text, style = style, color = Tokens.muted, modifier = modifier)
}

@Composable
fun MetricText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = LockdownType.cardTitle.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"), color = Tokens.text)
        Text(label, style = LockdownType.caption.copy(fontSize = 12.sp), color = Tokens.muted)
    }
}

// ── Surfaces ──────────────────────────────────────────────────────────────────────────────────

/** Rounded, hairline-bordered surface card. */
@Composable
fun FlCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Tokens.line,
    background: Color = Tokens.surface,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Radii.card))
            .border(1.dp, borderColor, RoundedCornerShape(Radii.card))
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Section card with an optional label, as used across Settings and the dashboard. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SectionLabel(title)
            Spacer(Modifier.size(11.dp))
        }
        FlCard(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
    }
}

/**
 * Blueprint frame: square, transparent, hairline-bordered, with "+" registration marks drawn
 * just outside each corner.
 */
fun Modifier.blueprint(line: Color, marks: Color): Modifier = this
    .border(1.dp, line)
    .drawWithContent {
        drawContent()
        val arm = 5.5.dp.toPx()
        val stroke = 1.dp.toPx()
        listOf(
            Offset(0f, 0f),
            Offset(size.width, 0f),
            Offset(0f, size.height),
            Offset(size.width, size.height),
        ).forEach { c ->
            drawLine(marks, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke)
            drawLine(marks, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke)
        }
    }

@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = Tokens.lineSoft) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

// ── Buttons ───────────────────────────────────────────────────────────────────────────────────

private enum class ButtonKind { PRIMARY, OUTLINE, TINT, DANGER }

/** Slight shrink while pressed, as in the design. */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, label = "press")
    return scale(scale)
}

@Composable
private fun DesignButton(
    text: String,
    onClick: () -> Unit,
    kind: ButtonKind,
    modifier: Modifier,
    enabled: Boolean,
    minHeight: Dp,
    icon: ImageVector?,
    textStyle: TextStyle,
) {
    val source = remember { MutableInteractionSource() }
    val colors = Tokens
    val (container, content, border) = when (kind) {
        ButtonKind.PRIMARY -> Triple(colors.accentSolid, colors.onAccent, null)
        ButtonKind.OUTLINE -> Triple(Color.Transparent, colors.text, colors.line)
        ButtonKind.TINT -> Triple(colors.accentTint, colors.accent, null)
        ButtonKind.DANGER -> Triple(Color.Transparent, colors.err, colors.err)
    }
    val shape = RoundedCornerShape(Radii.control)
    Row(
        modifier = modifier
            .pressScale(source)
            .heightIn(min = minHeight)
            .background(container.copy(alpha = if (enabled) container.alpha else container.alpha * DISABLED_ALPHA), shape)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) content else content.copy(alpha = DISABLED_ALPHA)
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text.uppercase(), style = textStyle, color = tint, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 52.dp,
    icon: ImageVector? = null,
    textStyle: TextStyle = LockdownType.button,
) = DesignButton(text, onClick, ButtonKind.PRIMARY, modifier, enabled, minHeight, icon, textStyle)

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    icon: ImageVector? = null,
    textStyle: TextStyle = LockdownType.button,
) = DesignButton(text, onClick, ButtonKind.OUTLINE, modifier, enabled, minHeight, icon, textStyle)

@Composable
fun TintButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) =
    DesignButton(text, onClick, ButtonKind.TINT, modifier, true, minHeight, null, LockdownType.button)

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    DesignButton(text, onClick, ButtonKind.DANGER, modifier, enabled, 48.dp, null, LockdownType.button.copy(fontSize = 12.5.sp))

/** Borderless body-font action ("Skip this one", "Not now", "Edit"). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tokens.muted,
    style: TextStyle = LockdownType.bodySmall,
    minHeight: Dp = 44.dp,
    textAlign: TextAlign = TextAlign.Center,
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = if (textAlign == TextAlign.Start) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(text, style = style, color = color, textAlign = textAlign)
    }
}

@Composable
fun IconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = Tokens.text) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Back arrow plus title, used by secondary screens. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconAction(LucideIcons.ChevronLeft, stringResource(R.string.action_back), onBack)
        Text(
            title,
            style = LockdownType.headerTitle,
            color = Tokens.text,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Kept for screens that only need a back header. */
@Composable
fun FocusTopBar(title: String, onBack: (() -> Unit)? = null) {
    if (onBack != null) {
        BackHeader(title, onBack)
    } else {
        ScreenTitle(title, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md))
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────────────────────

/** 46×27 switch from the design. Decorative: the parent row carries toggle semantics. */
@Composable
fun FlSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Tokens
    val track by animateColorAsState(if (checked) colors.accentSolid else colors.surface2, label = "track")
    val knob by animateDpAsState(if (checked) 19.dp else 0.dp, label = "knob")
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 27.dp)
            .background(track, RoundedCornerShape(14.dp))
            .border(1.dp, if (checked) track else colors.line, RoundedCornerShape(14.dp)),
    ) {
        Box(
            Modifier
                .padding(start = 2.dp, top = 2.dp)
                .offset { IntOffset(knob.roundToPx(), 0) }
                .size(21.dp)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.lineSoft, CircleShape),
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LockdownType.body, color = Tokens.text)
            if (subtitle != null) {
                Text(subtitle, style = LockdownType.caption, color = Tokens.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        FlSwitch(checked)
    }
}

/** Outlined choice that fills with the accent tint when selected. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    textStyle: TextStyle = LockdownType.bodySmall.copy(fontSize = 14.sp),
    textAlign: TextAlign = TextAlign.Center,
    role: Role = Role.RadioButton,
) {
    val colors = Tokens
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radii.control)
    val fill by animateColorAsState(if (selected) colors.accentTint else Color.Transparent, label = "chipFill")
    val edge by animateColorAsState(if (selected) colors.accentSolid else colors.line, label = "chipEdge")
    Box(
        modifier = modifier
            .pressScale(source, 0.96f)
            .heightIn(min = minHeight)
            .background(fill, shape)
            .border(1.dp, edge, shape)
            .selectable(selected = selected, interactionSource = source, indication = null, role = role, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = if (textAlign == TextAlign.Start) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(label, style = textStyle, color = colors.text, textAlign = textAlign, maxLines = 1)
    }
}

/** 21dp rounded checkbox from the allowed-apps list. Decorative. */
@Composable
fun FlCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Tokens
    val fill by animateColorAsState(if (checked) colors.accentSolid else Color.Transparent, label = "check")
    Box(
        modifier = modifier
            .size(21.dp)
            .background(fill, RoundedCornerShape(4.dp))
            .border(1.5.dp, if (checked) fill else colors.line, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(LucideIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun FlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    background: Color = Tokens.surface,
    minHeight: Dp = 50.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = Tokens
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = source,
        keyboardOptions = keyboardOptions,
        textStyle = LockdownType.body.copy(fontSize = 15.5.sp, color = colors.text, fontFamily = BodyFont),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .heightIn(min = minHeight)
                    .background(background, RoundedCornerShape(Radii.control))
                    .border(1.dp, if (focused) colors.accentSolid else colors.line, RoundedCornerShape(Radii.control))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, null, tint = colors.faint, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = LockdownType.body, color = colors.faint)
                    inner()
                }
            }
        },
    )
}

/** Small caption above a field ("Name"). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LockdownType.caption.copy(fontSize = 12.sp), color = Tokens.muted, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
fun StatusDot(status: CapabilityStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        CapabilityStatus.OK -> Tokens.ok
        CapabilityStatus.WARNING -> Tokens.warn
        CapabilityStatus.UNAVAILABLE -> Tokens.err
    }
    Box(modifier.size(9.dp).background(color, CircleShape))
}

@Composable
fun statusLabel(status: CapabilityStatus): String = when (status) {
    CapabilityStatus.OK -> stringResource(R.string.status_ok)
    CapabilityStatus.WARNING -> stringResource(R.string.status_warning)
    CapabilityStatus.UNAVAILABLE -> stringResource(R.string.status_unavailable)
}

/** Outlined label such as "STRICT". */
@Composable
fun OutlineTag(text: String, color: Color = Tokens.warn) {
    Text(
        text.uppercase(),
        style = LockdownType.sectionLabel.copy(fontSize = 10.5.sp, letterSpacing = 0.14.em),
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// ── Dialogs & feedback ────────────────────────────────────────────────────────────────────────

/** Surface dialog with the design's typography and text actions. */
@Composable
fun FlDialog(
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val colors = Tokens
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(Radii.dialog))
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
        ) {
            if (title != null) {
                Text(title, style = LockdownType.headerTitle.copy(fontSize = 21.sp), color = colors.text)
                Spacer(Modifier.size(10.dp))
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                TextAction(dismissLabel, onDismiss, style = LockdownType.bodySmall.copy(fontSize = 14.sp))
                TextAction(
                    confirmLabel.uppercase(),
                    { if (confirmEnabled) onConfirm() },
                    color = if (destructive) colors.err else colors.accent,
                    style = LockdownType.button.copy(letterSpacing = 0.05.em),
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
) {
    FlDialog(
        onDismiss = onDismiss,
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = dismissLabel,
        destructive = destructive,
    ) {
        Text(message, style = LockdownType.bodySmall.copy(fontSize = 14.sp, lineHeight = 22.sp), color = Tokens.muted)
    }
}

/** Dark toast-style snackbar from the design. */
@Composable
fun FlSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            modifier = Modifier.padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(Radii.control),
            containerColor = Tokens.toast,
            contentColor = Tokens.onToast,
        ) { Text(data.visuals.message, style = LockdownType.bodySmall) }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(message, style = LockdownType.bodySmall, color = Tokens.muted, textAlign = TextAlign.Center)
    }
}

private const val DISABLED_ALPHA = 0.45f
