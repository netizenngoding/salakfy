/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * iOS-styled checkbox / radio button used in place of the Material ones, so list rows and
 * dialogs stop looking like Google Material. Material-compatible signatures for aliasing.
 */

package moe.rukamori.archivetune.ui.component.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val ControlSize = 22.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val isLightTheme = isLightLiquidTheme()
    val accent = if (isLightTheme) LiquidAccentLight else LiquidAccentDark
    val ringColor = if (isLightTheme) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.28f)
    val fill by
        animateColorAsState(
            targetValue = if (checked) accent else Color.Transparent,
            animationSpec = spring(stiffness = 900f),
            label = "checkboxFill",
        )

    Box(
        modifier =
            Modifier
                .size(ControlSize)
                .then(if (enabled) Modifier else Modifier.alpha(0.4f))
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled && onCheckedChange != null,
                    role = Role.Checkbox,
                ) { onCheckedChange?.invoke(!checked) }
                .drawBehind {
                    drawCircle(color = fill)
                    if (!checked) {
                        val stroke = 1.5.dp.toPx()
                        drawCircle(
                            color = ringColor,
                            radius = size.minDimension / 2f - stroke / 2f,
                            style = Stroke(width = stroke),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val isLightTheme = isLightLiquidTheme()
    val accent = if (isLightTheme) LiquidAccentLight else LiquidAccentDark
    val ringColor = if (isLightTheme) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.28f)
    val background by
        animateColorAsState(
            targetValue = if (selected) accent else Color.Transparent,
            animationSpec = spring(stiffness = 900f),
            label = "radioFill",
        )
    val interaction = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .size(ControlSize)
                .then(if (enabled) Modifier else Modifier.alpha(0.4f))
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && onClick != null,
                    role = Role.RadioButton,
                ) { onClick?.invoke() }
                .drawBehind {
                    drawCircle(color = background)
                    if (selected) {
                        drawCircle(color = Color.White, radius = size.minDimension * 0.17f)
                    } else {
                        val stroke = 1.5.dp.toPx()
                        drawCircle(
                            color = ringColor,
                            radius = size.minDimension / 2f - stroke / 2f,
                            style = Stroke(width = stroke),
                        )
                    }
                },
    )
}
