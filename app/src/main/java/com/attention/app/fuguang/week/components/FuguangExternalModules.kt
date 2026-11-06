package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FuguangFeishuMessageCard(
    card: FuguangExternalCardUi,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = colors.block.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("飞", style = type.label, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = colors.elevated,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(colors.goldDeep))
                Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(18.dp).background(colors.goldDeep, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("飞", style = type.micro, color = colors.white)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(card.sender, style = type.label, color = colors.text, modifier = Modifier.weight(1f))
                        Text(card.group, style = type.micro, color = colors.textMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = externalQuote(card),
                        style = type.body,
                        color = colors.text,
                    )
                    if (card.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            card.tags.forEachIndexed { index, tag ->
                                FuguangWeekTag(
                                    text = tag,
                                    accent = if (index == card.tags.lastIndex) FuguangWeekAccent.GOLD else FuguangWeekAccent.NEUTRAL,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun externalQuote(card: FuguangExternalCardUi): AnnotatedString = buildAnnotatedString {
    append(card.quote)
    card.emphasizedPhrases.forEach { phrase ->
        val start = card.quote.indexOf(phrase)
        if (start >= 0) {
            addStyle(
                style = SpanStyle(
                    color = Color(0xFF9A772B),
                    fontWeight = FontWeight.SemiBold,
                ),
                start = start,
                end = start + phrase.length,
            )
        }
    }
}

@Composable
fun FuguangConflictPreviewCard(
    preview: FuguangConflictPreviewUi,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    FuguangWeekCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(preview.title, style = type.label, color = colors.text)
            Spacer(Modifier.height(12.dp))
            preview.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = type.micro,
                        color = colors.textMuted,
                        modifier = Modifier.width(30.dp),
                    )
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.segments.forEach { segment ->
                            Surface(
                                modifier = Modifier.weight(segment.weight.coerceAtLeast(0.1f)).height(24.dp),
                                shape = RoundedCornerShape(5.dp),
                                color = if (segment.outlined) Color.Transparent else fuguangAccentSoftColor(segment.accent),
                                border = if (segment.outlined) {
                                    BorderStroke(1.dp, fuguangAccentColor(segment.accent))
                                } else {
                                    null
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = segment.label,
                                        style = type.micro,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(start = 30.dp)) {
                Text(preview.startLabel, style = type.micro, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(preview.middleLabel, style = type.micro, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(preview.endLabel, style = type.micro, color = colors.textMuted)
            }
        }
    }
}

@Composable
fun FuguangExternalSummaryLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceMuted,
    ) {
        Text(
            text = text,
            style = type.micro,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
