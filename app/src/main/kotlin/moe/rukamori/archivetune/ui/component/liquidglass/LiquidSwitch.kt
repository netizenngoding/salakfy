/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid Glass toggle, matching Kyant0/AndroidLiquidGlass (Apache-2.0) LiquidToggle:
 * 64x28 track lerping to iOS green, 40x24 white-glass knob, 1.5x balloon + chromatic rim while
 * held, velocity squash, drag the knob or tap anywhere.
 *
 * Knob is plain Compose graphics (no backdrop layer) so it is crash-safe in settings lists,
 * lazy lists and dialogs, and does zero work in the layout phase.
 */

package moe.rukamori.archivetune.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
) {
    val isLightTheme = isLightLiquidTheme()
    val accentColor = if (isLightTheme) LiquidGreenLight else LiquidGreenDark
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(alpha = 0.26f) else Color(0xFF787880).copy(alpha = 0.42f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()

    val interactive = enabled && onCheckedChange != null
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val position = remember { Animatable(if (checked) 1f else 0f, 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }
    val stretch = remember { Animatable(0f, 0.001f) }

    LaunchedEffect(Unit) {
        snapshotFlow { checked }
            .collectLatest { isChecked ->
                val target = if (isChecked) 1f else 0f
                fraction = target
                if (abs(position.value - target) > 0.001f) {
                    position.animateTo(target, spring(0.88f, 520f, 0.001f))
                }
            }
    }

    Box(
        modifier
            .size(64.dp, 28.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .semantics { role = Role.Switch }
            .pointerInput(interactive, dragWidth) {
                if (!interactive) return@pointerInput
                var accumulated = 0f

                fun commit(next: Boolean) {
                    fraction = if (next) 1f else 0f
                    scope.launch { position.animateTo(fraction, spring(0.88f, 520f, 0.001f)) }
                    currentOnCheckedChange?.invoke(next)
                }

                fun finishDrag() {
                    scope.launch { press.animateTo(0f, spring(0.8f, 500f, 0.001f)) }
                    scope.launch { stretch.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                    if (abs(accumulated) < 8.dp.toPx()) {
                        commit(!currentChecked)
                    } else {
                        commit(position.value >= 0.5f)
                    }
                }

                inspectDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        scope.launch { press.animateTo(1f, spring(0.7f, 650f, 0.001f)) }
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = {
                        scope.launch { press.animateTo(0f, spring(0.8f, 500f, 0.001f)) }
                        scope.launch { stretch.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                        fraction = if (currentChecked) 1f else 0f
                        scope.launch { position.animateTo(fraction, spring(0.88f, 520f, 0.001f)) }
                    },
                ) { change, dragAmount ->
                    if (dragAmount == Offset.Zero) return@inspectDragGestures
                    accumulated += dragAmount.x
                    if (abs(accumulated) > 8.dp.toPx()) change.consume()
                    val delta = dragAmount.x / dragWidth * (if (isLtr) 1f else -1f)
                    val target = (position.value + delta).coerceIn(0f, 1f)
                    fraction = target
                    scope.launch { position.snapTo(target) }
                    scope.launch {
                        stretch.animateTo(
                            (delta * 0.35f).coerceIn(-0.16f, 0.16f),
                            spring(0.5f, 900f, 0.001f),
                        )
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // track
        Box(
            Modifier
                .size(64.dp, 28.dp)
                .clip(CapsuleShape)
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, position.value))
                },
        )

        // glass knob
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    val x = lerp(padding, padding + dragWidth, position.value)
                    translationX = if (isLtr) x else -x
                    val pressed = press.value
                    val base = 1f + 0.5f * pressed
                    scaleX = base + stretch.value
                    scaleY = (base - abs(stretch.value) * 0.4f).coerceIn(0.8f, 1.6f)
                }
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.18f),
                        topLeft = Offset(0f, 1.5.dp.toPx()),
                        size = size,
                        cornerRadius = radius,
                    )
                    drawRoundRect(color = Color.White, cornerRadius = radius)
                    // the track colour showing "through" the glass
                    if (position.value > 0.001f) {
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.16f + 0.24f * position.value),
                            size = size,
                            cornerRadius = radius,
                        )
                    }
                    drawRoundRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.65f,
                            ),
                        cornerRadius = radius,
                    )
                    val pressed = press.value
                    if (pressed > 0.01f) {
                        drawRoundRect(
                            color = Color(0xFF4FA8FF).copy(alpha = 0.32f * pressed),
                            topLeft = Offset(0.75.dp.toPx(), 0f),
                            size = Size(size.width - 1.5.dp.toPx(), size.height),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color(0xFFFF7AA8).copy(alpha = 0.28f * pressed),
                            topLeft = Offset(0f, 0.75.dp.toPx()),
                            size = Size(size.width - 1.5.dp.toPx(), size.height),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.07f),
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                .size(40.dp, 24.dp),
        )
    }
}

private val CapsuleShape = com.kyant.shapes.Capsule()
