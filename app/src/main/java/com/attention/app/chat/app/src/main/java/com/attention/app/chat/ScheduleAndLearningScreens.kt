package com.attention.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FuguangDayScheduleScreen(
    state: FuguangDayScheduleState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("day-schedule-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = dims.screenTop, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.title, style = type.display, color = colors.text)
                        Spacer(Modifier.height(4.dp))
                        Text(state.subtitle, style = type.body, color = colors.textTertiary)
                    }
                    Surface(
                        onClick = actions::onAddSchedule,
                        modifier = Modifier.size(48.dp).testTag("schedule-add"),
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "添加日程", tint = colors.text)
                        }
                    }
                }
            }
            item {
                FuguangRangeSelector(selected = state.selectedRange, onSelect = actions::onSelectScheduleRange)
            }
            state.notice?.let { notice ->
                item {
                    FuguangNoticeBanner(notice = notice, onClick = actions::onOpenNotice)
                }
            }
            items(state.items.size) { index ->
                FuguangScheduleCard(item = state.items[index], onClick = { actions.onOpenSchedule(state.items[index].id) })
                state.gaps.filter { it.afterItemIndex == index }.forEach { gap ->
                    Spacer(Modifier.height(4.dp))
                    FuguangGapRow(gap)
                    Spacer(Modifier.height(4.dp))
                }
            }
            item {
                Text(
                    text = state.footer,
                    style = type.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        FuguangBottomNav(
            selected = FuguangBottomDestination.AGENDA,
            onSelect = actions::onNavigate,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun FuguangWeekScheduleScreen(
    state: FuguangWeekScheduleState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("week-schedule-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = dims.screenTop, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.title, style = type.display, color = colors.text)
                        Spacer(Modifier.height(4.dp))
                        Text(state.subtitle, style = type.body, color = colors.textTertiary)
                    }
                    FuguangPillButton(text = "今天", onClick = { actions.onSelectWeekDay(2) }, filled = false)
                }
            }
            item {
                FuguangRangeSelector(selected = state.selectedRange, onSelect = actions::onSelectScheduleRange)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.days.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(day.weekday, style = type.micro, color = colors.textTertiary)
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                onClick = { actions.onSelectWeekDay(day.index) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (day.isToday) colors.primarySecondary else Color.Transparent,
                                modifier = Modifier.heightIn(min = 42.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (day.isToday) Text("三 · 今天", style = type.micro, color = colors.white)
                                    Text(day.dayOfMonth, style = type.navigationTitle, color = if (day.isToday) colors.white else colors.text)
                                }
                            }
                        }
                    }
                }
            }
            item {
                FuguangWeekTimeline(state = state, actions = actions)
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.weeklyActualHours, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text("/ ${state.weeklyTargetHours}", style = type.body, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(8.dp))
                    FuguangProgressBar(progress = state.weeklyActualHours.substringBefore('h').toFloatOrNull()?.div(state.weeklyTargetHours.substringBefore('h').toFloatOrNull() ?: 1f) ?: 0f)
                    Spacer(Modifier.height(8.dp))
                    Text(state.weeklyDetail, style = type.caption, color = colors.textTertiary)
                }
            }
            item {
                Text(state.footer, style = type.caption, color = colors.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        FuguangBottomNav(
            selected = FuguangBottomDestination.AGENDA,
            onSelect = actions::onNavigate,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FuguangRangeSelector(
    selected: FuguangScheduleRange,
    onSelect: (FuguangScheduleRange) -> Unit,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = colors.surfaceMuted,
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FuguangScheduleRange.entries.forEach { range ->
                val active = range == selected
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape)
                        .background(if (active) colors.primarySecondary else Color.Transparent)
                        .clickable { onSelect(range) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(range.label, style = if (active) type.label else type.body, color = if (active) colors.white else colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun FuguangNoticeBanner(notice: FuguangScheduleNotice, onClick: () -> Unit) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceMuted,
        border = BorderStroke(1.dp, colors.goldSoft),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notice.source, style = type.micro, color = colors.gold)
                Text(notice.message, style = type.bodyStrong, color = colors.text)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = colors.gold, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun FuguangScheduleCard(item: FuguangScheduleItem, onClick: () -> Unit) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val background = when (item.tone) {
        FuguangScheduleTone.ACTIVE -> colors.primaryTint.copy(alpha = 0.55f)
        FuguangScheduleTone.WARNING -> colors.surfaceMuted
        FuguangScheduleTone.NORMAL -> colors.surface
    }
    val border = when (item.tone) {
        FuguangScheduleTone.ACTIVE -> colors.primaryTint
        FuguangScheduleTone.WARNING -> colors.goldSoft
        FuguangScheduleTone.NORMAL -> colors.border
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.width(52.dp)) {
                Text(item.startLabel, style = type.caption, color = if (item.tone == FuguangScheduleTone.ACTIVE) colors.primaryDeep else colors.textTertiary)
                Text(item.endLabel, style = type.micro, color = colors.textTertiary)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = type.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(item.detail, style = type.label, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun FuguangGapRow(gap: FuguangGapItem) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = colors.surfaceMuted,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Text(
            text = gap.label,
            style = type.micro,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FuguangWeekTimeline(state: FuguangWeekScheduleState, actions: FuguangAppActions) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val hourHeight = 28.dp
    val timelineStartMinute = 8 * 60
    Row(
        modifier = Modifier.fillMaxWidth().height(430.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.days.forEach { day ->
            Box(
                modifier = Modifier.weight(1f).height(430.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (day.isToday) colors.primaryTint.copy(alpha = 0.55f) else colors.surfaceMuted.copy(alpha = 0.65f)),
            ) {
                state.events.filter { it.dayIndex == day.index }.forEach { event ->
                    val y = (((event.startMinuteOfDay - timelineStartMinute).coerceAtLeast(0)) / 60f * hourHeight.value).dp
                    val eventColor = when (event.tone) {
                        FuguangScheduleTone.ACTIVE -> colors.primarySecondary
                        FuguangScheduleTone.WARNING -> colors.goldSoft
                        FuguangScheduleTone.NORMAL -> colors.roseSoft
                    }
                    Surface(
                        onClick = { actions.onOpenSchedule(event.id) },
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = y).padding(horizontal = 2.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(7.dp),
                        color = eventColor,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Text(event.timeLabel, style = type.micro, color = colors.textSecondary, maxLines = 1)
                            Text(event.title, style = type.micro, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FuguangLearningScreen(
    state: FuguangLearningState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()
    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("learning-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = dims.screenTop, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.title, style = type.display, color = colors.text)
                        Spacer(Modifier.height(4.dp))
                        Text(state.subtitle, style = type.body, color = colors.textTertiary)
                    }
                    FuguangPillButton(text = state.reportLabel, onClick = actions::onOpenWeeklyReport, filled = false)
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.longTermTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text(state.longTermMeta, style = type.caption, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(12.dp))
                    state.goals.forEachIndexed { index, goal ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(goal.color))
                            Spacer(Modifier.width(8.dp))
                            Text(goal.title, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                            Text(goal.detail, style = type.caption, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(5.dp))
                        FuguangProgressBar(progress = goal.progress, fillColor = goal.color)
                    }
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.weeklyTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text("${state.weeklyActual} · ${state.weeklyRate}", style = type.label, color = colors.primaryDeep)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        state.weeklySegments.forEach { segment ->
                            Box(Modifier.weight(segment.value.coerceAtLeast(0.01f)).height(10.dp).clip(CircleShape).background(segment.color))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(state.weeklyDetail, style = type.caption, color = colors.textTertiary)
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.reviewTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text(state.reviewMeta, style = type.caption, color = colors.textTertiary)
                    }
                    state.reviewItems.forEach { item ->
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable { actions.onOpenReviewItem(item.id) }) {
                            Text(item.title, style = type.body, color = colors.text, modifier = Modifier.weight(1f))
                            Text(item.note, style = type.caption, color = colors.rose)
                        }
                    }
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.chartTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text(state.chartMeta, style = type.caption, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        state.chartValues.forEachIndexed { index, value ->
                            Box(Modifier.weight(1f).height((20 + value.coerceIn(0f, 1f) * 48).dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(if (index == state.chartValues.lastIndex) colors.primarySecondary else colors.primaryTint))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.countdowns.forEach { countdown ->
                        FuguangSurfaceCard(modifier = Modifier.weight(1f)) {
                            Text(countdown.title, style = type.caption, color = colors.textTertiary)
                            Text(countdown.dateLabel, style = type.micro, color = colors.textTertiary)
                            Spacer(Modifier.height(6.dp))
                            Text(countdown.days, style = type.title, color = colors.text)
                        }
                    }
                }
            }
        }
        FuguangBottomNav(selected = FuguangBottomDestination.STUDY, onSelect = actions::onNavigate, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun FuguangSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = fuguangPalette()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}



