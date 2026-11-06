package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FuguangWeekPageHeader(
    title: String,
    rangeLabel: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            FuguangWeekIconBubble(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回对话",
                background = colors.elevated,
                tint = colors.text,
                onClick = onBack,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = type.display, color = colors.text, modifier = Modifier.weight(1f))
        Text(rangeLabel, style = type.micro, color = colors.textMuted)
    }
}

@Composable
fun FuguangDaySelector(
    days: List<FuguangDayItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            days.forEach { day ->
                val selected = day.selected
                Surface(
                    onClick = { onSelect(day.id) },
                    modifier = Modifier.weight(1f).testTag("week-day-${day.id}"),
                    shape = RoundedCornerShape(15.dp),
                    color = if (selected) colors.primary else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = day.weekday,
                                style = type.micro,
                                color = if (selected) colors.white.copy(alpha = 0.88f) else colors.textMuted,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = day.date,
                                style = type.label,
                                color = if (selected) colors.white else colors.text,
                            )
                        }
                        FuguangDayMarkerDot(
                            marker = day.marker,
                            selected = selected,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FuguangDayMarkerDot(
    marker: FuguangDayMarker,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    when (marker) {
        FuguangDayMarker.NONE -> Unit
        FuguangDayMarker.DOT -> Box(
            modifier = modifier.size(5.dp).clip(CircleShape).background(if (selected) colors.white else colors.gold),
        )
        FuguangDayMarker.CONFLICT -> Box(
            modifier = modifier.size(5.dp).clip(CircleShape).background(if (selected) colors.white else colors.rose),
        )
        FuguangDayMarker.RING -> Box(
            modifier = modifier.size(7.dp).clip(CircleShape).background(if (selected) colors.white else colors.primary),
        )
    }
}

@Composable
fun FuguangDayTimeline(
    entries: List<FuguangAgendaEntryUi>,
    modifier: Modifier = Modifier,
    startHour: Int = 8,
    endHour: Int = 22,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val hourHeight = 46.dp
    val startMinute = startHour * 60
    val endMinute = endHour * 60
    val totalHeight = hourHeight * ((endMinute - startMinute) / 60f)
    val timeLabels = (startHour..endHour step 2).toList()

    Row(modifier = modifier.fillMaxWidth().height(totalHeight)) {
        Box(modifier = Modifier.width(46.dp).fillMaxHeight()) {
            timeLabels.forEachIndexed { index, hour ->
                Text(
                    text = "%02d:00".format(hour),
                    style = type.micro,
                    color = colors.textMuted,
                    modifier = Modifier.offset(y = hourHeight * (2f * index)).padding(top = 2.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            timeLabels.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = hourHeight * (2f * index))
                        .height(1.dp)
                        .background(colors.border),
                )
            }
            entries.forEach { entry ->
                val top = hourHeight * ((entry.startMinute - startMinute).coerceAtLeast(0) / 60f)
                val height = (hourHeight * ((entry.endMinute - entry.startMinute).coerceAtLeast(30) / 60f))
                    .coerceAtLeast(26.dp)
                FuguangTimelineEventCard(
                    entry = entry,
                    compact = entry.endMinute - entry.startMinute < 45,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .padding(start = 2.dp)
                        .offset(y = top)
                        .height(height),
                )
            }
        }
    }
}

private fun Dp.coerceAtLeast(minimum: Dp): Dp = if (this < minimum) minimum else this

@Composable
fun FuguangTimelineEventCard(
    entry: FuguangAgendaEntryUi,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val accent = entryAccent(entry.tone)
    Surface(
        modifier = modifier.testTag("timeline-event-${entry.id}"),
        shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 10.dp, bottomEnd = 10.dp),
        color = fuguangAccentSoftColor(accent),
        border = BorderStroke(1.dp, fuguangAccentColor(accent).copy(alpha = 0.42f)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(fuguangAccentColor(accent)),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = entry.title,
                    style = if (compact) type.micro else type.caption,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && entry.location.isNotBlank()) {
                    Text(
                        text = entry.location,
                        style = type.micro,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun entryAccent(tone: FuguangAgendaTone): FuguangWeekAccent = when (tone) {
    FuguangAgendaTone.STUDY -> FuguangWeekAccent.GREEN
    FuguangAgendaTone.CLASS -> FuguangWeekAccent.GOLD
    FuguangAgendaTone.FIXED -> FuguangWeekAccent.NEUTRAL
    FuguangAgendaTone.COMMUTE -> FuguangWeekAccent.BLUE
    FuguangAgendaTone.PRIVATE -> FuguangWeekAccent.ROSE
    FuguangAgendaTone.BUFFER -> FuguangWeekAccent.NEUTRAL
}

@Composable
fun FuguangSingleDayDetailCard(
    day: FuguangDayPlanUi,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    FuguangWeekCard(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(day.weekday, style = type.section, color = colors.text)
                Spacer(Modifier.width(6.dp))
                Text(day.date, style = type.label, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text("${day.entries.size} 段", style = type.micro, color = colors.textMuted)
            }
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    FuguangWeekInsetDivider(modifier = Modifier.padding(horizontal = 18.dp))
                }
                FuguangDayDetailRow(entry)
            }
            if (footer != null) {
                Text(
                    text = footer,
                    style = type.micro,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun FuguangDayDetailRow(entry: FuguangAgendaEntryUi) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val accent = entryAccent(entry.tone)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(66.dp)
                .background(fuguangAccentColor(accent), RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.width(54.dp)) {
            Text(formatMinute(entry.startMinute), style = type.micro, color = colors.textSecondary)
            Text(formatMinute(entry.endMinute), style = type.micro, color = colors.textMuted)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = type.bodyStrong, color = colors.text)
            Spacer(Modifier.height(3.dp))
            Text(entry.location, style = type.micro, color = colors.textMuted)
        }
    }
}
