/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

/**
 * Backdrop (the scrolling app content) that every Liquid Glass surface refracts.
 * Provided in MainActivity via `rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)`.
 * Falls back to an empty backdrop (plain frosted tint) when nothing is provided.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** Real blur needs API 31+, refraction (lens) needs API 33+. */
private val isGlassBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Immutable
data class GlassColors(
    val surface: Color,
    val content: Color,
    val secondaryContent: Color,
    val accent: Color,
    val selection: Color,
    val isDark: Boolean,
)

@Composable
fun rememberGlassColors(pureBlack: Boolean = false): GlassColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(scheme.primary, scheme.onSurface, isDark, pureBlack) {
        val base =
            when {
                pureBlack -> Color.Black
                isDark -> Color(0xFF1C1C1E)
                else -> Color(0xFFF9F9F9)
            }
        val alpha =
            when {
                !isGlassBlurSupported -> 0.94f
                isDark -> 0.48f
                else -> 0.58f
            }
        GlassColors(
            surface = base.copy(alpha = alpha),
            content = if (isDark || pureBlack) Color.White else Color(0xFF1C1C1E),
            secondaryContent = if (isDark || pureBlack) Color(0xFF98989F) else Color(0xFF8A8A8E),
            accent = scheme.primary,
            selection = if (isDark || pureBlack) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f),
            isDark = isDark || pureBlack,
        )
    }
}

/**
 * iOS 26 style Liquid Glass surface powered by Kyant0/AndroidLiquidGlass (backdrop).
 * Use with a RoundedRectangularShape (kyant Capsule / RoundedRectangle) or a CornerBasedShape.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape,
    colors: GlassColors,
    refraction: Boolean = true,
): Modifier =
    this.drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            blur((if (colors.isDark) 14f else 10f).dp.toPx())
            if (refraction) {
                lens(
                    refractionHeight = 16f.dp.toPx(),
                    refractionAmount = 26f.dp.toPx(),
                    depthEffect = true,
                )
            }
        },
        highlight = { if (colors.isDark) Highlight.Default.copy(alpha = 0.6f) else Highlight.Default },
        shadow = {
            Shadow(
                radius = 22f.dp,
                color = Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.12f),
            )
        },
        onDrawSurface = { drawRect(colors.surface) },
    )

/**
 * iOS-style press feedback: a soft dim overlay instead of the Material ripple.
 * Installed as LocalIndication inside ArchiveTuneTheme, so every Modifier.clickable gets it.
 */
@Immutable
class IosHighlightIndication(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = IosHighlightNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is IosHighlightIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class IosHighlightNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private val highlight = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                val pressed = pressCount > 0
                launch {
                    highlight.animateTo(
                        targetValue = if (pressed) 1f else 0f,
                        animationSpec = tween(durationMillis = if (pressed) 60 else 280),
                    ) { invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = highlight.value
        if (value > 0f) {
            drawRect(color = color, alpha = color.alpha * value)
        }
    }
}
