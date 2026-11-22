package com.attention.app.fuguang.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangT4DailySummary(
    summary: FuguangSummaryUi,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(roles.line2))
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今天 ${summary.totalLabel} · 已排 ${summary.itemCount} 项",
                style = type.t95.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = roles.mainDeep,
                modifier = Modifier.weight(1f),
            )
            FuguangB1Button(summary.actionLabel, FuguangButtonKind.TEXT, onOpenWeek)
        }
    }
}
