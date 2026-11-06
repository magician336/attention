package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FuguangRelocationOptionCard(
    option: FuguangRelocationOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("relocation-option-${option.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryPale else colors.elevated,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.primary else colors.border,
        ),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = if (selected) colors.primary else colors.surfaceMuted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option.letter,
                            style = type.label,
                            color = if (selected) colors.white else colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.time, style = type.bodyStrong, color = colors.text)
                    Text(option.place, style = type.micro, color = colors.textMuted)
                }
                if (option.recommended) {
                    FuguangWeekTag("推荐", FuguangWeekAccent.GOLD)
                }
            }
            Spacer(Modifier.height(9.dp))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = colors.goldSoft.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "代价：${option.cost}",
                    style = type.micro,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
fun FuguangRelocationPicker(
    title: String,
    subtitle: String,
    options: List<FuguangRelocationOptionUi>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onKeepOriginal: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    FuguangWeekCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = type.section, color = colors.text)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = type.caption, color = colors.textMuted)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { option ->
                    FuguangRelocationOptionCard(
                        option = option,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FuguangWeekPillButton(
                    text = "先不改",
                    onClick = onKeepOriginal,
                    modifier = Modifier.weight(0.42f),
                    primary = false,
                )
                FuguangWeekPillButton(
                    text = "就放${options.firstOrNull { it.id == selectedId }?.time?.substringBefore("-") ?: "这里"}",
                    onClick = onApply,
                    modifier = Modifier.weight(0.58f).testTag("relocation-apply"),
                )
            }
        }
    }
}
