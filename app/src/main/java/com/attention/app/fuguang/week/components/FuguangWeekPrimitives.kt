package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun fuguangAccentColor(accent: FuguangWeekAccent): Color {
    val colors = fuguangWeekColors()
    return when (accent) {
        FuguangWeekAccent.GREEN -> colors.primary
        FuguangWeekAccent.GOLD -> colors.gold
        FuguangWeekAccent.BLUE -> colors.blueDeep
        FuguangWeekAccent.ROSE -> colors.rose
        FuguangWeekAccent.NEUTRAL -> colors.block
    }
}

@Composable
fun fuguangAccentSoftColor(accent: FuguangWeekAccent): Color {
    val colors = fuguangWeekColors()
    return when (accent) {
        FuguangWeekAccent.GREEN -> colors.primarySoft
        FuguangWeekAccent.GOLD -> colors.goldSoft
        FuguangWeekAccent.BLUE -> colors.blueSoft
        FuguangWeekAccent.ROSE -> colors.roseSoft
        FuguangWeekAccent.NEUTRAL -> colors.surfaceMuted
    }
}

@Composable
fun FuguangWeekCard(
    modifier: Modifier = Modifier,
    color: Color = fuguangWeekColors().elevated,
    borderColor: Color = fuguangWeekColors().border,
    content: @Composable () -> Unit,
) {
    val dims = fuguangWeekDims()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardRadius),
        color = color,
        border = BorderStroke(1.dp, borderColor),
        content = content,
    )
}

@Composable
fun FuguangWeekTag(
    text: String,
    accent: FuguangWeekAccent = FuguangWeekAccent.GREEN,
    modifier: Modifier = Modifier,
) {
    val type = fuguangWeekType()
    val color = fuguangAccentColor(accent)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = fuguangAccentSoftColor(accent),
    ) {
        Text(
            text = text,
            style = type.micro,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun FuguangWeekPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val background = when {
        !enabled -> colors.surfaceMuted
        primary -> colors.primary
        else -> colors.elevated
    }
    val foreground = when {
        !enabled -> colors.textMuted
        primary -> colors.white
        else -> colors.text
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = CircleShape,
        color = background,
        border = if (primary) null else BorderStroke(1.dp, colors.borderStrong),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = type.label,
                color = foreground,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
fun FuguangWeekIconBubble(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    background: Color = fuguangWeekColors().goldSoft,
    tint: Color = fuguangWeekColors().goldDeep,
    onClick: (() -> Unit)? = null,
) {
    val dims = fuguangWeekDims()
    val shape = CircleShape
    if (onClick == null) {
        Box(
            modifier = modifier.size(dims.touchTarget).background(background, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(19.dp))
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.size(dims.touchTarget),
            shape = shape,
            color = background,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
fun FuguangWeekDot(
    accent: FuguangWeekAccent,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(6.dp)
            .background(fuguangAccentColor(accent), CircleShape),
    )
}

@Composable
fun FuguangWeekIconLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = fuguangWeekColors().textSecondary,
) {
    val type = fuguangWeekType()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = text, style = type.micro, color = tint)
    }
}

@Composable
fun FuguangWeekInsetDivider(
    modifier: Modifier = Modifier,
    color: Color = fuguangWeekColors().border,
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(color))
    }
}

@Composable
fun FuguangWeekInlineRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun FuguangWeekLabelValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val type = fuguangWeekType()
    val colors = fuguangWeekColors()
    Column(modifier = modifier) {
        Text(label, style = type.micro, color = colors.textMuted)
        Text(value, style = type.label, color = colors.text)
    }
}