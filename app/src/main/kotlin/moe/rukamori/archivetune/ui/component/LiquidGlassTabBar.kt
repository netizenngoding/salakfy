/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.ui.component.liquidglass.inspectDragGestures
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.GlassColors
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import moe.rukamori.archivetune.ui.theme.rememberGlassColors
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabBarThumbInset: Dp = 4.dp
private val TabBarThumbHeight: Dp = NavigationBarHeight - TabBarThumbInset * 2
private const val TabBarIconSizeDp = 22
private val TabBarPressedScale = 1.1f

/**
 * iOS 26/28 style Liquid Glass bottom navigation bar, built on Kyant0/AndroidLiquidGlass.
 *
 * Replica of the behaviour of Kyant's `LiquidBottomTabs`:
 *  - one floating glass capsule holding every tab,
 *  - a glass "lens" thumb that sits on the selected tab and slides/drags between them,
 *  - the thumb squashes & stretches with the drag velocity (liquid feel),
 *  - pressing the thumb turns it into a refractive, chromatic-aberration lens,
 *  - the whole capsule can be dragged left/right to change tabs, with haptics per tab.
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

    val backdrop = LocalGlassBackdrop.current
    val glass = rememberGlassColors(pureBlack)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val itemCount = items.size
    val lastIndex = (itemCount - 1).toFloat()
    val selectedIndex = items.indexOfFirst(isSelected).let { if (it < 0) 0 else it }

    val currentItems by rememberUpdatedState(items)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    val thumbValue = remember { Animatable(selectedIndex.toFloat(), 0.001f) }
    val pressProgress = remember { Animatable(0f, 0.001f) }
    val stretch = remember { Animatable(0f, 0.001f) }
    val glowCenter = remember { mutableStateOf(Offset.Unspecified) }
    val dragMoved = remember { mutableStateOf(false) }

    // Keep the thumb in sync when the destination changes outside of the bar (deep links, back, etc.)
    LaunchedEffect(selectedIndex, itemCount) {
        val target = selectedIndex.toFloat()
        if (thumbValue.targetValue != target) {
            val direction = if (target > thumbValue.value) 1f else -1f
            launch { stretch.animateTo(0.32f * direction, spring(0.7f, 900f, 0.001f)) }
            thumbValue.animateTo(target, spring(0.72f, 340f, 0.001f))
            launch { stretch.animateTo(0f, spring(0.6f, 420f, 0.001f)) }
        }
    }

    val dragGestureModifier =
        Modifier.pointerInput(itemCount, isLtr) {
            val insetPx = TabBarThumbInset.toPx()
            val cellWidthPx = ((size.width - insetPx * 2f) / itemCount).coerceAtLeast(1f)
            val touchSlop = viewConfiguration.touchSlop
            var accumulated = 0f

            fun release() {
                glowCenter.value = Offset.Unspecified
                dragMoved.value = false
                scope.launch { pressProgress.animateTo(0f, spring(0.6f, 420f, 0.001f)) }
                scope.launch { stretch.animateTo(0f, spring(0.6f, 320f, 0.001f)) }

                val target = thumbValue.value.roundToInt().coerceIn(0, itemCount - 1)
                scope.launch {
                    thumbValue.animateTo(target.toFloat(), spring(0.78f, 380f, 0.001f))
                }
                if (target != currentSelectedIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnItemClick(currentItems[target], false)
                }
            }

            inspectDragGestures(
                onDragStart = { down ->
                    accumulated = 0f
                    dragMoved.value = false
                    glowCenter.value = down.position
                    scope.launch { pressProgress.animateTo(1f, spring(1f, 1000f, 0.001f)) }
                },
                onDragEnd = { release() },
                onDragCancel = { release() },
            ) { change, dragAmount ->
                if (dragAmount != Offset.Zero) {
                    accumulated += dragAmount.x
                    if (abs(accumulated) > touchSlop) {
                        dragMoved.value = true
                        glowCenter.value = change.position
                        change.consume()
                        val delta = dragAmount.x / cellWidthPx * (if (isLtr) 1f else -1f)
                        scope.launch {
                            thumbValue.animateTo(
                                (thumbValue.targetValue + delta).coerceIn(0f, lastIndex),
                                spring(1f, 1000f, 0.001f),
                            )
                        }
                        scope.launch {
                            stretch.animateTo(
                                (delta * 2.6f).coerceIn(-0.4f, 0.4f),
                                spring(0.6f, 700f, 0.001f),
                            )
                        }
                    }
                }
            }
        }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val cellWidth = ((maxWidth - TabBarThumbInset * 2) / itemCount).coerceAtLeast(1.dp)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur((if (glass.isDark) 14f else 10f).dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = {
                            if (glass.isDark) {
                                Highlight.Default.copy(alpha = 0.7f)
                            } else {
                                Highlight.Default
                            }
                        },
                        shadow = {
                            Shadow(
                                radius = 24f.dp,
                                color = Color.Black.copy(alpha = if (glass.isDark) 0.38f else 0.14f),
                            )
                        },
                        onDrawSurface = { drawRect(glass.surface) },
                    )
                    .drawWithContent {
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
                    }
                    .then(dragGestureModifier),
        ) {
            // ---------- Sliding glass thumb ----------
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(cellWidth)
                        .height(TabBarThumbHeight)
                        .graphicsLayer {
                            val value = thumbValue.value
                            val insetPx = TabBarThumbInset.toPx()
                            val cellPx = cellWidth.toPx()
                            translationX =
                                if (isLtr) {
                                    insetPx + value * cellPx
                                } else {
                                    -(insetPx + value * cellPx)
                                }
                            val stretchValue = stretch.value
                            val pressed = pressProgress.value
                            val baseScale = 1f + (TabBarPressedScale - 1f) * pressed
                            scaleX = baseScale * (1f + stretchValue * 0.55f)
                            scaleY = baseScale * (1f - abs(stretchValue) * 0.35f)
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = pressProgress.value
                                vibrancy()
                                blur((if (glass.isDark) 10f else 8f).dp.toPx())
                                lens(
                                    refractionHeight = 20f.dp.toPx() * progress,
                                    refractionAmount = 26f.dp.toPx() * progress,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = 0.25f + 0.75f * pressProgress.value)
                            },
                            shadow = {
                                Shadow(
                                    radius = 14f.dp,
                                    color = Color.Black,
                                    alpha = 0.12f + 0.2f * pressProgress.value,
                                )
                            },
                            innerShadow = {
                                InnerShadow(radius = 8f.dp * pressProgress.value, alpha = 0.9f * pressProgress.value)
                            },
                            onDrawSurface = {
                                val progress = pressProgress.value
                                drawRect(glass.selection, alpha = 1f - progress)
                                drawRect(Color.Black.copy(alpha = 0.04f * progress))
                            },
                        ),
            )

            // ---------- Tabs ----------
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = TabBarThumbInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { screen ->
                    TabBarItem(
                        screen = screen,
                        selected = isSelected(screen),
                        glass = glass,
                        onClick = {
                            if (!dragMoved.value) onItemClick(screen, isSelected(screen))
                        },
                        onDoubleClick = onItemDoubleClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabBarItem(
    screen: Screens,
    selected: Boolean,
    glass: GlassColors,
    onClick: () -> Unit,
    onDoubleClick: ((Screens) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val iconScale by
        animateFloatAsState(
            targetValue =
                when {
                    pressed -> 0.86f
                    selected -> 1.06f
                    else -> 1f
                },
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
            label = "tabIconScale",
        )
    val tint by
        animateColorAsState(
            targetValue =
                if (selected) {
                    glass.accent
                } else {
                    glass.content.copy(alpha = if (glass.isDark) 0.72f else 0.62f)
                },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tabTint",
        )

    var lastClickTime by remember { mutableStateOf(0L) }

    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                ) {
                    val now = SystemClock.uptimeMillis()
                    val doubleTap =
                        onDoubleClick != null &&
                            now - lastClickTime <= ViewConfiguration.getDoubleTapTimeout()
                    lastClickTime = if (doubleTap) 0L else now
                    if (doubleTap) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDoubleClick?.invoke(screen)
                    } else {
                        onClick()
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier
                    .size(TabBarIconSizeDp.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = stringResource(screen.titleId),
            color = tint,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
