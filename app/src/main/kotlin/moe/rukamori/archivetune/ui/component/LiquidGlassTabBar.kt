/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * 1:1 port of Kyant0/AndroidLiquidGlass (Apache-2.0) — the "Bottom Tabs" screen of the
 * Backdrop Catalog demo APK:
 *
 *   catalog/components/LiquidBottomTabs.kt   (structure + physics, copied verbatim)
 *   catalog/components/LiquidBottomTab.kt    (tab item, copied verbatim)
 *   catalog/destinations/BottomTabsContent.kt(64dp bar, 28dp icon, 12sp label)
 *
 * Everything that makes the demo what it is, is kept:
 *  - 64dp glass capsule, 56dp thumb, 4dp insets, pressedScale 78/56
 *  - the invisible accent-tinted refraction layer, so the selected icon lives *inside*
 *    the glass lens and gets magnified / chromatic-aberrated when the bubble is pressed
 *  - DampedDragAnimation physics (velocity squash/stretch), panel wobble,
 *    InteractiveHighlight specular blob that follows the finger
 *  - the bubble is draggable between tabs and pressable
 *
 * Only two things are added on top, both invisible: a re-select callback when the bubble of
 * the current tab is tapped, and a guard so navigation does not re-trigger itself.
 */

package moe.rukamori.archivetune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.component.liquidglass.DampedDragAnimation
import moe.rukamori.archivetune.ui.component.liquidglass.InteractiveHighlight
import moe.rukamori.archivetune.ui.component.liquidglass.isLightLiquidTheme
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import kotlin.math.abs
import kotlin.math.sign

private val TabBarTrackHeight = 64.dp
private val TabBarThumbHeight = 56.dp
private val TabBarInset = 4.dp
private val TabBarIconSize = 28.dp
private val TabBarLabelSize = 12.sp
private val TabCapsuleShape = Capsule()

private val LocalLiquidTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

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
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor =
        when {
            pureBlack -> Color(0xFF0A0A0A).copy(alpha = 0.82f)
            isLightTheme -> Color(0xFFF7F7F8).copy(alpha = 0.74f)
            else -> Color(0xFF161618).copy(alpha = 0.78f)
        }
    val contentColor = if (isLightTheme) Color.Black else Color.White

    val backdrop = LocalGlassBackdrop.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val tabsCount = items.size
    val selectedIndex = items.indexOfFirst(isSelected).let { if (it < 0) 0 else it }
    val currentItems by rememberUpdatedState(items)
    val currentIsSelected by rememberUpdatedState(isSelected)
    val currentOnItemClick by rememberUpdatedState(onItemClick)

    var pendingUserSelection by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }

    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val tabWidth =
            with(density) {
                (constraints.maxWidth.toFloat() - TabBarInset.toPx() * 2f) / tabsCount
            }

        val tabsBackdrop = rememberLayerBackdrop()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    TabBarInset.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        var dragMoved by remember { mutableStateOf(false) }

        val dampedDragAnimation =
            remember(animationScope, tabsCount) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = selectedIndex.toFloat(),
                    valueRange = 0f..(tabsCount - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 78f / 56f,
                    onDragStarted = {
                        dragMoved = false
                    },
                    onDragStopped = {
                        val dragged = dragMoved
                        val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        animateToValue(targetIndex.toFloat())
                        animationScope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                        if (targetIndex != currentIndex) {
                            currentIndex = targetIndex
                            pendingUserSelection = true
                        } else if (!dragged) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnItemClick(currentItems[targetIndex], true)
                        }
                    },
                    onDrag = { _, dragAmount ->
                        if (abs(dragAmount.x) > 0.5f) {
                            dragMoved = true
                        }
                        updateValue(
                            (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    },
                )
            }

        // destination changed from outside (tap, deep link, back) -> move the bubble
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentItems.indexOfFirst(currentIsSelected).let { if (it < 0) 0 else it } }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        // thumb moves + notify navigation when the user dragged it there
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    if (pendingUserSelection) {
                        pendingUserSelection = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnItemClick(currentItems[index.coerceIn(0, tabsCount - 1)], false)
                    }
                }
        }

        val interactiveHighlight =
            remember(animationScope) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            } else {
                                size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            },
                            size.height / 2f,
                        )
                    },
                )
            }

        val tabContent: @Composable RowScope.() -> Unit = {
            items.forEach { screen ->
                LiquidGlassTab(
                    onClick = {
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
                    },
                ) {
                    Icon(
                        painter = painterResource(if (isSelected(screen)) screen.iconIdActive else screen.iconIdInactive),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(TabBarIconSize),
                    )
                    Text(
                        text = stringResource(screen.titleId),
                        color = contentColor,
                        fontSize = TabBarLabelSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ---------- 1. glass capsule ----------
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }.drawBackdrop(
                    backdrop = backdrop,
                    shape = { TabCapsuleShape },
                    effects = {
                        vibrancy()
                        blur(14f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width.coerceAtLeast(1f), progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(containerColor)
                        // glassy sheen: light catching the top of the capsule
                        drawRect(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = if (isLightTheme) 0.34f else 0.12f),
                                        Color.Transparent,
                                    ),
                                startY = 0f,
                                endY = size.height * 0.55f,
                            ),
                        )
                    },
                ).then(interactiveHighlight.modifier)
                .height(TabBarTrackHeight)
                .fillMaxWidth()
                .padding(TabBarInset),
            verticalAlignment = Alignment.CenterVertically,
            content = tabContent,
        )

        // ---------- 2. invisible accent layer (refraction source for the lens) ----------
        CompositionLocalProvider(
            LocalLiquidTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }.height(TabBarThumbHeight)
                    .fillMaxWidth()
                    .padding(horizontal = TabBarInset)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = tabContent,
            )
        }

        // ---------- 3. the glass thumb ----------
        // Two stacked layers, same visual language as the capsule:
        //  a) frosted pill        b) the tinted icon layer + the press lens
        Box(
            Modifier
                .padding(horizontal = TabBarInset)
                .graphicsLayer {
                    translationX =
                        if (isLtr) {
                            dampedDragAnimation.value * tabWidth + panelOffset
                        } else {
                            -(dampedDragAnimation.value * tabWidth) + panelOffset
                        }
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                }.then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .height(TabBarThumbHeight)
                .fillMaxWidth(1f / tabsCount),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { TabCapsuleShape },
                        effects = {
                            vibrancy()
                            blur(14f.dp.toPx())
                        },
                        highlight = { Highlight.Default },
                        onDrawSurface = {
                            drawRect(
                                if (isLightTheme) {
                                    Color.White.copy(alpha = 0.46f)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                            )
                        },
                    ),
            )

            Box(
                Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = tabsBackdrop,
                        shape = { TabCapsuleShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8f.dp * progress,
                                alpha = progress,
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
private fun RowScope.LiquidGlassTab(
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        Modifier
            .clip(TabCapsuleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ).fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
