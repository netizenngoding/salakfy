/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * iOS 26/28 Liquid Glass tab bar, shaped after Kyant0/AndroidLiquidGlass (Apache-2.0)
 * "Bottom Tabs" screen — 64dp capsule / 56dp thumb / 4dp insets / lens + chromatic aberration.
 *
 * Differences from the catalog demo are deliberate and only about smoothness:
 *  1. selecting a tab never "presses" the thumb, so the bubble does not balloon and
 *     collapse on every tab change (that was the visible "patah" when switching tabs);
 *  2. no invisible accent refraction layer -> the selected icon can no longer ghost /
 *     double against the monochrome icon behind it; the icon rides inside the bubble
 *     exactly like Apple Music;
 *  3. a single animation source (`position`) + direct 1:1 finger tracking while dragging,
 *     so nothing fights over the same Animatable;
 *  4. two glass layers instead of three (fewer blur/lens passes per frame => no dropped frames).
 */

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.component.liquidglass.LiquidAccentDark
import moe.rukamori.archivetune.ui.component.liquidglass.LiquidAccentLight
import moe.rukamori.archivetune.ui.component.liquidglass.inspectDragGestures
import moe.rukamori.archivetune.ui.component.liquidglass.isLightLiquidTheme
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabCapsuleShape = Capsule()

private val TabBarHeight = 64.dp
private val TabBarThumbHeight = 56.dp
private val TabBarInset = 4.dp
private val TabBarIconSize = 28.dp
private val TabBarLabelSize = 12.sp

/** Critically damped: the bubble glides, it never bounces or overshoots. */
private val TabSlideSpec = spring<Float>(dampingRatio = 0.92f, stiffness = 480f, visibilityThreshold = 0.001f)
private val PressInSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 650f, visibilityThreshold = 0.001f)
private val PressOutSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 500f, visibilityThreshold = 0.001f)

/**
 * iOS 26/28 Liquid Glass bottom tab bar.
 *
 * One glass capsule holds every tab; the selection is a glass lens that slides between them,
 * carries the selected icon/label (accent tinted, like Apple Music) and turns into a refractive
 * lens with chromatic aberration only while a finger is on it.
 */
