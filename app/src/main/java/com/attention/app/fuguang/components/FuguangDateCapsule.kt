package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangT1DateCapsuleBar(
    days: List<FuguangDateCellUi>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = modifier.fillMaxWidth().height(dims.dateBarHeight),
        shape = RoundedCornerShape(9.dp),
        color = roles.surface,
        border = BorderStroke(1.dp, roles.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            days.take(7).forEach { day ->
                Surface(
                    onClick = { onSelect(day.id) },
                    modifier = Modifier.weight(1f).testTag("t1-day-${day.id}"),
                    shape = RoundedCornerShape(9.dp),
                    color = if (day.selected) roles.mainDeep else Color.Transparent,
                ) {
                    Box {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(day.weekday, style = type.t95.copy(fontSize = 9.sp), color = if (day.selected) roles.surface else roles.ink3)
                            Text(
                                day.date,
                                style = type.t125.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                                color = if (day.selected) roles.surface else roles.ink,
                            )
                        }
                        when {
                            day.changed -> Box(
                                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp)
                                    .size(4.dp).clip(CircleShape).background(roles.mainDeep),
                            )
                            day.conflicted -> Box(
                                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp)
                                    .size(4.dp).clip(CircleShape).background(roles.accentDeep),
                            )
                        }
                    }
                }
            }
        }
    }
}
