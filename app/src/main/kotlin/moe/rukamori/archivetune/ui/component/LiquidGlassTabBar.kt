/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * 1:1 port of Kyant0/AndroidLiquidGlass (Apache-2.0) — the "Bottom Tabs" screen of the
 * Backdrop Catalog demo APK:
 *   catalog/components/LiquidBottomTabs.kt
 *   catalog/components/LiquidBottomTab.kt
 *   catalog/destinations/BottomTabsContent.kt
 *
 * Everything is kept as in the original: 64dp capsule / 56dp thumb, 4dp insets,
 * pressedScale 78/56, panel wobble, invisible accent-tinted refraction layer,
 * DampedDragAnimation + InteractiveHighlight physics, iOS tint 0xFF0088FF / 0xFF0091FF.
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
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
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import moe.rukamori.archivetune.ui.theme.rememberGlassColors
import kotlin.math.abs
import kotlin.math.sign

private val TabBarHeight = 64.dp
private val TabBarThumbHeight = 56.dp
private val TabBarInset = 4.dp
private val TabBarIconSize = 28.dp
private val TabBarLabelSize = 12.sp

private val LocalLiquidTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * iOS 26/28 Liquid Glass bottom tab bar — exact behaviour of the Backdrop Catalog demo:
 * one glass capsule, an invisible accent-tinted refraction layer, and a glass thumb on top
 * that magnifies the selected icon, squashes with drag velocity and turns into a
 * chromatic-aberration lens while pressed. Drag the thumb (or anywhere on it) to switch tabs.
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

    val glass = rememberGlassColors(pureBlack)
    val isLightTheme = !glass.isDark
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor =
        when {
            pureBlack -> Color.Black.copy(alpha = 0.4f)
            isLightTheme -> Color(0xFFFAFAFA).copy(alpha = 0.4f)
            else -> Color(0xFF121212).copy(alpha = 0.4f)
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

    var lastClickTime by remember { mutableStateOf(0L) }
    val onTabClick: (Screens) -> Unit = { screen ->
        val now = SystemClock.uptimeMillis()
        val isDoubleTap =
            screen == Screens.Search &&
                onItemDoubleClick != null &&
                now - lastClickTime <= ViewConfiguration.getDoubleTapTimeout()
        lastClickTime = if (isDoubleTap) 0L else now
        if (isDoubleTap) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onItemDoubleClick?.invoke(screen)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnItemClick(screen, currentIsSelected(screen))
        }
    }

    val tabContent: @Composable RowScope.() -> Unit = {
        items.forEach { screen ->
            LiquidGlassTab(
                onClick = { onTabClick(screen) },
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
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val tabWidth =
            with(density) {
                (constraints.maxWidth.toFloat() - TabBarInset.toPx() * 2f) / tabsCount
            }

        val tabsBackdrop = rememberLayerBackdrop()
        val animationScope = rememberCoroutineScope()

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
        // Anything smaller than this is finger jitter on a tap, not a drag.
        val slop = with(density) { 8.dp.toPx() }

        val dampedDragAnimation =
            remember(animationScope, tabsCount, constraints.maxWidth) {
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
                        val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        val wasDrag = dragMoved
                        currentIndex = targetIndex
                        animateToValue(targetIndex.toFloat())
                        animationScope.launch {
                            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                        }
                        if (targetIndex != currentSelectedIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnItemClick(currentItems[targetIndex], false)
                        } else if (!wasDrag) {
                            // tapping the already-selected tab keeps the app's "re-select" behaviour
                            currentOnItemClick(currentItems[targetIndex], true)
                        }
                    },
                    onDrag = { _, dragAmount ->
                        if (abs(dragAmount.x) > slop) {
                            dragMoved = true
                        }
                        updateValue(
                            (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, lastIndex.toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    },
                )
            }

        // External destination changes (deep link, back, tap on a tab) slide the thumb.
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentSelectedIndex }
                .collectLatest { index ->
                    currentIndex = index
                    if (abs(dampedDragAnimation.targetValue - index.toFloat()) > 0.001f) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (abs(dampedDragAnimation.targetValue - index.toFloat()) > 0.001f) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                }
        }

        val interactiveHighlight =
            remember(animationScope) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { _, offset ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            } else {
                                -(dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            },
                            offset.y,
                        )
                    },
                )
            }

        // ---------- 1. glass capsule (track) ----------
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }.drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                ).then(interactiveHighlight.modifier)
                .height(TabBarHeight)
                .fillMaxWidth()
                .padding(TabBarInset),
            verticalAlignment = Alignment.CenterVertically,
            content = tabContent,
        )

        // ---------- 2. invisible accent layer, used as refraction source ----------
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
                    }.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress,
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    ).then(interactiveHighlight.modifier)
                    .height(TabBarThumbHeight)
                    .fillMaxWidth()
                    .padding(horizontal = TabBarInset)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = tabContent,
            )
        }

        // ---------- 3. the glass thumb / lens ----------
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
                }.then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
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
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ).height(TabBarThumbHeight)
                .fillMaxWidth(1f / tabsCount),
        )
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
            .clip(Capsule())
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
