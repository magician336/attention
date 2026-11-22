package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FuguangB1Button(
    label: String,
    kind: FuguangButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val isIcon = kind == FuguangButtonKind.ICON
    val height = when (kind) {
        FuguangButtonKind.TEXT -> 27.dp
        FuguangButtonKind.ICON -> 34.dp
        else -> dims.buttonHeight
    }
    val background = when {
        !enabled -> roles.n3
        kind == FuguangButtonKind.PRIMARY -> roles.mainDeep
        kind == FuguangButtonKind.ICON -> roles.surface
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> roles.ink4
        kind == FuguangButtonKind.PRIMARY -> roles.surface
        else -> roles.mainDeep
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height).testTag("button-b1-$label"),
        shape = if (isIcon) CircleShape else RoundedCornerShape(percent = 50),
        color = background,
        border = if (kind == FuguangButtonKind.SECONDARY || kind == FuguangButtonKind.ICON) {
            BorderStroke(1.dp, roles.line)
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isIcon) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isIcon) {
                Icon(icon ?: Icons.Filled.MoreHoriz, label, tint = foreground, modifier = Modifier.size(16.dp))
            } else {
                Text(label, style = if (kind == FuguangButtonKind.TEXT) type.t95 else type.t125, color = foreground)
            }
        }
    }
}

@Composable
fun FuguangB2Badge(
    label: String,
    kind: FuguangBadgeKind,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    val background = when (kind) {
        FuguangBadgeKind.TARGET -> roles.accentTint
        FuguangBadgeKind.COUNT -> roles.mainTint
        FuguangBadgeKind.NEUTRAL -> roles.n2
    }
    val foreground = when (kind) {
        FuguangBadgeKind.TARGET -> roles.accentDeep
        FuguangBadgeKind.COUNT -> roles.mainInk
        FuguangBadgeKind.NEUTRAL -> roles.ink3
    }
    Surface(modifier = modifier.height(dims.badgeHeight), shape = CircleShape, color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(foreground, CircleShape))
            Text(label, style = type.t95.copy(fontWeight = FontWeight.Medium), color = foreground, maxLines = 1)
        }
    }
}
