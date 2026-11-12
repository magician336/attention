package com.attention.app.fuguang.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangA1State(
    mode: FuguangStateMode,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val accent = if (mode == FuguangStateMode.ERROR) roles.danger else roles.mainDeep
    val iconBackground = if (mode == FuguangStateMode.ERROR) roles.danger.copy(alpha = 0.12f) else roles.mainTint
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = roles.line,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(34.dp).background(iconBackground, CircleShape), contentAlignment = Alignment.Center) {
            when (mode) {
                FuguangStateMode.LOADING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = accent)
                FuguangStateMode.ERROR -> Icon(Icons.Filled.ErrorOutline, null, tint = accent, modifier = Modifier.size(18.dp))
                FuguangStateMode.EMPTY -> Icon(Icons.Filled.Inbox, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = type.t125.copy(fontWeight = FontWeight.Medium), color = roles.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(description, style = type.t95.copy(fontSize = 10.5.sp), color = roles.ink3, textAlign = TextAlign.Center)
        if (mode == FuguangStateMode.LOADING) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.58f).height(7.dp).background(roles.n3, CircleShape))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.42f).height(7.dp).background(roles.n3, CircleShape))
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            FuguangB1Button(actionLabel, FuguangButtonKind.TEXT, onAction)
        }
    }
}