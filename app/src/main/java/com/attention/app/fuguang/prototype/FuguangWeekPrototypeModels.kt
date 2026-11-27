package com.attention.app.fuguang.prototype

import androidx.annotation.DrawableRes
import com.attention.app.R
import kotlin.math.abs

enum class PrototypeBeat(val index: Int, val railTitle: String, val railSubtitle: String, val clock: String) {
    RECOGNIZE(0, "说出这件事", "21:47 · 一条人话", "21:47"),
    ADJUST(1, "助手把代价摆出来", "21:49 · 四条可摘", "21:49"),
    DAY(2, "改完的这周", "21:50 · 日视图", "21:50"),
    FEISHU(3, "飞书又来了一条", "次日 13:52 · 撞课", "13:52"),
    RELOCATE(4, "三个落点", "13:53 · 挑一个", "13:53"),
    WEEK(5, "整周详细", "13:54 · 可左右滑", "13:54"),
}

enum class PrototypeEventKind {
    COURSE,
    IELTS,
    PREP,
    TASK,
    MEETING,
    MOVE,
}

enum class PrototypeEventState {
    NORMAL,
    ADDED,
    CANCELED,
}

data class PrototypeEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val startMinute: Int,
    val endMinute: Int,
    val kind: PrototypeEventKind,
    val state: PrototypeEventState = PrototypeEventState.NORMAL,
)

data class PrototypeDay(
    val id: String,
    val weekday: String,
    val dateLabel: String,
    val events: List<PrototypeEvent>,
)

data class PrototypeRecapItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val editedText: String? = null,
    val confirmed: Boolean = false,
)

data class PrototypeAdjustmentItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val flow: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val selectedAlternative: String? = null,
)

data class PrototypeRelocationOption(
    val id: Char,
    val time: String,
    val place: String,
    val cost: String,
    val recommended: Boolean = false,
)

data class PrototypeMealShortage(
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val needMinutes: Int,
)

@DrawableRes
fun PrototypeBeat.catBody(): Int = when (this) {
    PrototypeBeat.RECOGNIZE -> R.drawable.fuguang_cat_wave2
    PrototypeBeat.ADJUST -> R.drawable.fuguang_cat_hug
    PrototypeBeat.DAY -> R.drawable.fuguang_cat_doze
    PrototypeBeat.FEISHU -> R.drawable.fuguang_cat_shy
    PrototypeBeat.RELOCATE -> R.drawable.fuguang_cat_reach
    PrototypeBeat.WEEK -> R.drawable.fuguang_cat_wave
}

@DrawableRes
fun PrototypeBeat.catHead(): Int = when (this) {
    PrototypeBeat.RECOGNIZE -> R.drawable.fuguang_cathead_wave2
    PrototypeBeat.ADJUST -> R.drawable.fuguang_cathead_hug
    PrototypeBeat.DAY -> R.drawable.fuguang_cathead_doze
    PrototypeBeat.FEISHU -> R.drawable.fuguang_cathead_shy
    PrototypeBeat.RELOCATE -> R.drawable.fuguang_cathead_reach
    PrototypeBeat.WEEK -> R.drawable.fuguang_cathead_wave
}

