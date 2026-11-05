/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Liquid Glass toggle — same look and feel as Kyant0/AndroidLiquidGlass (Apache-2.0)
 * catalog/components/LiquidToggle.kt: 64x28 track that lerps to iOS green, 40x24 glass knob
 * that balloons + refracts while held, velocity squash. Material-compatible signature.
 *
 * No saved layer backdrops and no layout-phase work, so it is crash-safe inside lazy lists
 * and dialogs and cheap to animate.
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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

private val SwitchCapsuleShape = Capsule()

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
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.24f) else Color(0xFF787880).copy(0.4f)
    val backdrop = LocalGlassBackdrop.current

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

                fun finishDrag() {
                    scope.launch { press.animateTo(0f, spring(0.8f, 500f, 0.001f)) }
                    scope.launch { stretch.animateTo(0f, spring(0.6f, 400f, 0.001f)) }
                    if (abs(accumulated) < 8.dp.toPx()) {
                        // tap
                        val next = !currentChecked
                        fraction = if (next) 1f else 0f
                        scope.launch { position.animateTo(fraction, spring(0.88f, 520f, 0.001f)) }
                        currentOnCheckedChange?.invoke(next)
                    } else {
                        val next = position.value >= 0.5f
                        fraction = if (next) 1f else 0f
                        scope.launch { position.animateTo(fraction, spring(0.88f, 520f, 0.001f)) }
                        currentOnCheckedChange?.invoke(next)
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
                .clip(Capsule())
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
                    val base = 1f + 0.5f * press.value
                    scaleX = base + stretch.value
                    scaleY = (base - abs(stretch.value) * 0.4f).coerceIn(0.8f, 1.6f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { SwitchCapsuleShape },
                    effects = {
                        val pressed = press.value
                        blur(8f.dp.toPx() * (1f - pressed))
                        lens(
                            5f.dp.toPx() * pressed,
                            10f.dp.toPx() * pressed,
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
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.08f)) },
                    innerShadow = {
                        val pressed = press.value
                        InnerShadow(radius = 4.dp * pressed, alpha = pressed)
                    },
                    onDrawSurface = { drawRect(Color.White) },
                )
                .size(40.dp, 24.dp),
        )
    }
}
