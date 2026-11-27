package com.attention.app.fuguang.prototype

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FuguangWeekPrototypeApp() {
    val initialWeek = remember { prototypeInitialWeek() }
    val finalWeek = remember(initialWeek) { prototypeFinalWeek(initialWeek) }
    var beat by remember { mutableStateOf(PrototypeBeat.RECOGNIZE) }
    var recap by remember {
        mutableStateOf(
            listOf(
                PrototypeRecapItem(
                    id = "sunday",
                    title = "周日一整天不排事",
                    subtitle = "你说的「一整天都不在」—— 从早到晚在北京，一件也塞不进",
                    timeLabel = "周日 · 全天",
                ),
                PrototypeRecapItem(
                    id = "move",
                    title = "这两件挪到周中",
                    subtitle = "「概率论第三单元习题」和「管理会计复习」，都别留在周日",
                    timeLabel = "2 件事",
                ),
                PrototypeRecapItem(
                    id = "constraint",
                    title = "但要等这门课下课",
                    subtitle = "你说的「一定要在这门课上完之后」—— 不能挪进周五上午的课里",
                    timeLabel = "周五 12:45 后",
                ),
            ),
        )
    }
    var recapReady by remember { mutableStateOf(false) }
    var editingRecapId by remember { mutableStateOf<String?>(null) }
    var editingRecapText by remember { mutableStateOf("") }
    var adjustments by remember {
        mutableStateOf(
            listOf(
                PrototypeAdjustmentItem(
                    id = "prob",
                    title = "概率论第三单元习题",
                    subtitle = "",
                    flow = listOf("周五 14:00–15:30", "周六 10:00–11:30"),
                    alternatives = listOf("周五 14:00", "周六 10:00", "周六 14:00"),
                ),
                PrototypeAdjustmentItem(
                    id = "notes",
                    title = "记 MA 笔记",
                    subtitle = "周一那节早下课了",
                    flow = listOf("周三 20:30–22:00"),
                    alternatives = listOf("周三 20:30", "周四 21:00", "周六 16:00"),
                ),
                PrototypeAdjustmentItem(
                    id = "group",
                    title = "群面案例练习",
                    subtitle = "每天 40min，周三起 6 天各一块",
                    alternatives = listOf("每天 40 min", "隔天 80 min", "只排工作日"),
                ),
                PrototypeAdjustmentItem(
                    id = "sunday",
                    title = "周日 7 处日程",
                    subtitle = "全部移出，当天只留面试与往返",
                    alternatives = listOf("全部移出", "只留晚上 1h"),
                ),
            ),
        )
    }
    var adopted by remember { mutableStateOf(List(4) { true }) }
    var selfEdit by remember { mutableStateOf(false) }
    var customized by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf(3) }
    var mealKept by remember { mutableStateOf(setOf<String>()) }
    var later by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Char?>(null) }
    var skipWeek by remember { mutableStateOf(false) }
    var reverted by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var extraMessages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val week = if (reverted) initialWeek else finalWeek

    fun showToast(message: String) {
        toast = message
    }

    fun go(next: PrototypeBeat) {
        beat = next
        extraMessages = emptyList()
    }

    LaunchedEffect(toast) {
        val current = toast ?: return@LaunchedEffect
        delay(1800)
        if (toast == current) toast = null
    }

    Box(modifier = Modifier.fillMaxSize().background(FuguangPrototypeColors.paper2)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = FuguangPrototypeColors.paper2,
        ) {
            PrototypePhoneShell(
                input = input,
                onInputChange = { input = it },
                onSend = {
                    val message = input.trim()
                    if (message.isNotEmpty()) {
                        extraMessages = extraMessages + (message to mockReply(message))
                        input = ""
                        showToast("记下了")
                    }
                },
                onNavigateAgenda = {
                    selectedDay = 3
                    go(if (beat.index <= 2) PrototypeBeat.DAY else PrototypeBeat.WEEK)
                },
                onTabMessage = ::showToast,
            ) {
                when (beat) {
                    PrototypeBeat.RECOGNIZE -> RecognizeScreen(
                        beat = beat,
                        recap = recap,
                        recapReady = recapReady,
                        editingRecapId = editingRecapId,
                        editingRecapText = editingRecapText,
                        onEditingRecapTextChange = { editingRecapText = it },
                        extraMessages = extraMessages,
                        onToggleRecap = { id ->
                            recap = recap.map {
                                if (it.id == id) it.copy(confirmed = !it.confirmed) else it
                            }
                            val left = recap.count { !it.confirmed }
                            showToast(if (left == 0) "三条都对上了，往下算吧" else "还剩 $left 条没确认")
                        },
                        onStartEditRecap = { id ->
                            editingRecapId = id
                            editingRecapText = recap.first { it.id == id }.let { it.editedText ?: it.title }
                        },
                        onSaveRecapEdit = {
                            val id = editingRecapId
                            val value = editingRecapText.trim()
                            if (id != null && value.isNotEmpty()) {
                                recap = recap.map {
                                    if (it.id == id) it.copy(editedText = value, confirmed = true) else it
                                }
                                editingRecapId = null
                                editingRecapText = ""
                                showToast("改好了 · 只动这一条要求，其余照旧")
                            } else {
                                showToast("这条要求想改成什么？写一句再存")
                            }
                        },
                        onCancelRecapEdit = {
                            editingRecapId = null
                            editingRecapText = ""
                            showToast("好，这条按原来的")
                        },
                        onConfirmRecap = {
                            if (recap.all { it.confirmed }) {
                                recapReady = true
                                showToast("行，就按这几件算代价")
                            } else {
                                showToast("还有没确认的条目")
                            }
                        },
                        onResetRecap = {
                            recapReady = false
                            recap = recap.map { it.copy(confirmed = false) }
                            editingRecapId = null
                        },
                        onContinue = { go(PrototypeBeat.ADJUST) },
                    )
                    PrototypeBeat.ADJUST -> AdjustScreen(
                        beat = beat,
                        items = adjustments,
                        adopted = adopted,
                        selfEdit = selfEdit,
                        extraMessages = extraMessages,
                        onToggleAdopt = { id ->
                            val index = adjustments.indexOfFirst { it.id == id }
                            if (index >= 0) {
                                adopted = adopted.toMutableList().also { it[index] = !it[index] }
                            }
                        },
                        onToggleSelfEdit = {
                            selfEdit = true
                            showToast("逐条换时间，选完点「存下这一版」")
                        },
                        onSaveSelfEdit = {
                            selfEdit = false
                            customized = true
                            val count = adjustments.count { it.selectedAlternative != null }
                            showToast(if (count > 0) "已存下 $count 处自定义" else "已存下，按默认那版")
                            scope.launch {
                                delay(520)
                                go(PrototypeBeat.DAY)
                            }
                        },
                        onChooseAlternative = { itemId, alternative ->
                            adjustments = adjustments.map {
                                if (it.id == itemId) it.copy(selectedAlternative = alternative) else it
                            }
                        },
                        onContinue = { go(PrototypeBeat.DAY) },
                        onBackToRecognize = { go(PrototypeBeat.RECOGNIZE) },
                    )
                    PrototypeBeat.DAY -> DayScreen(
                        beat = beat,
                        days = initialWeek,
                        selectedDayIndex = selectedDay,
                        mealKept = initialWeek[selectedDay].weekday in mealKept,
                        customized = customized,
                        extraMessages = extraMessages,
                        onSelectDay = { selectedDay = it },
                        onBackToAdjust = { go(PrototypeBeat.ADJUST) },
                        onBackToRecognize = { go(PrototypeBeat.RECOGNIZE) },
                        onContinue = { go(PrototypeBeat.FEISHU) },
                        onMealKeep = { keep ->
                            val weekday = initialWeek[selectedDay].weekday
                            mealKept = if (keep) mealKept + weekday else mealKept - weekday
                            showToast(if (keep) "留好了 · 不写进周计划" else "行，那我不动，你自己挤")
                        },
                    )
                    PrototypeBeat.FEISHU -> FeishuScreen(
                        beat = beat,
                        later = later,
                        extraMessages = extraMessages,
                        onLater = {
                            later = !later
                            showToast(if (later) "先搁着，表不动" else "好，那我帮你找位置")
                        },
                        onFindPlace = { go(PrototypeBeat.RELOCATE) },
                        onBackToRecognize = { go(PrototypeBeat.RECOGNIZE) },
                    )
                    PrototypeBeat.RELOCATE -> RelocateScreen(
                        beat = beat,
                        options = prototypeRelocationOptions,
                        picked = picked,
                        skipWeek = skipWeek,
                        extraMessages = extraMessages,
                        onPick = { picked = it },
                        onSkipWeek = {
                            skipWeek = !skipWeek
                            if (skipWeek) picked = null
                            showToast(if (skipWeek) "这周记 0 小时，下周补" else "改回来了，挑个落点就能排")
                        },
                        onApply = {
                            val option = prototypeRelocationOptions.firstOrNull { it.id == picked }
                            if (option != null) {
                                showToast("已放到${option.time}，写进表了")
                                scope.launch {
                                    delay(420)
                                    go(PrototypeBeat.WEEK)
                                }
                            }
                        },
                        onBackToRecognize = { go(PrototypeBeat.RECOGNIZE) },
                    )
                    PrototypeBeat.WEEK -> WeekScreen(
                        beat = beat,
                        days = week,
                        selectedDayIndex = selectedDay,
                        reverted = reverted,
                        extraMessages = extraMessages,
                        onSelectDay = { selectedDay = it },
                        onUndo = {
                            reverted = !reverted
                            showToast(if (reverted) "撤回了，表回到改课前" else "恢复了，改课那一版又回来了")
                        },
                        onBackToRecognize = { go(PrototypeBeat.RECOGNIZE) },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
        ) {
            toast?.let { PrototypeToast(it) }
        }
    }
}

private val prototypeRelocationOptions = listOf(
    PrototypeRelocationOption(
        id = 'A',
        time = "周六 18:00–19:00",
        place = "图书馆 · 接在整块自习后面",
        cost = "代价：周六从 17:50 延到 19:00 收工。",
        recommended = true,
    ),
    PrototypeRelocationOption(
        id = 'B',
        time = "周五 21:10–22:10",
        place = "寝室 · 线上",
        cost = "代价：周五从早八点连到晚上十点十分。",
    ),
    PrototypeRelocationOption(
        id = 'C',
        time = "下周一 10:00–11:00",
        place = "商学院自习区",
        cost = "代价：跨周，本周雅思写作记 0 小时。",
    ),
)

private fun mockReply(message: String): String = when {
    Regex("[改挪换]").containsMatchIn(message) ->
        "这条我记下来了。表还没动——你说「就按这个改」，我才写。"
    Regex("[不别停]").containsMatchIn(message) ->
        "好，那我先不动。需要的时候点上面那版安排就行。"
    else -> "收到。我还是只读——要改表，得你点头。"
}