fun prototypeInitialWeek(): List<PrototypeDay> = listOf(
    prototypeDay(
        "mon", "周一", "11/2",
        event("mon-ethics", "企业伦理", "二主楼 B405", 8, 9 * 60 + 40, PrototypeEventKind.COURSE),
        event("mon-exchange", "留学与国际学术交流", "在线", 10 * 60, 11 * 60 + 40, PrototypeEventKind.COURSE),
        event("mon-ma", "管理会计 MA", "二主楼 B301", 14 * 60, 15 * 60 + 40, PrototypeEventKind.COURSE),
        event("mon-france", "中国革命在法国", "主楼 211", 20 * 60 + 20, 22 * 60, PrototypeEventKind.COURSE),
    ),
    prototypeDay(
        "tue", "周二", "11/3",
        event("tue-macro", "宏观经济学", "二主楼 B301", 10 * 60, 11 * 60 + 40, PrototypeEventKind.COURSE),
        event("tue-fa2", "财务会计 FA2", "二主楼 B301", 14 * 60, 16 * 60 + 40, PrototypeEventKind.COURSE),
        event("tue-marketing", "市场营销", "二主楼 A514", 18 * 60 + 30, 20 * 60 + 10, PrototypeEventKind.COURSE),
    ),
    prototypeDay(
        "wed", "周三", "11/4",
        event("wed-history", "中国近现代史纲要", "二主楼 A403", 14 * 60, 15 * 60 + 40, PrototypeEventKind.COURSE),
        event("wed-listening", "雅思听力 50min", "二主楼自习区", 15 * 60 + 50, 16 * 60 + 40, PrototypeEventKind.IELTS),
        event("wed-group", "群面练习 ①", "二主楼空教室", 16 * 60 + 50, 17 * 60 + 30, PrototypeEventKind.PREP),
        event("wed-big-data", "大数据与会计分析", "二主楼 B301", 18 * 60 + 30, 20 * 60 + 10, PrototypeEventKind.COURSE),
        event("wed-discussion", "市场营销小组讨论", "寝室 · 线上", 20 * 60 + 30, 21 * 60 + 30, PrototypeEventKind.TASK),
    ),
    prototypeDay(
        "thu", "周四", "11/5",
        event("thu-group", "群面练习 ②", "寝室", 9 * 60 + 10, 9 * 60 + 50, PrototypeEventKind.PREP),
        event("thu-reading", "雅思阅读 + 词汇", "商学院自习区", 10 * 60, 11 * 60 + 30, PrototypeEventKind.IELTS),
        event("thu-meeting", "第一党支部例会", "商学院 A502", 12 * 60 + 30, 13 * 60 + 30, PrototypeEventKind.MEETING),
        event("thu-writing", "雅思写作", "二主楼自习区", 14 * 60, 15 * 60, PrototypeEventKind.IELTS),
        event("thu-notes", "记 FA2 笔记", "二主楼自习区", 17 * 60, 18 * 60 + 30, PrototypeEventKind.TASK),
        event("thu-homework", "FA2 作业 ①", "二主楼自习区", 19 * 60 + 30, 20 * 60 + 30, PrototypeEventKind.TASK),
    ),
    prototypeDay(
        "fri", "周五", "11/6",
        event("fri-prob", "概率论与数理统计", "二主楼 A311", 8 * 60, 12 * 60 + 45, PrototypeEventKind.COURSE),
        event("fri-prob-task", "概率论第三单元习题 ①", "二主楼自习区", 14 * 60, 15 * 60 + 30, PrototypeEventKind.TASK),
        event("fri-ob", "组织行为学", "二主楼 A104", 16 * 60, 17 * 60 + 40, PrototypeEventKind.COURSE),
        event("fri-data", "大数据与会计分析", "二主楼 B301", 18 * 60 + 30, 20 * 60 + 10, PrototypeEventKind.COURSE),
        event("fri-group", "群面练习 ③", "二主楼空教室", 20 * 60 + 20, 21 * 60, PrototypeEventKind.PREP),
        event("fri-speaking", "雅思口语", "寝室 · 线上", 21 * 60 + 10, 21 * 60 + 50, PrototypeEventKind.IELTS),
    ),
    prototypeDay(
        "sat", "周六", "11/7",
        event("sat-group", "群面练习 ④", "寝室", 9 * 60 + 10, 9 * 60 + 50, PrototypeEventKind.PREP),
        event("sat-prob", "概率论第三单元习题 ②", "图书馆", 10 * 60, 11 * 60 + 30, PrototypeEventKind.TASK),
        event("sat-macro", "宏观经济学刷题 ①", "图书馆", 12 * 60 + 50, 14 * 60 + 20, PrototypeEventKind.TASK),
        event("sat-notes", "记 MA 笔记", "图书馆", 14 * 60 + 30, 16 * 60, PrototypeEventKind.TASK),
        event("sat-homework", "FA2 作业 ②", "图书馆", 16 * 60 + 10, 17 * 60 + 10, PrototypeEventKind.TASK),
        event("sat-macro-2", "宏观经济学刷题 ②", "图书馆", 17 * 60 + 20, 17 * 60 + 50, PrototypeEventKind.TASK),
    ),
    prototypeDay(
        "sun", "周日", "11/8",
        event("sun-train", "赴北京", "高铁", 6 * 60 + 30, 9 * 60, PrototypeEventKind.MOVE),
        event("sun-interview", "面试 · 群面全英案例", "北京 China", 9 * 60, 12 * 60, PrototypeEventKind.PREP),
        event("sun-return", "返程", "高铁上补 40min 练习", 12 * 60, 16 * 60, PrototypeEventKind.MOVE),
    ),
)

