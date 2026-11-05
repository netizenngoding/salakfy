/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid Glass slider, matching Kyant0/AndroidLiquidGlass (Apache-2.0) LiquidSlider:
 * 6dp track, 40x24 white-glass knob, 1.5x balloon + chromatic rim while held, velocity squash,
 * drag anywhere + tap to jump.
 *
 * The knob is drawn with plain Compose graphics instead of a backdrop layer: inside settings
 * lists / dialogs a saved backdrop layer is what made the app crash, and on a 40x24 control the
 * refraction is not visible anyway. Look stays the same, the crash vector is gone.
 */

package moe.rukamori.archivetune.ui.component.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LiquidAccentLight = Color(0xFF0088FF)
internal val LiquidAccentDark = Color(0xFF0091FF)
internal val LiquidGreenLight = Color(0xFF34C759)
internal val LiquidGreenDark = Color(0xFF30D158)

@Composable
internal fun isLightLiquidTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    thumb: (@Composable (SliderState) -> Unit)? = null,
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    LiquidSliderImpl(
        valueProvider = { value },
        onValueChange = { v -> onValueChange(snap(v, valueRange, steps)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidSlider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    thumb: (@Composable (SliderState) -> Unit)? = null,
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    val valueRange = state.valueRange
    LiquidSliderImpl(
        valueProvider = { state.value },
        onValueChange = { v -> state.onValueChange?.invoke(snap(v, valueRange, state.steps)) },
        onValueChangeFinished = null,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
    )
}

private fun snap(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val stepSize = if (steps > 0 && end > start) (end - start) / (steps + 1) else 0f
    val snapped =
        if (stepSize > 0f) start + ((value - start) / stepSize).roundToInt() * stepSize else value
    return if (snapped.isFinite()) snapped.coerceIn(start, end) else start
}

@Composable
private fun LiquidSliderImpl(
    valueProvider: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isLightTheme = isLightLiquidTheme()
    val accentColor = if (isLightTheme) LiquidAccentLight else LiquidAccentDark
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(alpha = 0.24f) else Color(0xFF787880).copy(alpha = 0.4f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val start = valueRange.start
    val span = (valueRange.endInclusive - start).let { if (it > 0f && it.isFinite()) it else 1f }

    fun toProgress(value: Float): Float =
        ((value - start) / span).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }

    fun toValue(progress: Float): Float = start + span * progress.coerceIn(0f, 1f)

    val progress = remember { Animatable(toProgress(valueProvider()), 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }
    val stretch = remember { Animatable(0f, 0.001f) }

    LaunchedEffect(Unit) {
        snapshotFlow { valueProvider() }
            .collectLatest { value ->
                val target = toProgress(value)
                if (abs(progress.value - target) > 0.001f) {
                    progress.animateTo(target, spring(0.9f, 520f, 0.001f))
                }
            }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat().let { if (it.isFinite() && it > 0f) it else 1f }

        // ---------- track + fill ----------
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val barHeight = 6.dp.toPx()
                    val top = (size.height - barHeight) / 2f
                    val radius = CornerRadius(barHeight / 2f)
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, barHeight),
                        cornerRadius = radius,
                    )
                    val filled = (size.width * progress.value).coerceIn(0f, size.width)
                    if (filled > 0f) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(0f, top),
                            size = Size(filled, barHeight),
                            cornerRadius = radius,
                        )
                    }
                },
        )

        // ---------- glass knob ----------
        Box(
            Modifier
                .graphicsLayer {
                    val widthPx = size.width
                    val x = (trackWidthPx * progress.value - widthPx / 2f)
                        .coerceIn(-widthPx / 4f, trackWidthPx - widthPx * 3f / 4f)
                    translationX = if (isLtr) x else trackWidthPx - widthPx - x
                    val pressed = press.value
                    val base = 1f + 0.5f * pressed
                    scaleX = base + stretch.value
                    scaleY = (base - abs(stretch.value) * 0.4f).coerceIn(0.8f, 1.6f)
                }.drawBehind {
                    val pressed = press.value
                    val radius = CornerRadius(size.height / 2f)
                    val solidTrack = if (isLightTheme) Color(0xFFC7C7CC) else Color(0xFF5A5A60)

                    // drop shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.18f + 0.08f * pressed),
                        topLeft = Offset(0f, 1.5.dp.toPx()),
                        size = size,
                        cornerRadius = radius,
                    )

                    // what the glass is looking at: the track, magnified while held
                    if (pressed > 0.01f) {
                        scale(1f + 0.4f * pressed, 1f + 0.4f * pressed, pivot = center) {
                            drawRect(color = solidTrack, size = size)
                            if (progress.value > 0.001f) {
                                clipRect(right = size.width / 2f) {
                                    drawRect(color = accentColor, size = size)
                                }
                            }
                        }
                    }

                    // glass body: opaque white at rest (demo behaviour), translucent while held
                    drawRoundRect(
                        color = Color.White.copy(alpha = 1f - 0.55f * pressed),
                        cornerRadius = radius,
                    )

                    // top specular sheen
                    drawRoundRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.65f,
                            ),
                        cornerRadius = radius,
                    )

                    // chromatic dispersion rim while held
                    if (pressed > 0.01f) {
                        drawRoundRect(
                            color = Color(0xFF4FA8FF).copy(alpha = 0.5f * pressed),
                            topLeft = Offset(-0.5.dp.toPx(), 0f),
                            size = Size(size.width + 1.dp.toPx(), size.height),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color(0xFFFF6FA8).copy(alpha = 0.45f * pressed),
                            topLeft = Offset(0.5.dp.toPx(), 0f),
                            size = Size(size.width + 1.dp.toPx(), size.height),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }

                    // hairline border
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.07f),
                        cornerRadius = radius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }.size(40.dp, 24.dp),
        )

        // ---------- gesture surface (drag anywhere, tap to jump) ----------
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(span, isLtr, trackWidthPx, enabled) {
                    if (!enabled) return@pointerInput
                    val width = size.width.toFloat().let { if (it > 0f) it else 1f }
                    var startProgress = progress.value
                    var accumulated = 0f
                    var downX = 0f

                    fun jump(x: Float) {
                        val fraction = (x / width).let { if (isLtr) it else 1f - it }.coerceIn(0f, 1f)
                        scope.launch { progress.animateTo(fraction, spring(0.88f, 520f, 0.001f)) }
                        currentOnValueChange(toValue(fraction))
                        currentOnFinished?.invoke()
                    }

                    inspectDragGestures(
                        onDragStart = { down ->
                            startProgress = progress.value
                            accumulated = 0f
                            downX = down.position.x
                            scope.launch { press.animateTo(1f, spring(0.7f, 650f, 0.001f)) }
                        },
                        onDragEnd = {
                            scope.launch { press.animateTo(0f, spring(0.8f, 500f, 0.001f)) }
                            scope.launch { stretch.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                            if (abs(accumulated) < 8.dp.toPx()) {
                                jump(downX)
                            } else {
                                currentOnFinished?.invoke()
                            }
                        },
                        onDragCancel = {
                            scope.launch { press.animateTo(0f, spring(0.8f, 500f, 0.001f)) }
                            scope.launch { stretch.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                        },
                    ) { change, dragAmount ->
                        if (dragAmount == Offset.Zero) return@inspectDragGestures
                        accumulated += dragAmount.x
                        if (abs(accumulated) > 8.dp.toPx()) change.consume()
                        val delta = accumulated / width * (if (isLtr) 1f else -1f)
                        val target = (startProgress + delta).coerceIn(0f, 1f)
                        scope.launch { progress.snapTo(target) }
                        scope.launch {
                            stretch.animateTo(
                                (dragAmount.x / width * 2.5f).coerceIn(-0.2f, 0.2f),
                                spring(0.5f, 900f, 0.001f),
                            )
                        }
                        currentOnValueChange(toValue(target))
                    }
                },
        )
    }
}
