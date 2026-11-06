package com.attention.app.fuguang.week.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FuguangChangeSummaryBar(
    affectedDays: Int,
    changes: Int,
    timeLabel: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp).background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.white,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = "已调整 · ${affectedDays} 天 · ${changes} 处",
                style = type.label,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (timeLabel != null) {
                Text(timeLabel, style = type.micro, color = colors.textMuted)
            }
        }
    }
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.primarySoft.copy(alpha = 0.72f),
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.primarySoft.copy(alpha = 0.72f),
            content = content,
        )
    }
}

@Composable
fun FuguangDayPagerIndicator(
    pageCount: Int,
    selectedIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuguangWeekIconBubble(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = "上一天",
            background = colors.elevated,
            tint = colors.textMuted,
            onClick = onPrevious,
        )
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == selectedIndex) 14.dp else 5.dp, 5.dp)
                        .background(
                            color = if (index == selectedIndex) colors.primary else colors.borderStrong,
                            shape = CircleShape,
                        ),
                )
            }
        }
        FuguangWeekIconBubble(
            icon = Icons.Filled.ChevronRight,
            contentDescription = "下一天",
            background = colors.elevated,
            tint = colors.textMuted,
            onClick = onNext,
        )
    }
}

@Composable
fun FuguangCommitFooter(
    summary: String,
    onUndo: () -> Unit,
    onBackToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.elevated,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                style = type.micro,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            FuguangWeekPillButton(
                text = "撤回",
                onClick = onUndo,
                modifier = Modifier.testTag("week-change-undo"),
                primary = false,
            )
            Spacer(Modifier.size(8.dp))
            FuguangWeekPillButton(
                text = "回到对话",
                onClick = onBackToChat,
                modifier = Modifier.testTag("week-back-to-chat"),
            )
        }
    }
}
