package com.attention.app.fuguang.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attention.app.R
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun RecognizeScreen(
    beat: PrototypeBeat,
    recap: List<PrototypeRecapItem>,
    recapReady: Boolean,
    editingRecapId: String?,
    editingRecapText: String,
    onEditingRecapTextChange: (String) -> Unit,
    extraMessages: List<Pair<String, String>>,
    onToggleRecap: (String) -> Unit,
    onStartEditRecap: (String) -> Unit,
    onSaveRecapEdit: () -> Unit,
    onCancelRecapEdit: () -> Unit,
    onConfirmRecap: () -> Unit,
    onResetRecap: () -> Unit,
    onContinue: () -> Unit,
) {
    val type = fuguangPrototypeType
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("recognize-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "今天 21:47",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            UserBubble(
                "周日临时有个去北京的面试，一整天都不在。概率论第三单元的习题和管理会计的复习得挪到周中，但一定要在这门课上完之后。",
            )
        }
        item {
            AssistantBubble(
                text = prototypeAnnotated(
                    "我把你这段话拆成三条要求，你看我听得对不对 —— 哪条歪了点一下就能改。",
                    listOf("三条要求"),
                ),
            )
        }
        item {
            if (recapReady) {
                PrototypeCard(background = FuguangPrototypeColors.lime) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(18.dp).background(FuguangPrototypeColors.sageDeep, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = FuguangPrototypeColors.lime, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${recap.size} 条要求都对${if (recap.any { it.editedText != null }) " · 你改了 ${recap.count { it.editedText != null }} 条" else ""} · 影响 4 天",
                            style = type.bodyStrong,
                            color = FuguangPrototypeColors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "再对一遍",
                            style = type.caption,
                            color = FuguangPrototypeColors.ink2,
                            modifier = Modifier.clickable(onClick = onResetRecap).padding(4.dp),
                        )
                    }
                }
            } else {
                RecapCard(
                    recap = recap,
                    editingRecapId = editingRecapId,
                    editingRecapText = editingRecapText,
                    onEditingRecapTextChange = onEditingRecapTextChange,
                    onToggleRecap = onToggleRecap,
                    onStartEditRecap = onStartEditRecap,
                    onSaveRecapEdit = onSaveRecapEdit,
                    onCancelRecapEdit = onCancelRecapEdit,
                    onConfirmRecap = onConfirmRecap,
                )
            }
        }
        item {
            PrototypeCard {
                Box {
                    PrototypeCat(
                        drawable = beat.catBody(),
                        size = 44.dp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-10).dp),
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = prototypeAnnotated(
                                "概率论周五 08:00–12:45 上课，习题只能排在下课后",
                                listOf("08:00–12:45"),
                                FuguangPrototypeColors.ink,
                            ),
                            style = type.caption,
                            color = FuguangPrototypeColors.ink2,
                            modifier = Modifier.padding(end = 48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("排完了 · 周三最晚收工", style = type.label, color = FuguangPrototypeColors.ink2)
                                Text("22:00", style = type.number, color = FuguangPrototypeColors.ink)
                            }
                            PrototypeButton("看调整 ›", onContinue, style = PrototypeButtonStyle.LIME)
                        }
                    }
                }
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RecapCard(
    recap: List<PrototypeRecapItem>,
    editingRecapId: String?,
    editingRecapText: String,
    onEditingRecapTextChange: (String) -> Unit,
    onToggleRecap: (String) -> Unit,
    onStartEditRecap: (String) -> Unit,
    onSaveRecapEdit: () -> Unit,
    onCancelRecapEdit: () -> Unit,
    onConfirmRecap: () -> Unit,
) {
    val type = fuguangPrototypeType
    val left = recap.count { !it.confirmed }
    PrototypeCard {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("我来确定一下你的要求", style = type.title, color = FuguangPrototypeColors.ink)
                Spacer(Modifier.weight(1f))
                PrototypeTinyTag("听成 ${recap.size} 条", FuguangPrototypeColors.pink, FuguangPrototypeColors.hotDeep)
            }
            recap.forEach { item ->
                if (item.id == editingRecapId) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FuguangPrototypeColors.paper,
                        border = BorderStroke(2.dp, FuguangPrototypeColors.hotDeep),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            BasicTextField(
                                value = editingRecapText,
                                onValueChange = onEditingRecapTextChange,
                                modifier = Modifier.fillMaxWidth().testTag("recap-edit-input"),
                                textStyle = type.body.copy(color = FuguangPrototypeColors.ink),
                                cursorBrush = SolidColor(FuguangPrototypeColors.hotDeep),
                            )
                            Text(
                                "只改这一条要求，另外 ${recap.size - 1} 条不动",
                                style = type.micro,
                                color = FuguangPrototypeColors.ink3,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                PrototypeButton("存下这条", onSaveRecapEdit, Modifier.weight(1f), PrototypeButtonStyle.LIME)
                                PrototypeButton("算了", onCancelRecapEdit, Modifier.weight(1f), PrototypeButtonStyle.GHOST)
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("recap-${item.id}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.confirmed) FuguangPrototypeColors.sage else FuguangPrototypeColors.paper,
                        border = BorderStroke(1.dp, FuguangPrototypeColors.line),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.editedText ?: item.title,
                                    style = type.label.copy(fontWeight = FontWeight.Bold),
                                    color = FuguangPrototypeColors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.editedText != null) {
                                    PrototypeTinyTag("改过", FuguangPrototypeColors.lime, FuguangPrototypeColors.sageDeep)
                                } else {
                                    Text(item.timeLabel, style = type.micro, color = FuguangPrototypeColors.ink3)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(item.subtitle, style = type.caption, color = FuguangPrototypeColors.ink2)
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                PrototypeButton(
                                    if (item.confirmed) "✓ 这条说对了" else "对，是这意思",
                                    { onToggleRecap(item.id) },
                                    Modifier.weight(1f),
                                    if (item.confirmed) PrototypeButtonStyle.PRIMARY else PrototypeButtonStyle.GHOST,
                                )
                                PrototypeButton(
                                    "改一下",
                                    { onStartEditRecap(item.id) },
                                    Modifier.weight(1f),
                                    PrototypeButtonStyle.GHOST,
                                )
                            }
                        }
                    }
                }
            }
            PrototypeButton(
                label = if (left > 0) "还有 $left 条没确认" else "都对，往下算 ›",
                onClick = onConfirmRecap,
                modifier = Modifier.fillMaxWidth(),
                style = PrototypeButtonStyle.LIME,
                enabled = left == 0,
            )
        }
    }
}

