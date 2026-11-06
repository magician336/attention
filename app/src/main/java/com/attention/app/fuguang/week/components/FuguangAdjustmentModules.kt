package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FuguangAdjustmentProposalCard(
    proposal: FuguangAdjustmentProposalUi,
    onToggleItem: (String) -> Unit,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    FuguangWeekCard(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = proposal.title,
                    style = type.title,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                FuguangWeekTag(proposal.impactLabel, FuguangWeekAccent.GOLD)
            }
            proposal.items.forEachIndexed { index, item ->
                if (index > 0) {
                    FuguangWeekInsetDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                FuguangAdjustmentItemRow(
                    item = item,
                    onToggle = { onToggleItem(item.id) },
                )
            }
            FuguangWeekInsetDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FuguangWeekPillButton(
                    text = proposal.acceptLabel,
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).testTag("adjustment-accept-all"),
                )
                FuguangWeekPillButton(
                    text = proposal.adjustLabel,
                    onClick = onAdjust,
                    modifier = Modifier.weight(1f).testTag("adjustment-adjust"),
                    primary = false,
                )
            }
            Text(
                text = "已采纳 ${proposal.items.count { it.accepted }} / ${proposal.items.size} 处",
                style = type.micro,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth().padding(end = 18.dp, bottom = 13.dp),
            )
        }
    }
}

@Composable
fun FuguangAdjustmentItemRow(
    item: FuguangAdjustmentItemUi,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 38.dp)
                .background(colors.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = type.bodyStrong,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.detail,
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        FuguangAcceptChip(
            accepted = item.accepted,
            onClick = onToggle,
            modifier = Modifier.testTag("adjustment-accept-${item.id}"),
        )
    }
}

@Composable
fun FuguangAcceptChip(
    accepted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (accepted) colors.primary else colors.elevated,
        border = BorderStroke(1.dp, if (accepted) colors.primary else colors.borderStrong),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (accepted) colors.white else colors.textMuted,
                modifier = Modifier.width(13.dp).height(13.dp),
            )
            Text(
                text = if (accepted) "采纳" else "恢复",
                style = type.micro,
                color = if (accepted) colors.white else colors.textMuted,
            )
        }
    }
}

@Composable
fun FuguangAdjustmentHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.width(13.dp).height(13.dp),
        )
        Text(text, style = type.micro, color = colors.textMuted)
    }
}