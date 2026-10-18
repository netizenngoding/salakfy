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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.GlassColors
import moe.rukamori.archivetune.ui.theme.LocalGlassBackdrop
import moe.rukamori.archivetune.ui.theme.liquidGlass
import moe.rukamori.archivetune.ui.theme.rememberGlassColors

private val TabBarInnerPadding = 4.dp
private val TabBarSearchGap = 10.dp

/**
 * iOS 26 / Apple Music style floating Liquid Glass tab bar.
 * - Main tabs live in one glass capsule with a sliding selection pill.
 * - Search is detached into its own circular glass button (like iOS 26 Apple Music).
 * Signature kept identical to the old Material version so MainActivity barely changes.
 */
@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") miniPlayerProximityProvider: () -> Float = { 0f },
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val backdrop = LocalGlassBackdrop.current
    val glass = rememberGlassColors(pureBlack)
    val tabItems = remember(items) { items.filter { it != Screens.Search } }
    val searchItem = remember(items) { items.firstOrNull { it == Screens.Search } }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = NavigationBarMaxWidth)
                    .fillMaxWidth()
                    .height(NavigationBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TabBarSearchGap),
        ) {
            // ---------- Main tabs capsule ----------
            val selectedIndex = tabItems.indexOfFirst(isSelected)
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .liquidGlass(backdrop = backdrop, shape = { Capsule() }, colors = glass)
                        .padding(TabBarInnerPadding),
            ) {
                val count = tabItems.size.coerceAtLeast(1)
                val tabWidth = maxWidth / count
                val pillPosition by animateFloatAsState(
                    targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                    label = "tabPill",
                )
                val pillAlpha by animateFloatAsState(
                    targetValue = if (selectedIndex >= 0) 1f else 0f,
                    label = "tabPillAlpha",
                )

                // Selection pill (iOS 26 "lifted" tab)
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                translationX = pillPosition * tabWidth.toPx()
                                alpha = pillAlpha
                            }.width(tabWidth)
                            .fillMaxHeight()
                            .clip(Capsule())
                            .background(glass.selection),
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    tabItems.forEach { screen ->
                        val selected = isSelected(screen)
                        GlassTabItem(
                            screen = screen,
                            selected = selected,
                            glass = glass,
                            onClick = { onItemClick(screen, selected) },
                        )
                    }
                }
            }

            // ---------- Detached search button ----------
            if (searchItem != null) {
                val selected = isSelected(searchItem)
                val lastClickTime = remember { mutableLongStateOf(0L) }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.6f, 600f), label = "searchScale")
                val tint by animateColorAsState(if (selected) glass.accent else glass.content, label = "searchTint")
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }.liquidGlass(backdrop = backdrop, shape = { Capsule() }, colors = glass)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                            ) {
                                val now = SystemClock.uptimeMillis()
                                val isDouble =
                                    onSearchItemDoubleClick != null &&
                                            now - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                                lastClickTime.longValue = if (isDouble) 0L else now
                                if (isDouble) onSearchItemDoubleClick?.invoke() else onItemClick(searchItem, selected)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (selected) searchItem.iconIdActive else searchItem.iconIdInactive),
                        contentDescription = stringResource(searchItem.titleId),
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GlassTabItem(
    screen: Screens,
    selected: Boolean,
    glass: GlassColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.6f, 600f), label = "tabScale")
    val tint by animateColorAsState(if (selected) glass.accent else glass.content, label = "tabTint")

    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(Capsule())
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(screen.titleId),
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
