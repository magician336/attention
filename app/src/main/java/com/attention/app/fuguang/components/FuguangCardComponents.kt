package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangK1AdjustmentCard(
    card: FuguangAdjustmentCardUi,
    onToggleItem: (String) -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = modifier.widthIn(max = dims.cardWidth),
        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 15.dp, bottomEnd = 15.dp, bottomStart = 15.dp),
        color = roles.surface,
        border = BorderStroke(1.dp, roles.line),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(card.title, style = type.t125.copy(fontWeight = FontWeight.Medium), color = roles.ink, modifier = Modifier.weight(1f))
                FuguangB2Badge(card.status, FuguangBadgeKind.NEUTRAL)
            }
            card.items.take(4).forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(roles.line2))
                FuguangK1AdjustmentRow(item, onToggle = { onToggleItem(item.id) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FuguangB1Button(card.primaryLabel, FuguangButtonKind.PRIMARY, onPrimary, Modifier.weight(1f))
                FuguangB1Button(card.secondaryLabel, FuguangButtonKind.SECONDARY, onSecondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FuguangK1AdjustmentRow(
    item: FuguangAdjustmentItemUi,
    onToggle: () -> Unit,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).height(34.dp).background(
                if (item.accepted) roles.mainDeep else roles.line,
                RoundedCornerShape(2.dp),
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = type.t125.copy(fontWeight = FontWeight.Medium),
                color = roles.ink,
                textDecoration = if (item.accepted) TextDecoration.None else TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.detail, style = type.t11, color = roles.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onToggle,
            modifier = Modifier.height(28.dp).testTag("k1-toggle-${item.id}"),
            shape = CircleShape,
            color = if (item.accepted) roles.mainDeep else roles.surface,
            border = if (item.accepted) null else BorderStroke(1.dp, roles.line),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (item.accepted) "采纳" else "原样",
                    style = type.t95,
                    color = if (item.accepted) roles.surface else roles.ink3,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}

@Composable
fun FuguangK2FeishuCard(
    card: FuguangFeishuCardUi,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = modifier.widthIn(max = dims.feishuWidth),
        shape = RoundedCornerShape(10.dp),
        color = roles.surface,
        border = BorderStroke(1.dp, roles.line),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(roles.accentDeep))
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).background(roles.accentTint, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Text("飞", style = type.t95, color = roles.accentDeep)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(card.source, style = type.t11.copy(fontWeight = FontWeight.Medium), color = roles.ink, modifier = Modifier.weight(1f))
                    Text(card.time, style = type.t95, color = roles.ink3)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = feishuBody(card, roles.accentDeep),
                    style = type.t125.copy(fontSize = 12.sp),
                    color = roles.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.tags.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        card.tags.take(3).forEach { tag -> FuguangB2Badge(tag, FuguangBadgeKind.NEUTRAL) }
                    }
                }
            }
        }
    }
}

@Composable
fun FuguangK3ComparisonCard(
    title: String,
    rows: List<FuguangComparisonRowUi>,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = modifier.widthIn(max = dims.cardWidth),
        shape = RoundedCornerShape(10.dp),
        color = roles.surface,
        border = BorderStroke(1.dp, roles.line),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = type.t125.copy(fontWeight = FontWeight.Medium), color = roles.ink)
            rows.takeLast(2).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = type.t95, color = roles.ink3, modifier = Modifier.width(20.dp))
                    Surface(
                        modifier = Modifier.weight(1f).height(17.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = if (row.isOriginal) roles.line2 else roles.accentTint,
                        border = if (row.isOriginal) null else BorderStroke(1.dp, roles.accent),
                    ) {
                        Box(contentAlignment = Alignment.CenterStart) {
                            Text(
                                row.text,
                                style = type.t8.copy(fontSize = 7.5.sp),
                                color = if (row.isOriginal) roles.ink4 else roles.accentDeep2,
                                modifier = Modifier.padding(horizontal = 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun feishuBody(card: FuguangFeishuCardUi, emphasisColor: Color) = buildAnnotatedString {
    append(card.body)
    card.emphasis.forEach { phrase ->
        val start = card.body.indexOf(phrase)
        if (start >= 0) addStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Medium), start, start + phrase.length)
    }
}
