package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FuguangWeekBoard(
    days: List<FuguangDayPlanUi>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("week-plan-board"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEach { day ->
            FuguangWeekDayColumn(day = day)
        }
    }
}

@Composable
fun FuguangWeekDayColumn(
    day: FuguangDayPlanUi,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = modifier.width(150.dp).height(430.dp).testTag("week-board-day-${day.id}"),
        shape = RoundedCornerShape(15.dp),
        color = colors.elevated,
        border = BorderStroke(
            width = if (day.changed) 1.5.dp else 1.dp,
            color = if (day.changed) colors.gold else colors.border,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.weekday, style = type.bodyStrong, color = colors.text)
                Spacer(Modifier.width(5.dp))
                Text(day.date, style = type.micro, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text("${day.entries.size} 段", style = type.micro, color = colors.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                day.entries.forEach { entry ->
                    FuguangWeekEntryRow(entry)
                }
            }
        }
    }
}

@Composable
fun FuguangWeekEntryRow(
    entry: FuguangAgendaEntryUi,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val accent = weekEntryAccent(entry.tone)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(fuguangAccentColor(accent), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMinute(entry.startMinute),
                    style = type.micro,
                    color = colors.textMuted,
                )
                if (entry.statusLabel != null) {
                    Spacer(Modifier.width(4.dp))
                    FuguangWeekTag(entry.statusLabel, accent)
                }
            }
            Text(
                text = entry.title,
                style = type.label,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.location.isNotBlank()) {
                Text(
                    text = entry.location,
                    style = type.micro,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun weekEntryAccent(tone: FuguangAgendaTone): FuguangWeekAccent = when (tone) {
    FuguangAgendaTone.STUDY -> FuguangWeekAccent.GREEN
    FuguangAgendaTone.CLASS -> FuguangWeekAccent.GOLD
    FuguangAgendaTone.FIXED -> FuguangWeekAccent.NEUTRAL
    FuguangAgendaTone.COMMUTE -> FuguangWeekAccent.BLUE
    FuguangAgendaTone.PRIVATE -> FuguangWeekAccent.ROSE
    FuguangAgendaTone.BUFFER -> FuguangWeekAccent.NEUTRAL
}

@Composable
fun FuguangWeekLegend(
    items: List<FuguangWeekLegendItemUi>,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(fuguangAccentColor(item.accent), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(5.dp))
                Text(item.label, style = type.micro, color = colors.textSecondary)
            }
        }
    }
}

@Composable
fun FuguangWeekNarrativeFooter(
    items: List<FuguangWeekNarrativeUi>,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.forEach { item ->
            Column {
                Text(item.title, style = type.bodyStrong, color = colors.text)
                Spacer(Modifier.height(4.dp))
                Text(item.body, style = type.caption, color = colors.textSecondary)
            }
        }
    }
}
