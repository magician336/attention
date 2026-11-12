package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FuguangW1WeekSwipe(
    days: List<FuguangWeekDayCardUi>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val dims = fuguangDims()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, 6))
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect(onSelectedChange)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(end = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(days.size) { index -> FuguangWeekDayCard(days[index]) }
            }
            Box(
                Modifier.align(Alignment.CenterEnd).width(40.dp).height(290.dp)
                    .background(Brush.horizontalGradient(listOf(roles.paper.copy(alpha = 0f), roles.paper))),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(days.size.coerceAtMost(7)) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(width = if (index == selectedIndex) 12.dp else 5.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(if (index == selectedIndex) roles.mainDeep else roles.line),
                )
            }
        }
    }
}

@Composable
private fun FuguangWeekDayCard(day: FuguangWeekDayCardUi) {
    val roles = fuguangRoles()
    val type = fuguangType()
    val dims = fuguangDims()
    Surface(
        modifier = Modifier.width(dims.cardWidth).height(290.dp),
        shape = RoundedCornerShape(10.dp),
        color = roles.surface,
        border = BorderStroke(1.dp, roles.line),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.weekday, style = type.t125.copy(fontWeight = FontWeight.Medium), color = roles.ink)
                Spacer(Modifier.width(5.dp))
                Text(day.date, style = type.t95, color = roles.ink3)
                Spacer(Modifier.weight(1f))
                Text(day.totalLabel, style = type.t95, color = roles.mainDeep)
            }
            Spacer(Modifier.height(8.dp))
            day.entries.take(7).forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.width(34.dp)) {
                        Text(entry.start, style = type.t8, color = roles.ink3)
                        Text(entry.end, style = type.t8, color = roles.ink3)
                    }
                    Box(Modifier.width(3.dp).height(28.dp).background(if (entry.isNew) roles.accentDeep else roles.mainDeep, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(7.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = type.t125.copy(fontSize = 11.5.sp), color = roles.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.location, style = type.t8.copy(fontSize = 8.5.sp), color = roles.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
