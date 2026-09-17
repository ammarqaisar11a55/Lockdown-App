package com.example.focuslock.feature.lockdown

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.ui.theme.BodyFont
import com.example.focuslock.ui.theme.CountdownTextStyle
import com.example.focuslock.ui.theme.LockdownScreenColors
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Radii
import java.time.Duration

@Composable
fun LockdownScreen(
    uiState: LockdownUiState,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(LockdownScreenColors.ground)) {
        when (uiState) {
            LockdownUiState.Loading, LockdownUiState.NotLocked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LockdownScreenColors.accent)
            }
            is LockdownUiState.Locked -> {
                BreathingGlow()
                LockdownContent(uiState.display, onLaunchApp)
            }
        }
    }
}

/** Slow ambient glow. Static when the user has turned system animations off. */
@Composable
private fun BreathingGlow() {
    val (alpha, scale) = if (animationsEnabled()) {
        val transition = rememberInfiniteTransition(label = "breathe")
        val spec = infiniteRepeatable<Float>(tween(BREATH_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        val a by transition.animateFloat(GLOW_MIN_ALPHA, GLOW_MAX_ALPHA, spec, label = "glowAlpha")
        val s by transition.animateFloat(1f, GLOW_MAX_SCALE, spec, label = "glowScale")
        a to s
    } else {
        GLOW_MIN_ALPHA to 1f
    }
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * GLOW_CENTER_Y)
        val radius = GLOW_RADIUS.toPx() * scale
        drawCircle(
            brush = Brush.radialGradient(
                0f to LockdownScreenColors.glow.copy(alpha = GLOW_CORE_ALPHA * alpha),
                GLOW_FADE_STOP to LockdownScreenColors.glow.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockdownContent(display: LockdownDisplay, onLaunchApp: (String) -> Unit) {
    val colors = LockdownScreenColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val statusStyle = LockdownType.caption.copy(fontSize = 12.sp, letterSpacing = 0.04.em, fontFeatureSettings = "tnum")
            Text(display.currentTime, style = statusStyle, color = colors.dim)
            display.batteryPercent?.let {
                Text(stringResource(R.string.lockdown_battery, it), style = statusStyle, color = colors.dim)
            }
        }

        Text(
            stringResource(R.string.app_name).uppercase(),
            style = LockdownType.sectionLabel.copy(letterSpacing = 0.34.em),
            color = colors.label,
            modifier = Modifier.padding(top = 34.dp),
        )
        Text(
            text = stringResource(R.string.lockdown_title),
            style = LockdownType.sectionLabel.copy(fontSize = 12.5.sp, letterSpacing = 0.46.em),
            color = colors.accent,
            modifier = Modifier.padding(top = 40.dp).semantics { heading() },
        )

        val spokenRemaining = stringResource(R.string.lockdown_remaining_spoken, DurationFormatter.spoken(display.remaining))
        Text(
            text = DurationFormatter.clock(display.remaining),
            style = CountdownTextStyle,
            color = colors.clock,
            modifier = Modifier
                .padding(top = 20.dp)
                .testTag(TAG_REMAINING)
                // Speak a coarse summary instead of a value that changes every second.
                .clearAndSetSemantics { contentDescription = spokenRemaining },
        )
        SessionProgress(display.remaining, display.total, Modifier.padding(top = 24.dp, start = 10.dp, end = 10.dp))
        Text(
            stringResource(
                R.string.lockdown_remaining_of,
                DurationFormatter.short(display.remaining),
                DurationFormatter.short(display.total),
            ),
            style = LockdownType.caption.copy(fontFeatureSettings = "tnum"),
            color = colors.dim,
            modifier = Modifier.padding(top = 14.dp).clearAndSetSemantics { },
        )

        Text(
            text = display.sessionName.ifBlank { stringResource(R.string.lockdown_session_active) },
            style = LockdownType.cardTitle.copy(fontSize = 22.sp),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.lockdown_ends_at, display.endTime),
            style = LockdownType.body.copy(fontSize = 14.sp),
            color = colors.dim,
            modifier = Modifier.padding(top = 4.dp),
        )
        FocusedIndicator(display.strictMode, Modifier.padding(top = 26.dp))

        Text(
            stringResource(R.string.lockdown_tagline),
            style = LockdownType.body.copy(fontSize = 14.sp),
            color = colors.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 28.dp).widthIn(max = 250.dp),
        )

        if (display.dailyGoal.isNotBlank()) {
            Text(
                text = stringResource(R.string.lockdown_goal_label).uppercase(),
                style = LockdownType.sectionLabel.copy(fontSize = 10.5.sp, letterSpacing = 0.22.em),
                color = colors.label,
                modifier = Modifier.padding(top = 30.dp),
            )
            Text(
                display.dailyGoal,
                style = LockdownType.body,
                color = colors.goal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (display.motivationalMessage.isNotBlank()) {
            Text(
                text = display.motivationalMessage,
                style = LockdownType.body.copy(fontSize = 14.5.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp).widthIn(max = 270.dp),
            )
        }

        Spacer(Modifier.height(36.dp))
        if (display.allowedApps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.lockdown_allowed_apps).uppercase(),
                style = LockdownType.sectionLabel.copy(fontSize = 10.5.sp, letterSpacing = 0.22.em),
                color = colors.label,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                display.allowedApps.forEach { app ->
                    AppButton(app.label) { onLaunchApp(app.packageName) }
                }
            }
        }

        if (display.enforcement == EnforcementLevel.SCREEN_PINNING) {
            Text(
                text = stringResource(R.string.lockdown_pinning_note),
                style = LockdownType.caption.copy(fontSize = 11.5.sp),
                color = colors.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        Text(
            text = stringResource(R.string.lockdown_emergency_note),
            style = LockdownType.caption.copy(fontSize = 11.5.sp),
            color = colors.fine,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/** Hairline track with a tick marking how much of the session has passed. */
@Composable
private fun SessionProgress(remaining: Duration, total: Duration, modifier: Modifier = Modifier) {
    val fraction = if (total.isZero) 1f else (1f - remaining.toMillis().toFloat() / total.toMillis()).coerceIn(0f, 1f)
    BoxWithConstraints(modifier.fillMaxWidth().height(9.dp).clearAndSetSemantics { }) {
        val filled = maxWidth * fraction
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(LockdownScreenColors.track))
        Box(Modifier.padding(top = 4.dp).width(filled).height(1.dp).background(LockdownScreenColors.accent))
        Box(
            Modifier
                .padding(start = filled.coerceAtMost(maxWidth - 1.dp))
                .size(width = 1.dp, height = 9.dp)
                .background(LockdownScreenColors.accent),
        )
    }
}

@Composable
private fun FocusedIndicator(strict: Boolean, modifier: Modifier = Modifier) {
    val pulse = if (animationsEnabled()) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            PULSE_MIN_ALPHA,
            1f,
            infiniteRepeatable(tween(PULSE_HALF_MS), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        value
    } else {
        1f
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(6.dp).alpha(pulse).background(LockdownScreenColors.accent, CircleShape))
        Text(
            stringResource(if (strict) R.string.lockdown_strict else R.string.lockdown_focused).uppercase(),
            style = LockdownType.sectionLabel.copy(fontSize = 11.5.sp, letterSpacing = 0.24.em),
            color = LockdownScreenColors.soft,
        )
    }
}

@Composable
private fun AppButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radii.control)
    Box(
        Modifier
            .heightIn(min = 42.dp)
            .border(1.dp, LockdownScreenColors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = LockdownType.bodySmall.copy(fontFamily = BodyFont),
            color = LockdownScreenColors.goal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val BREATH_HALF_MS = 5_000
private const val PULSE_HALF_MS = 2_500
private const val GLOW_MIN_ALPHA = 0.26f
private const val GLOW_MAX_ALPHA = 0.58f
private const val GLOW_MAX_SCALE = 1.09f
private const val GLOW_CORE_ALPHA = 0.62f
private const val GLOW_FADE_STOP = 0.64f
private const val GLOW_CENTER_Y = 0.30f
private const val PULSE_MIN_ALPHA = 0.4f
private val GLOW_RADIUS = 320.dp

const val TAG_REMAINING = "lockdown_remaining"
