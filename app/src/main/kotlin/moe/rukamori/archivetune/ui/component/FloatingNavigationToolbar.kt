/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.ui.screens.Screens

/**
 * Bottom navigation bar of the app.
 *
 * It is the iOS 26/28 style floating Liquid Glass tab bar (Kyant0/AndroidLiquidGlass "LiquidBottomTabs"):
 * every tab lives inside one glass capsule and the selection is a sliding, squashing glass lens.
 * The public signature is kept identical to the previous Material implementation so `MainActivity`
 * keeps working unchanged.
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
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassTabBar(
            items = items,
            pureBlack = pureBlack,
            isSelected = isSelected,
            onItemClick = onItemClick,
            onItemDoubleClick = { screen ->
                if (screen == Screens.Search) onSearchItemDoubleClick?.invoke()
            },
            modifier =
                Modifier
                    .widthIn(max = NavigationBarMaxWidth)
                    .fillMaxWidth()
                    .height(NavigationBarHeight),
        )
    }
}
