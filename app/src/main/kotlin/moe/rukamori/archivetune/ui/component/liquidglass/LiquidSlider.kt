/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid Glass slider — same look and feel as Kyant0/AndroidLiquidGlass (Apache-2.0)
 * catalog/components/LiquidSlider.kt: 6dp track, 40x24 glass knob, 1.5x balloon + lens +
 * chromatic aberration while held, velocity squash. Material-compatible signature so the
 * whole app can use it through an import alias.
 *
 * Implemented without saved layer backdrops / custom layout modifiers, so it cannot blow up
 * on unbounded constraints or detached layers and does no work during the layout phase.
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

private val SliderCapsuleShape = Capsule()

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
    val stepSize = if (steps > 0) (valueRange.endInclusive - valueRange.start) / (steps + 1) else 0f
    LiquidSliderImpl(
        valueProvider = { value },
        onValueChange = { v ->
            val snapped =
                if (stepSize > 0f) {
                    valueRange.start + ((v - valueRange.start) / stepSize).roundToInt() * stepSize
                } else {
                    v
                }
            onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
        },
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
    val stepSize = if (state.steps > 0) (valueRange.endInclusive - valueRange.start) / (state.steps + 1) else 0f
    LiquidSliderImpl(
        valueProvider = { state.value },
        onValueChange = { v ->
            val snapped =
                if (stepSize > 0f) {
                    valueRange.start + ((v - valueRange.start) / stepSize).roundToInt() * stepSize
                } else {
                    v
                }
            state.onValueChange?.invoke(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
        },
        onValueChangeFinished = null,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
    )
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
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.24f) else Color(0xFF787880).copy(0.4f)
    val backdrop = LocalGlassBackdrop.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val span = (valueRange.endInclusive - valueRange.start).let { if (it > 0f && it.isFinite()) it else 1f }
    fun toProgress(value: Float): Float = ((value - valueRange.start) / span).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    fun toValue(progress: Float): Float = valueRange.start + span * progress.coerceIn(0f, 1f)

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

        val gesture =
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
                            }
                            currentOnFinished?.invoke()
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
                }

        // track + fill, painted (no layout work, no saved layers)
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

        // glass knob
        Box(
            Modifier
                .graphicsLayer {
                    val px = trackWidthPx
                    val widthPx = size.width
                    val x = (px * progress.value - widthPx / 2f)
                        .coerceIn(-widthPx / 4f, px - widthPx * 3f / 4f)
                    translationX = if (isLtr) x else px - widthPx - x
                    val pressed = press.value
                    val base = 1f + 0.5f * pressed
                    scaleX = (base + stretch.value) * 1f
                    scaleY = (base - abs(stretch.value) * 0.4f).coerceIn(0.8f, 1.6f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { SliderCapsuleShape },
                    effects = {
                        val pressed = press.value
                        blur(8f.dp.toPx() * (1f - pressed))
                        lens(
                            10f.dp.toPx() * pressed,
                            14f.dp.toPx() * pressed,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = 0.4f + 0.6f * press.value,
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.06f)) },
                    innerShadow = {
                        val pressed = press.value
                        InnerShadow(radius = 4.dp * pressed, alpha = pressed)
                    },
                    onDrawSurface = {
                        // white glass knob; the accent track passes "through" it
                        drawRect(Color.White.copy(alpha = 0.92f))
                        val knobWidth = size.width
                        val accentUntil = (knobWidth / 2f).coerceIn(0f, knobWidth)
                        if (progress.value > 0.001f) {
                            drawRect(
                                color = accentColor.copy(alpha = 0.9f),
                                topLeft = Offset(0f, 0f),
                                size = Size(accentUntil, size.height),
                            )
                        }
                    },
                )
                .size(40.dp, 24.dp),
        )

        // gesture surface on top so the whole row is draggable / tappable
        Box(gesture)
    }
}
