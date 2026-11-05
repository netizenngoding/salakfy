/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid Glass slider — 1:1 port of Kyant0/AndroidLiquidGlass (Apache-2.0)
 * catalog/components/LiquidSlider.kt, with a Material-compatible signature so every
 * `Slider(...)` call site in the app can switch to it via an import alias.
 */

package moe.rukamori.archivetune.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
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
            state.onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
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
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val backdrop = LocalGlassBackdrop.current

    val trackBackdrop = rememberLayerBackdrop()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val dampedDragAnimation =
            remember(animationScope, valueRange, trackWidth) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = valueProvider(),
                    valueRange = valueRange,
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 1.5f,
                    onDragStarted = {},
                    onDragStopped = {
                        if (didDrag) {
                            currentOnValueChange(targetValue)
                        }
                        didDrag = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDrag = { _, dragAmount ->
                        if (!didDrag) {
                            didDrag = dragAmount.x != 0f
                        }
                        val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                        currentOnValueChange(
                            (if (isLtr) targetValue + delta else targetValue - delta).coerceIn(valueRange),
                        )
                    },
                )
            }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { valueProvider() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(animationScope, trackWidth) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetPosition =
                                (if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetPosition)
                            currentOnValueChange(targetPosition)
                            currentOnValueChangeFinished?.invoke()
                        }
                    },
            )
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .height(6.dp)
                    .fillMaxWidth(),
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }.then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop =
                        rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 1f, progress)
                                val scaleY = lerp(0f, 1f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            },
                        ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress,
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f),
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    },
                ).size(40.dp, 24.dp),
        )
    }
}