@Composable
fun AdjustScreen(
    beat: PrototypeBeat,
    items: List<PrototypeAdjustmentItem>,
    adopted: List<Boolean>,
    selfEdit: Boolean,
    extraMessages: List<Pair<String, String>>,
    onToggleAdopt: (String) -> Unit,
    onToggleSelfEdit: () -> Unit,
    onSaveSelfEdit: () -> Unit,
    onChooseAlternative: (String, String) -> Unit,
    onContinue: () -> Unit,
    onBackToRecognize: () -> Unit,
) {
    val type = fuguangPrototypeType
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("adjust-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PrototypeReceipt("已认领 3 件事 · 影响 4 天", onClick = onBackToRecognize) }
        item {
            Text(
                "今天 21:49",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            AssistantBubble(
                text = prototypeAnnotated(
                    "四件事动，其余不动。不认可的点一下就摘掉。",
                    listOf("不认可的点一下就摘掉"),
                ),
            )
        }
        item {
            PrototypeCard {
                Box {
                    PrototypeCat(
                        drawable = beat.catBody(),
                        size = 56.dp,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-12).dp),
                    )
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
                            Text("调整后的安排", style = type.title, color = FuguangPrototypeColors.ink)
                            Spacer(Modifier.weight(1f))
                            Text("影响 4 天", style = type.caption, color = FuguangPrototypeColors.ink3)
                        }
                        items.forEachIndexed { index, item ->
                            if (index > 0) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 13.dp).height(1.dp).background(FuguangPrototypeColors.line))
                            }
                            AdjustmentRow(
                                number = index + 1,
                                item = item,
                                adopted = adopted.getOrElse(index) { true },
                                selfEdit = selfEdit,
                                onClick = { onToggleAdopt(item.id) },
                                onChooseAlternative = { onChooseAlternative(item.id, it) },
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(FuguangPrototypeColors.line))
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "已采纳 ${adopted.count { it }}/4",
                                style = type.caption,
                                color = FuguangPrototypeColors.ink3,
                                modifier = Modifier.weight(1f),
                            )
                            PrototypeButton(
                                if (selfEdit) "存下这一版" else "我自己调",
                                if (selfEdit) onSaveSelfEdit else onToggleSelfEdit,
                                style = PrototypeButtonStyle.GHOST,
                            )
                            PrototypeButton("就按这个改", onContinue, style = PrototypeButtonStyle.LIME)
                        }
                    }
                }
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AdjustmentRow(
    number: Int,
    item: PrototypeAdjustmentItem,
    adopted: Boolean,
    selfEdit: Boolean,
    onClick: () -> Unit,
    onChooseAlternative: (String) -> Unit,
) {
    val type = fuguangPrototypeType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adjustment-${item.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(
                if (adopted) FuguangPrototypeColors.lime else FuguangPrototypeColors.ink.copy(alpha = 0.08f),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = type.caption.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.ink)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = type.label.copy(fontWeight = FontWeight.Bold),
                color = if (adopted) FuguangPrototypeColors.ink else FuguangPrototypeColors.ink3,
                textDecoration = if (adopted) TextDecoration.None else TextDecoration.LineThrough,
            )
            val selected = item.selectedAlternative
            if (selected == null && item.flow.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.flow.forEachIndexed { index, value ->
                        if (index > 0) Text("+", style = type.micro, color = FuguangPrototypeColors.hotDeep)
                        Text(value, style = type.micro.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.hotDeep)
                    }
                }
            }
            Text(
                selected ?: item.subtitle,
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (selfEdit && adopted) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.alternatives.forEach { alternative ->
                        Surface(
                            onClick = { onChooseAlternative(alternative) },
                            shape = CircleShape,
                            color = if (selected == alternative) FuguangPrototypeColors.hotDeep else FuguangPrototypeColors.paper,
                            border = if (selected == alternative) null else BorderStroke(1.4.dp, FuguangPrototypeColors.ink.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                alternative,
                                style = type.micro,
                                color = if (selected == alternative) FuguangPrototypeColors.white else FuguangPrototypeColors.ink2,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = if (adopted) FuguangPrototypeColors.hot else FuguangPrototypeColors.paper,
            border = if (adopted) null else BorderStroke(1.4.dp, FuguangPrototypeColors.ink.copy(alpha = 0.24f)),
        ) {
            Text(
                if (adopted) "采纳" else "保持原样",
                style = type.micro.copy(fontWeight = FontWeight.Bold),
                color = if (adopted) FuguangPrototypeColors.ink else FuguangPrototypeColors.ink2,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun DayScreen(
    beat: PrototypeBeat,
    days: List<PrototypeDay>,
    selectedDayIndex: Int,
    mealKept: Boolean,
    customized: Boolean,
    extraMessages: List<Pair<String, String>>,
    onSelectDay: (Int) -> Unit,
    onBackToAdjust: () -> Unit,
    onBackToRecognize: () -> Unit,
    onContinue: () -> Unit,
    onMealKeep: (Boolean) -> Unit,
) {
    val type = fuguangPrototypeType
    val day = days[selectedDayIndex]
    val mealShortages = prototypeMealShortages(day)
    val mealShort = mealShortages.map { it.label }
    val mealNeed = prototypeMealNeedDuration(mealShortages)
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("day-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PrototypeReceipt(
                text = if (customized) "按你改的 4 处写入" else "已调整 4 处 · 面试当天留空",
                collapsedCount = 1,
                onClick = onBackToRecognize,
            )
        }
        item {
            Text(
                "今天 21:50",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.weekday, style = type.section, color = FuguangPrototypeColors.ink)
                Spacer(Modifier.width(6.dp))
                Text(day.dateLabel, style = type.title, color = FuguangPrototypeColors.ink3)
                if (day.id in setOf("wed", "thu", "sat")) {
                    Spacer(Modifier.width(7.dp))
                    PrototypeTinyTag("改过", FuguangPrototypeColors.lime, FuguangPrototypeColors.sageDeep)
                }
                Spacer(Modifier.weight(1f))
                Text("11/2 – 11/8", style = type.caption, color = FuguangPrototypeColors.ink3)
            }
        }
        item {
            PrototypeDayBar(
                days = days,
                selectedIndex = selectedDayIndex,
                markers = mapOf(2 to PrototypeDayMarker.CHANGED, 3 to PrototypeDayMarker.CHANGED, 5 to PrototypeDayMarker.CHANGED),
                onSelect = onSelectDay,
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = FuguangPrototypeColors.ai,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayNote(day.weekday), style = type.label.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.ink2, modifier = Modifier.weight(1f))
                    PrototypeCat(R.drawable.fuguang_cat_sit, 52.dp)
                }
            }
        }
        item {
            PrototypeCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${day.weekday} · 单日时间轴", style = type.title, color = FuguangPrototypeColors.ink)
                        Spacer(Modifier.weight(1f))
                        Text("8:00–22:30", style = type.micro, color = FuguangPrototypeColors.ink3)
                    }
                    Spacer(Modifier.height(10.dp))
                    Timeline(day = day, beat = beat)
                }
            }
        }
        if (mealShort.isNotEmpty()) {
            item {
                if (mealKept) {
                    Surface(shape = RoundedCornerShape(12.dp), color = FuguangPrototypeColors.lime) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PrototypeCat(R.drawable.fuguang_cat_doze, 50.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "已给 ${day.weekday} 留出 $mealNeed · 只在你手机里生效，周计划不占位",
                                style = type.caption,
                                color = FuguangPrototypeColors.ink,
                            )
                        }
                    }
                } else {
                    PrototypeCard(background = FuguangPrototypeColors.pink) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PrototypeCat(R.drawable.fuguang_cat_tilthead, 50.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (mealShort.any { it != "午觉" }) "排完发现吃不上饭" else "排完发现睡不上觉",
                                    style = type.title,
                                    color = FuguangPrototypeColors.hotDeep,
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                prototypeMealWhy(day, mealShortages),
                                style = type.caption,
                                color = FuguangPrototypeColors.ink2,
                            )
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrototypeButton("不用，我自己挤", { onMealKeep(false) }, Modifier.weight(1f), PrototypeButtonStyle.GHOST)
                                PrototypeButton("留出 $mealNeed", { onMealKeep(true) }, Modifier.weight(1f), PrototypeButtonStyle.LIME)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrototypeButton("‹ 调整条目", onBackToAdjust, Modifier.weight(1f), PrototypeButtonStyle.GHOST)
                PrototypeButton("继续 · 下一条", onContinue, Modifier.weight(1.2f), PrototypeButtonStyle.LIME)
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PrototypeDayBar(
    days: List<PrototypeDay>,
    selectedIndex: Int,
    markers: Map<Int, PrototypeDayMarker>,
    onSelect: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = FuguangPrototypeColors.paper,
        border = BorderStroke(1.dp, FuguangPrototypeColors.ink.copy(alpha = 0.2f)),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            days.forEachIndexed { index, day ->
                PrototypeDayChip(
                    weekday = day.weekday,
                    date = day.dateLabel,
                    selected = index == selectedIndex,
                    marker = markers[index] ?: PrototypeDayMarker.NONE,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun Timeline(day: PrototypeDay, beat: PrototypeBeat) {
    val type = fuguangPrototypeType
    val firstMinute = day.events.minOfOrNull { it.startMinute } ?: (8 * 60)
    val startHour = max(6f, floor((firstMinute / 60f) * 2f) / 2f - 0.5f)
    val endHour = 22.5f
    val hourHeight = 40.dp
    val totalHeight = hourHeight * (endHour - startHour)
    Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
        for (hour in ceil(startHour).toInt()..22) {
            val top = hourHeight * (hour - startHour)
            Box(
                modifier = Modifier.fillMaxWidth().offset(y = top).height(1.dp).background(FuguangPrototypeColors.line),
            )
            Text(
                text = "%02d".format(hour),
                style = type.micro,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.offset(y = top - 7.dp),
            )
        }
        day.events.forEach { event ->
            val top = hourHeight * ((event.startMinute / 60f) - startHour)
            val height = (hourHeight * ((event.endMinute - event.startMinute) / 60f)).coerceAtLeast(26.dp) - 3.dp
            TimelineBlock(
                event = event,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp)
                    .offset(y = top)
                    .height(height),
            )
        }
        largestTimelineGap(day, startHour, endHour)?.let { gap ->
            val gapHeight = hourHeight * (gap.second - gap.first)
            if (gapHeight >= 52.dp) {
                val catTop = hourHeight * (gap.first - startHour) + (gapHeight - 34.dp) / 2
                PrototypeCat(
                    drawable = R.drawable.fuguang_cathead_sleep,
                    size = 34.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = catTop, x = (-10).dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineBlock(event: PrototypeEvent, modifier: Modifier = Modifier) {
    val type = fuguangPrototypeType
    val background = eventColor(event.kind)
    val border = if (event.state == PrototypeEventState.ADDED) BorderStroke(2.dp, FuguangPrototypeColors.hotDeep) else null
    Surface(
        modifier = modifier.testTag("timeline-${event.id}"),
        shape = RoundedCornerShape(7.dp),
        color = if (event.state == PrototypeEventState.CANCELED) FuguangPrototypeColors.ink.copy(alpha = 0.08f) else background,
        border = border,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(3.dp).fillMaxHeight().background(
                    when (event.kind) {
                        PrototypeEventKind.COURSE -> FuguangPrototypeColors.hotDeep
                        PrototypeEventKind.IELTS -> FuguangPrototypeColors.sageDeep
                        PrototypeEventKind.PREP -> FuguangPrototypeColors.hotDeep
                        PrototypeEventKind.TASK -> FuguangPrototypeColors.limeDeep
                        PrototypeEventKind.MEETING -> FuguangPrototypeColors.ink2
                        PrototypeEventKind.MOVE -> FuguangPrototypeColors.ink2
                    },
                ),
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    "${formatMinute(event.startMinute)}–${formatMinute(event.endMinute)}",
                    style = type.micro,
                    color = FuguangPrototypeColors.ink2,
                )
                Text(
                    event.title,
                    style = type.caption.copy(fontWeight = FontWeight.Bold),
                    color = FuguangPrototypeColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (event.state == PrototypeEventState.CANCELED) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (event.isTall() && event.subtitle.isNotBlank()) {
                    Text(
                        event.subtitle,
                        style = type.micro,
                        color = FuguangPrototypeColors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun FeishuScreen(
    beat: PrototypeBeat,
    later: Boolean,
    extraMessages: List<Pair<String, String>>,
    onLater: () -> Unit,
    onFindPlace: () -> Unit,
    onBackToRecognize: () -> Unit,
) {
    val type = fuguangPrototypeType
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("feishu-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PrototypeReceipt("21:50 整周已重排 · 4 天有变化", collapsedCount = 2, onClick = onBackToRecognize) }
        item {
            Text(
                "11/4 周三 13:52",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            AssistantBubble(
                text = prototypeAnnotated("凌晨那版已经写进表了。飞书刚推来一条，我摘原文给你看。"),
            )
        }
        item {
            Box {
                PrototypeCard {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(34.dp).background(FuguangPrototypeColors.sageDeep, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("飞", style = type.title, color = FuguangPrototypeColors.white)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("刘老师", style = type.label.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.ink)
                            Spacer(Modifier.width(7.dp))
                            Text("管理会计 3 班", style = type.micro, color = FuguangPrototypeColors.ink3)
                            Spacer(Modifier.weight(1f))
                            Text("13:52", style = type.micro, color = FuguangPrototypeColors.ink3)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            prototypeAnnotated(
                                "「周二下午的财务会计改到周四下午两点到四点四十，二主楼 B301。周二那节不上了。」",
                                listOf("周四下午两点到四点四十"),
                                FuguangPrototypeColors.ink,
                            ),
                            style = type.body,
                            color = FuguangPrototypeColors.ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PrototypeTinyTag("1 移出 · 1 新增", FuguangPrototypeColors.lime, FuguangPrototypeColors.sageDeep)
                            PrototypeTinyTag("撞了 1 处", FuguangPrototypeColors.pink, FuguangPrototypeColors.hotDeep)
                        }
                    }
                    }
                }
                PrototypeCat(
                    drawable = R.drawable.fuguang_cat_shy,
                    size = 50.dp,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = (-24).dp),
                )
            }
        }
        item {
            Box {
                PrototypeCard {
                    Column(modifier = Modifier.padding(14.dp)) {
                    Text("周四 12:30 → 14:00 之间", style = type.label.copy(fontWeight = FontWeight.Bold), color = FuguangPrototypeColors.ink2)
                    Text("只剩 20 分钟", style = fuguangPrototypeType.number.copy(fontSize = 27.sp), color = FuguangPrototypeColors.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConflictChip("12:30\n例会", FuguangPrototypeColors.ai, Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FuguangPrototypeColors.ink3, modifier = Modifier.padding(horizontal = 4.dp))
                        ConflictChip("14:00\n财务会计 FA2", FuguangPrototypeColors.pink, Modifier.weight(1.35f), outlined = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrototypeButton(if (later) "已搁着 · 不催你" else "先搁着", onLater, style = PrototypeButtonStyle.GHOST)
                        Spacer(Modifier.weight(1f))
                        PrototypeButton(if (later) "还是看看位置 →" else "帮我找位置 →", onFindPlace, style = PrototypeButtonStyle.LIME)
                    }
                    if (later) {
                        Spacer(Modifier.height(7.dp))
                        Text("周四 14:00 现在还是雅思写作，表没动。", style = type.micro, color = FuguangPrototypeColors.ink3)
                    }
                    }
                }
                PrototypeCat(
                    drawable = R.drawable.fuguang_cat_reach,
                    size = 54.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = (-26).dp),
                )
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ConflictChip(
    label: String,
    background: Color,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(6.dp),
        color = background,
        border = if (outlined) BorderStroke(1.8.dp, FuguangPrototypeColors.hotDeep) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = fuguangPrototypeType.caption,
                color = FuguangPrototypeColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun RelocateScreen(
    beat: PrototypeBeat,
    options: List<PrototypeRelocationOption>,
    picked: Char?,
    skipWeek: Boolean,
    extraMessages: List<Pair<String, String>>,
    onPick: (Char) -> Unit,
    onSkipWeek: () -> Unit,
    onApply: () -> Unit,
    onBackToRecognize: () -> Unit,
) {
    val type = fuguangPrototypeType
    val pickedOption = options.firstOrNull { it.id == picked }
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("relocate-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PrototypeReceipt("飞书改课已认下 · 撞了周四 14:00", collapsedCount = 3, onClick = onBackToRecognize) }
        item {
            Text(
                "11/4 周三 13:53",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            AssistantBubble(
                text = prototypeAnnotated(
                    "周四 14:00 的雅思写作被顶掉了，整周没有现成空位。那放哪儿？",
                    listOf("雅思写作", "那放哪儿？"),
                ),
            )
        }
        item {
            Box {
                PrototypeCard {
                    Column(modifier = Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("雅思写作放哪天", style = type.title, color = FuguangPrototypeColors.ink)
                        Spacer(Modifier.weight(1f))
                        Text("代价不一样", style = type.caption, color = FuguangPrototypeColors.ink3)
                    }
                    Spacer(Modifier.height(10.dp))
                    options.forEach { option ->
                        RelocationOptionRow(
                            option = option,
                            selected = option.id == picked,
                            onClick = { onPick(option.id) },
                        )
                        Spacer(Modifier.height(9.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrototypeButton(if (skipWeek) "记 0 小时了" else "先不改", onSkipWeek, style = PrototypeButtonStyle.GHOST)
                        Spacer(Modifier.weight(1f))
                        PrototypeButton(
                            if (pickedOption == null) "先挑一个落点" else "就放${pickedOption.time}",
                            onApply,
                            style = PrototypeButtonStyle.LIME,
                            enabled = pickedOption != null,
                        )
                    }
                    if (skipWeek) {
                        Spacer(Modifier.height(7.dp))
                        Text("本周雅思写作记 0 小时，下周补。", style = type.micro, color = FuguangPrototypeColors.ink3)
                    }
                    }
                }
                PrototypeCat(
                    drawable = R.drawable.fuguang_cat_reach,
                    size = 54.dp,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (-2).dp, y = (-26).dp),
                )
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RelocationOptionRow(
    option: PrototypeRelocationOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val type = fuguangPrototypeType
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("relocation-${option.id}"),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) FuguangPrototypeColors.pink else FuguangPrototypeColors.paper,
        border = BorderStroke(if (selected) 2.2.dp else 1.6.dp, if (selected) FuguangPrototypeColors.hotDeep else FuguangPrototypeColors.ink.copy(alpha = 0.25f)),
    ) {
        Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(32.dp).background(if (selected) FuguangPrototypeColors.hotDeep else FuguangPrototypeColors.pink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${option.id}", style = type.label.copy(fontWeight = FontWeight.Bold), color = if (selected) FuguangPrototypeColors.white else FuguangPrototypeColors.ink2)
            }
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(option.time, style = type.title, color = FuguangPrototypeColors.ink)
                    if (option.recommended) {
                        Spacer(Modifier.width(6.dp))
                        PrototypeTinyTag("推荐", FuguangPrototypeColors.lime, FuguangPrototypeColors.sageDeep)
                    }
                }
                Text(option.place, style = type.caption, color = FuguangPrototypeColors.ink2)
                Spacer(Modifier.height(6.dp))
                Text(option.cost, style = type.caption, color = FuguangPrototypeColors.ink2)
            }
        }
    }
}

@Composable
fun WeekScreen(
    beat: PrototypeBeat,
    days: List<PrototypeDay>,
    selectedDayIndex: Int,
    reverted: Boolean,
    extraMessages: List<Pair<String, String>>,
    onSelectDay: (Int) -> Unit,
    onUndo: () -> Unit,
    onBackToRecognize: () -> Unit,
) {
    val type = fuguangPrototypeType
    val pagerState = rememberPagerState(initialPage = selectedDayIndex, pageCount = { days.size })
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedDayIndex) onSelectDay(pagerState.currentPage)
    }
    LaunchedEffect(selectedDayIndex) {
        if (pagerState.currentPage != selectedDayIndex) pagerState.animateScrollToPage(selectedDayIndex)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().prototypeGridBackground().testTag("week-screen"),
        contentPadding = PaddingValues(horizontal = PrototypeScreenHorizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PrototypeReceipt(
                if (reverted) "已撤回 · 表回到改课前" else "雅思写作落到周六 18:00",
                collapsedCount = 4,
                onClick = onBackToRecognize,
            )
        }
        item {
            Text(
                "11/4 周三 13:54",
                style = type.caption,
                color = FuguangPrototypeColors.ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("整周详细", style = type.section, color = FuguangPrototypeColors.ink)
                Spacer(Modifier.width(7.dp))
                Text("11/2 – 11/8 · 整天连空档", style = type.micro, color = FuguangPrototypeColors.ink3)
                Spacer(Modifier.weight(1f))
                PrototypeButton(if (reverted) "恢复这版" else "撤回整周", onUndo, style = PrototypeButtonStyle.GHOST)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legend("课程", FuguangPrototypeColors.course)
                Legend("任务", FuguangPrototypeColors.task)
                Legend("雅思", FuguangPrototypeColors.ielts)
                Legend("面试", FuguangPrototypeColors.prep)
                Legend("会议 / 出行", FuguangPrototypeColors.meeting)
                Legend("休息", Color.Transparent, outlined = true)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { index, day ->
                    PrototypeDayChip(
                        weekday = day.weekday,
                        date = "",
                        selected = index == selectedDayIndex,
                        marker = when {
                            reverted && index in setOf(2, 3, 5) -> PrototypeDayMarker.CHANGED
                            !reverted && index in setOf(2, 5) -> PrototypeDayMarker.CHANGED
                            !reverted && index == 3 -> PrototypeDayMarker.CONFLICT
                            else -> PrototypeDayMarker.NONE
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDay(index) },
                    )
                }
            }
        }
        item {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(start = 1.dp, end = 46.dp),
                pageSpacing = 9.dp,
                modifier = Modifier.fillMaxWidth().height(440.dp),
            ) { page ->
                WeekDayCard(
                    day = days[page],
                    changedFlag = weekFlag(days[page], reverted),
                    beat = beat,
                )
            }
        }
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = FuguangPrototypeColors.card) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (reverted) "已回到改课前，三处改动都撤了" else "虚线是空档，颜色是事情类型",
                        style = type.caption,
                        color = FuguangPrototypeColors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    PrototypeCat(R.drawable.fuguang_cat_tilthead, 50.dp)
                }
            }
        }
        extraMessages.forEach { (user, reply) ->
            item { UserBubble(user) }
            item { AssistantBubble(prototypeAnnotated(reply)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Legend(
    label: String,
    color: Color,
    outlined: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = RoundedCornerShape(2.dp),
            color = color,
            border = if (outlined) BorderStroke(1.dp, FuguangPrototypeColors.ink.copy(alpha = 0.3f)) else null,
        ) {}
        Spacer(Modifier.width(4.dp))
        Text(label, style = fuguangPrototypeType.micro, color = FuguangPrototypeColors.ink2)
    }
}

@Composable
private fun WeekDayCard(
    day: PrototypeDay,
    changedFlag: String?,
    beat: PrototypeBeat,
) {
    val type = fuguangPrototypeType
    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = FuguangPrototypeColors.card,
        border = BorderStroke(1.dp, FuguangPrototypeColors.line),
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.weekday, style = type.title, color = FuguangPrototypeColors.ink)
                    Spacer(Modifier.width(5.dp))
                    Text(day.dateLabel, style = type.micro, color = FuguangPrototypeColors.ink3)
                    if (changedFlag != null) {
                        Spacer(Modifier.weight(1f))
                        PrototypeTinyTag(changedFlag, FuguangPrototypeColors.pink, FuguangPrototypeColors.hotDeep)
                    }
                }
                Spacer(Modifier.height(7.dp))
                val rows = weekRows(day)
                rows.forEach { row ->
                    if (row.restStart != null && row.restEnd != null) {
                        WeekRestRow(row.restStart, row.restEnd)
                    } else if (row.event != null) {
                        WeekEventRow(row.event)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(FuguangPrototypeColors.line))
                Spacer(Modifier.height(7.dp))
                Text(
                    weekNote(day.weekday).let { "${it.first}　${it.second}" },
                    style = type.micro,
                    color = FuguangPrototypeColors.ink2,
                )
            }
            if (day.events.size <= 4) {
                PrototypeCat(
                    drawable = R.drawable.fuguang_cathead_sit,
                    size = 40.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                )
            }
        }
    }
}

private data class WeekRow(
    val event: PrototypeEvent? = null,
    val restStart: Int? = null,
    val restEnd: Int? = null,
)

@Composable
private fun WeekEventRow(event: PrototypeEvent) {
    val type = fuguangPrototypeType
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${formatMinute(event.startMinute)}–${formatMinute(event.endMinute)}",
            style = type.micro,
            color = FuguangPrototypeColors.ink3,
            modifier = Modifier.width(72.dp),
        )
        Box(Modifier.width(4.dp).height(27.dp).background(eventColor(event.kind), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    style = type.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (event.state == PrototypeEventState.CANCELED) FuguangPrototypeColors.ink3 else FuguangPrototypeColors.ink,
                    textDecoration = if (event.state == PrototypeEventState.CANCELED) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.state == PrototypeEventState.ADDED) {
                    Spacer(Modifier.width(4.dp))
                    PrototypeTinyTag("新增", FuguangPrototypeColors.lime, FuguangPrototypeColors.sageDeep)
                }
            }
            if (event.subtitle.isNotBlank()) {
                Text(event.subtitle, style = type.micro, color = FuguangPrototypeColors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekRestRow(startMinute: Int, endMinute: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${formatMinute(startMinute)}–${formatMinute(endMinute)}",
            style = fuguangPrototypeType.micro,
            color = FuguangPrototypeColors.ink3,
            modifier = Modifier.width(72.dp),
        )
        Box(
            Modifier.weight(1f).height(1.dp).background(FuguangPrototypeColors.ink.copy(alpha = 0.14f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "休息 · ${formatDuration(startMinute, endMinute)}",
            style = fuguangPrototypeType.micro,
            color = FuguangPrototypeColors.ink3,
        )
    }
}

private fun weekRows(day: PrototypeDay): List<WeekRow> {
    val events = day.events.filter { it.state != PrototypeEventState.CANCELED }.sortedBy { it.startMinute }
    val first = events.firstOrNull()?.startMinute ?: 8 * 60
    val last = events.maxOfOrNull { it.endMinute } ?: 22 * 60
    val from = min(8 * 60, first)
    val to = max(22 * 60, last)
    val rows = mutableListOf<WeekRow>()
    var cursor = from
    events.forEach { event ->
        if (event.startMinute - cursor >= 40) {
            rows += WeekRow(restStart = cursor, restEnd = event.startMinute)
        }
        rows += WeekRow(event = event)
        cursor = max(cursor, event.endMinute)
    }
    if (to - cursor >= 40) rows += WeekRow(restStart = cursor, restEnd = to)
    return rows
}

private fun eventColor(kind: PrototypeEventKind): Color = when (kind) {
    PrototypeEventKind.COURSE -> FuguangPrototypeColors.course
    PrototypeEventKind.IELTS -> FuguangPrototypeColors.ielts
    PrototypeEventKind.PREP -> FuguangPrototypeColors.prep
    PrototypeEventKind.TASK -> FuguangPrototypeColors.task
    PrototypeEventKind.MEETING -> FuguangPrototypeColors.meeting
    PrototypeEventKind.MOVE -> FuguangPrototypeColors.move
}

private fun dayNote(weekday: String): String = when (weekday) {
    "周一" -> "三段课都在二主楼，中间不用回寝室"
    "周二" -> "下午整段空出来了，FA2 已经改到周四"
    "周三" -> "20:30 市场营销小组讨论在寝室 · 线上，不用再跑一趟"
    "周四" -> "中午到傍晚没缝隙，12:30 例会 → 14:00 上课"
    "周五" -> "早八连到晚上十点，课后三块都在二主楼"
    "周六" -> "图书馆一整天，10:00 进馆，19:00 收工"
    else -> "全天在北京，只在回程高铁上补 40 分钟"
}

private fun weekNote(weekday: String): Pair<String, String> = when (weekday) {
    "周一" -> "在校 8:00–22:00" to "四段课，空档都在二主楼"
    "周二" -> "下午整段空出来了" to "FA2 已经改到周四"
    "周三" -> "五段 + 一次线上讨论" to "20:30 市场营销小组讨论"
    "周四" -> "中午到傍晚没缝隙" to "例会 → 二主楼 → FA2"
    "周五" -> "早八连到晚上十点" to "课后三块都在二主楼"
    "周六" -> "图书馆一整天" to "10:00 进馆，19:00 收工"
    else -> "全天在北京" to "回程高铁上补 40 分钟"
}

private fun weekFlag(day: PrototypeDay, reverted: Boolean): String? {
    return if (reverted) {
        when (day.weekday) {
            "周三", "周四", "周六" -> "改过 1 处"
            else -> null
        }
    } else {
        when (day.weekday) {
            "周三" -> "改过 1 处"
            "周四" -> "换课 · 撞 1 处"
            "周六" -> "接住雅思写作"
            "周日" -> "面试 · 不留任务"
            else -> null
        }
    }
}

private fun Dp.coerceAtLeast(minimum: Dp): Dp = if (this < minimum) minimum else this

private fun largestTimelineGap(
    day: PrototypeDay,
    startHour: Float,
    endHour: Float,
): Pair<Float, Float>? {
    val events = day.events
        .filter { it.state != PrototypeEventState.CANCELED }
        .sortedBy { it.startMinute }
    var cursor = startHour * 60f
    var bestStart = 0f
    var bestEnd = 0f
    fun consider(start: Float, end: Float) {
        if (end - start > bestEnd - bestStart) {
            bestStart = start
            bestEnd = end
        }
    }
    events.forEach { event ->
        if (event.startMinute > cursor) consider(cursor, event.startMinute.toFloat())
        cursor = maxOf(cursor, event.endMinute.toFloat())
    }
    val endMinute = endHour * 60f
    if (cursor < endMinute) consider(cursor, endMinute)
    return if (bestEnd > bestStart) (bestStart / 60f) to (bestEnd / 60f) else null
}