@Composable
fun LiquidGlassTabBar(
    items: List<Screens>,
    pureBlack: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onItemDoubleClick: ((Screens) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val isLightTheme = isLightLiquidTheme()
    val accentColor = if (isLightTheme) LiquidAccentLight else LiquidAccentDark
    val containerColor =
        when {
            pureBlack -> Color.Black.copy(alpha = 0.56f)
            isLightTheme -> Color(0xFFFAFAFA).copy(alpha = 0.58f)
            else -> Color(0xFF141414).copy(alpha = 0.62f)
        }
    val contentColor = if (isLightTheme) Color.Black else Color.White

    val backdrop = LocalGlassBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val tabsCount = items.size
    val lastIndex = tabsCount - 1
    val selectedIndex = items.indexOfFirst(isSelected).let { if (it < 0) 0 else it }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentItems by rememberUpdatedState(items)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentIsSelected by rememberUpdatedState(isSelected)

    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat(), 0.001f) }
    val pressProgress = remember { Animatable(0f, 0.001f) }
    val velocityStretch = remember { Animatable(0f, 0.001f) }
    val isDragging = remember { mutableStateOf(false) }
    val glowCenter = remember { mutableStateOf(Offset.Unspecified) }

    var lastClickTime by remember { mutableStateOf(0L) }
    val onTabClick: (Screens) -> Unit = { screen ->
        val now = SystemClock.uptimeMillis()
        val isDoubleTap =
            screen == Screens.Search &&
                onItemDoubleClick != null &&
                now - lastClickTime <= ViewConfiguration.getDoubleTapTimeout()
        lastClickTime = if (isDoubleTap) 0L else now
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isDoubleTap) {
            onItemDoubleClick?.invoke(screen)
        } else {
            currentOnItemClick(screen, currentIsSelected(screen))
        }
    }

    // Destination changed outside the bar (tap, deep link, back): glide there. Never press.
    LaunchedEffect(selectedIndex, tabsCount) {
        if (!isDragging.value) {
            val target = selectedIndex.toFloat()
            if (abs(position.value - target) > 0.001f) {
                position.animateTo(target, TabSlideSpec)
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val cellWidth = ((maxWidth - TabBarInset * 2) / tabsCount).coerceAtLeast(1.dp)
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val insetPx = with(density) { TabBarInset.toPx() }
        val slop = with(density) { 8.dp.toPx() }

        val dragModifier =
            Modifier.pointerInput(tabsCount, cellWidthPx, isLtr) {
                val touchSlop = viewConfiguration.touchSlop
                var accumulated = 0f
                var startValue = 0f
                var lastEventTime = 0L

                fun settle() {
                    val dragged = abs(accumulated) > touchSlop
                    isDragging.value = false
                    glowCenter.value = Offset.Unspecified
                    scope.launch { pressProgress.animateTo(0f, PressOutSpec) }
                    scope.launch { velocityStretch.animateTo(0f, PressOutSpec) }

                    val target = position.value.roundToInt().coerceIn(0, lastIndex)
                    scope.launch { position.animateTo(target.toFloat(), TabSlideSpec) }

                    if (target != currentSelectedIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnItemClick(currentItems[target], false)
                    } else if (!dragged) {
                        // tap on the bubble of the current tab -> app's re-select behaviour
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnItemClick(currentItems[target], true)
                    }
                }

                inspectDragGestures(
                    onDragStart = { down ->
                        isDragging.value = true
                        accumulated = 0f
                        startValue = position.value
                        lastEventTime = SystemClock.uptimeMillis()
                        glowCenter.value = down.position
                        scope.launch { pressProgress.animateTo(1f, PressInSpec) }
                    },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                ) { change, dragAmount ->
                    if (dragAmount != Offset.Zero) {
                        accumulated += dragAmount.x
                        if (abs(accumulated) > slop) {
                            change.consume()
                        }
                        glowCenter.value = change.position
                        val delta = accumulated / cellWidthPx * (if (isLtr) 1f else -1f)
                        val target = (startValue + delta).coerceIn(0f, lastIndex.toFloat())
                        scope.launch { position.snapTo(target) }

                        val now = SystemClock.uptimeMillis()
                        val dt = (now - lastEventTime).coerceAtLeast(1L)
                        lastEventTime = now
                        val velocity = dragAmount.x / dt * 1000f // px / s
                        scope.launch {
                            velocityStretch.animateTo(
                                (velocity / 9000f).coerceIn(-0.16f, 0.16f),
                                spring(0.5f, 900f, 0.001f),
                            )
                        }
                    }
                }
            }

        // ---------- glass capsule ----------
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { TabCapsuleShape },
                        effects = {
                            vibrancy()
                            blur(10f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = {
                            if (isLightTheme) Highlight.Default else Highlight.Default.copy(alpha = 0.7f)
                        },
                        shadow = {
                            Shadow(
                                radius = 20f.dp,
                                color = Color.Black.copy(alpha = if (isLightTheme) 0.14f else 0.36f),
                            )
                        },
                        layerBlock = {
                            val progress = pressProgress.value
                            val scale = 1f + (16f.dp.toPx() / size.width.coerceAtLeast(1f)) * progress
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    ).drawWithContent {
                        // soft specular blob that follows the finger
                        val progress = pressProgress.value
                        if (progress > 0f && glowCenter.value.isSpecified) {
                            val center = glowCenter.value
                            clipPath(
                                Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            left = 0f,
                                            top = 0f,
                                            right = size.width,
                                            bottom = size.height,
                                            cornerRadius = CornerRadius(size.height / 2f),
                                        ),
                                    )
                                },
                            ) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color.White.copy(alpha = 0.16f * progress),
                                                    Color.Transparent,
                                                ),
                                            center = center,
                                            radius = size.minDimension * 1.15f,
                                        ),
                                )
                            }
                        }
                        drawContent()
                    },
        ) {
            // ---------- glass thumb (carries the selected icon) ----------
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(cellWidth)
                        .height(TabBarThumbHeight)
                        .graphicsLayer {
                            val value = position.value
                            translationX =
                                if (isLtr) {
                                    insetPx + value * cellWidthPx
                                } else {
                                    -(insetPx + value * cellWidthPx)
                                }
                            val remaining = (position.targetValue - value).fastCoerceIn(-1f, 1f)
                            val velocity = velocityStretch.value
                            // demo-exact balloon: 56dp -> 78dp while a finger is on the bubble
                            val press = 1f + (78f / 56f - 1f) * pressProgress.value
                            scaleX = (1f + abs(remaining) * 0.14f + velocity) * press
                            scaleY =
                                ((1f - abs(remaining) * 0.06f - abs(velocity) * 0.5f) * press)
                                    .coerceIn(0.86f, 1.45f)
                        }
                        .then(dragModifier)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { TabCapsuleShape },
                            effects = {
                                val progress = pressProgress.value
                                vibrancy()
                                blur(8f.dp.toPx() * (1f - progress))
                                lens(
                                    10f.dp.toPx() * progress,
                                    14f.dp.toPx() * progress,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                val progress = pressProgress.value
                                Highlight.Default.copy(alpha = 0.35f + 0.65f * progress)
                            },
                            shadow = {
                                val progress = pressProgress.value
                                Shadow(
                                    radius = 8f.dp + 6f.dp * progress,
                                    color = Color.Black,
                                    alpha = 0.08f + 0.18f * progress,
                                )
                            },
                            innerShadow = {
                                val progress = pressProgress.value
                                InnerShadow(radius = 8f.dp * progress, alpha = 0.9f * progress)
                            },
                            onDrawSurface = {
                                val progress = pressProgress.value
                                // resting pill (iOS selection tint) that dissolves into the lens on press
                                drawRect(
                                    if (isLightTheme) {
                                        Color.Black.copy(alpha = 0.10f)
                                    } else {
                                        Color.White.copy(alpha = 0.18f)
                                    },
                                    alpha = 1f - 0.55f * progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = selectedIndex, animationSpec = tween(150), label = "tabIcon") { index ->
                    val screen = items[index.coerceIn(0, lastIndex)]
                    TabBarContent(
                        iconId = screen.iconIdActive,
                        titleId = screen.titleId,
                        tint = accentColor,
                        labelWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ---------- tab row ----------
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = TabBarInset),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { screen ->
                        val selected = isSelected(screen)
                        val itemAlpha by
                            animateFloatAsState(
                                targetValue = if (selected) 0f else 1f,
                                animationSpec = tween(160),
                                label = "tabItemAlpha",
                            )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = itemAlpha }
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Tab,
                                    ) { onTabClick(screen) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TabBarContent(
                                iconId = screen.iconIdInactive,
                                titleId = screen.titleId,
                                tint = contentColor.copy(alpha = if (isLightTheme) 0.68f else 0.62f),
                                labelWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBarContent(
    iconId: Int,
    titleId: Int,
    tint: Color,
    labelWeight: FontWeight,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(TabBarIconSize),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(titleId),
            color = tint,
            fontSize = TabBarLabelSize,
            fontWeight = labelWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