fun prototypeFinalWeek(initial: List<PrototypeDay>): List<PrototypeDay> {
    return initial.map { day ->
        when (day.id) {
            "tue" -> day.copy(
                events = day.events.map {
                    if (it.id == "tue-fa2") {
                        it.copy(subtitle = "改到周四了", state = PrototypeEventState.CANCELED)
                    } else {
                        it
                    }
                },
            )
            "thu" -> day.copy(
                events = day.events.filterNot { it.id == "thu-writing" }.plus(
                    event(
                        "thu-fa2-added",
                        "财务会计 FA2",
                        "二主楼 B301",
                        14 * 60,
                        16 * 60 + 40,
                        PrototypeEventKind.COURSE,
                        PrototypeEventState.ADDED,
                    ),
                ).sortedBy { it.startMinute },
            )
            "sat" -> day.copy(
                events = day.events.plus(
                    event(
                        "sat-writing-added",
                        "雅思写作",
                        "图书馆 · 从周四挪来",
                        18 * 60,
                        19 * 60,
                        PrototypeEventKind.IELTS,
                        PrototypeEventState.ADDED,
                    ),
                ).sortedBy { it.startMinute },
            )
            else -> day
        }
    }
}

fun prototypeMealShortages(day: PrototypeDay): List<PrototypeMealShortage> {
    val meals = listOf(
        Triple("午饭", 11 * 60 + 30, 13 * 60 + 30),
        Triple("晚饭", 17 * 60, 19 * 60 + 30),
        Triple("午觉", 12 * 60, 14 * 60 + 30),
    )
    val required = listOf(30, 30, 45)
    return meals.mapIndexedNotNull { index, (label, start, end) ->
        val need = required[index]
        val anySlot = (start..(end - need) step 30).any { candidate ->
            day.events.filter { it.state != PrototypeEventState.CANCELED }.none { event ->
                event.startMinute < candidate + need && event.endMinute > candidate
            }
        }
        if (anySlot) null else PrototypeMealShortage(label, start, end, need)
    }
}

fun prototypeMealShort(day: PrototypeDay): List<String> = prototypeMealShortages(day).map { it.label }

fun prototypeMealWhy(day: PrototypeDay, shortages: List<PrototypeMealShortage>): String {
    if (shortages.isEmpty()) return ""
    val labels = shortages.joinToString("、") { it.label }
    val firstShortage = shortages.first()
    val inside = day.events
        .filter { it.state != PrototypeEventState.CANCELED }
        .filter { it.endMinute > firstShortage.startMinute && it.startMinute < firstShortage.endMinute }
        .sortedBy { it.startMinute }
    if (inside.isEmpty()) return "${labels}塞不进去。"
    if (inside.size == 1) {
        val event = inside.first()
        return "${formatMinute(maxOf(event.startMinute, firstShortage.startMinute))}–${formatMinute(event.endMinute)} " +
            "整段在「${event.title}」上 —— ${labels}塞不进去。"
    }
    val first = inside.first()
    val last = inside.last()
    return if (last.startMinute - first.endMinute < 10) {
        "${first.title} ${formatMinute(first.endMinute)} 一收，${last.title}就接上 —— " +
            "一分钟没留，${labels}塞不进去。"
    } else {
        "${first.title} 占到 ${formatMinute(first.endMinute)}，${last.title} " +
            "${formatMinute(last.startMinute)} 又接上 —— ${labels}塞不进去。"
    }
}

fun prototypeMealNeedDuration(shortages: List<PrototypeMealShortage>): String =
    formatDuration(0, shortages.sumOf { it.needMinutes })

fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

fun formatDuration(startMinute: Int, endMinute: Int): String {
    val minutes = endMinute - startMinute
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest 分钟"
        rest == 0 -> "$hours 小时"
        else -> "$hours 小时 $rest 分"
    }
}

fun PrototypeEvent.isTall(): Boolean = endMinute - startMinute >= 52

private fun prototypeDay(
    id: String,
    weekday: String,
    date: String,
    vararg events: PrototypeEvent,
) = PrototypeDay(id, weekday, date, events.toList())

private fun event(
    id: String,
    title: String,
    subtitle: String,
    startMinute: Int,
    endMinute: Int,
    kind: PrototypeEventKind,
    state: PrototypeEventState = PrototypeEventState.NORMAL,
) = PrototypeEvent(id, title, subtitle, startMinute, endMinute, kind, state)
