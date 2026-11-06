package com.attention.app.fuguang.week.preview

import com.attention.app.fuguang.week.components.FuguangAdjustmentItemUi
import com.attention.app.fuguang.week.components.FuguangAdjustmentProposalUi
import com.attention.app.fuguang.week.components.FuguangAgendaEntryUi
import com.attention.app.fuguang.week.components.FuguangAgendaTone
import com.attention.app.fuguang.week.components.FuguangChatMessageUi
import com.attention.app.fuguang.week.components.FuguangConflictPreviewUi
import com.attention.app.fuguang.week.components.FuguangDayItem
import com.attention.app.fuguang.week.components.FuguangDayMarker
import com.attention.app.fuguang.week.components.FuguangDayPlanUi
import com.attention.app.fuguang.week.components.FuguangExternalCardUi
import com.attention.app.fuguang.week.components.FuguangMessageAuthor
import com.attention.app.fuguang.week.components.FuguangMiniSegmentUi
import com.attention.app.fuguang.week.components.FuguangMiniTimelineRowUi
import com.attention.app.fuguang.week.components.FuguangRelocationOptionUi
import com.attention.app.fuguang.week.components.FuguangWeekAccent
import com.attention.app.fuguang.week.components.FuguangWeekLegendItemUi
import com.attention.app.fuguang.week.components.FuguangWeekNarrativeUi

internal object FuguangWeekSampleData {
    val userMessage = FuguangChatMessageUi(
        id = "user-interview",
        author = FuguangMessageAuthor.USER,
        text = "周日临时有个去北京的面试，一整天都不在。概率论第三单元的习题和管理会计的复习得挪到周中，但一定要在这门课上完之后。",
    )

    val agentRecognition = FuguangChatMessageUi(
        id = "agent-recognition",
        author = FuguangMessageAuthor.AGENT,
        text = "收到了。要挪的是三件事：概率论作业 3 小时、MA 笔记 1.5 小时，还有你每天的群面练习 40 分钟。",
        emphasizedPhrases = listOf("概率论作业 3 小时", "MA 笔记 1.5 小时", "群面练习 40 分钟"),
    )

    val agentConstraint = FuguangChatMessageUi(
        id = "agent-constraint",
        author = FuguangMessageAuthor.AGENT,
        text = "有个硬约束先跟你说：概率论的课在周五上午，所以那 3 小时只能在周五下午和周六，周中塞不进去。周中剩的只有周三晚和周四。",
        emphasizedPhrases = listOf("周五上午", "只在周五下午和周六", "周三晚和周四"),
    )

    val adjustmentProposal = FuguangAdjustmentProposalUi(
        impactLabel = "影响 4 天",
        items = listOf(
            FuguangAdjustmentItemUi(
                id = "probability",
                title = "概率论第三单元习题",
                detail = "3h 拆成周五 14:00 +1.5h、周六 10:00 +1.5h（课后）",
            ),
            FuguangAdjustmentItemUi(
                id = "ma-notes",
                title = "记 MA 笔记",
                detail = "1.5h 落到周三 20:30，周一那节课早就上完了",
            ),
            FuguangAdjustmentItemUi(
                id = "group-interview",
                title = "群面案例练习",
                detail = "每天 40min，周三起 6 天各一块",
            ),
            FuguangAdjustmentItemUi(
                id = "sunday",
                title = "周日 7 处日程",
                detail = "全部移出，当天不留任务，只留面试与往返",
            ),
        ),
    )

    val days = listOf(
        FuguangDayItem("mon", "一", "2", marker = FuguangDayMarker.NONE),
        FuguangDayItem("tue", "二", "3", marker = FuguangDayMarker.NONE),
        FuguangDayItem("wed", "三", "4", marker = FuguangDayMarker.DOT),
        FuguangDayItem("thu", "四", "5", selected = true, marker = FuguangDayMarker.RING),
        FuguangDayItem("fri", "五", "6", marker = FuguangDayMarker.NONE),
        FuguangDayItem("sat", "六", "7", marker = FuguangDayMarker.DOT),
        FuguangDayItem("sun", "日", "8", marker = FuguangDayMarker.CONFLICT),
    )

    val thursday = FuguangDayPlanUi(
        id = "thu",
        weekday = "周四",
        date = "11/5",
        entries = listOf(
            FuguangAgendaEntryUi("group-2", "群面练习 ②", "寝室", 9 * 60 + 10, 9 * 60 + 50, FuguangAgendaTone.STUDY),
            FuguangAgendaEntryUi("ielts-reading", "雅思阅读 + 词汇", "商学院自习区", 10 * 60, 11 * 60 + 30, FuguangAgendaTone.STUDY),
            FuguangAgendaEntryUi("lunch", "午餐", "", 11 * 60 + 30, 12 * 60 + 20, FuguangAgendaTone.BUFFER),
            FuguangAgendaEntryUi("meeting", "第一党支部例会", "商学院 A502", 12 * 60 + 30, 13 * 60 + 30, FuguangAgendaTone.FIXED),
            FuguangAgendaEntryUi("fa2-class", "财务会计 FA2", "二主楼 B301", 14 * 60, 16 * 60 + 40, FuguangAgendaTone.CLASS, statusLabel = "新增"),
            FuguangAgendaEntryUi("fa2-notes", "记 FA2 笔记", "二主楼自习区", 17 * 60, 18 * 60 + 30, FuguangAgendaTone.STUDY),
            FuguangAgendaEntryUi("fa2-homework", "FA2 作业 ①", "二主楼自习区", 19 * 60 + 30, 20 * 60 + 30, FuguangAgendaTone.STUDY),
        ),
    )

    val feishuCard = FuguangExternalCardUi(
        sender = "刘老师",
        group = "管理会计 3 班",
        quote = "「周二下午的财务会计改到周四下午两点到四点四十，二主楼 B301。周二那节不上了。」",
        emphasizedPhrases = listOf("周四下午两点到四点四十", "二主楼 B301"),
        tags = listOf("1 移出", "1 新增", "撞了 1 处"),
    )

    val conflictPreview = FuguangConflictPreviewUi(
        title = "周四 11/5 · 12:30–16:40 无缝隙",
        rows = listOf(
            FuguangMiniTimelineRowUi(
                label = "现在",
                segments = listOf(
                    FuguangMiniSegmentUi("例会", 1.0f, FuguangWeekAccent.NEUTRAL),
                    FuguangMiniSegmentUi("财务会计 FA2", 2.1f, FuguangWeekAccent.GREEN, outlined = true),
                ),
            ),
            FuguangMiniTimelineRowUi(
                label = "原来",
                segments = listOf(
                    FuguangMiniSegmentUi("例会", 1.0f, FuguangWeekAccent.NEUTRAL),
                    FuguangMiniSegmentUi("雅思写作", 1.0f, FuguangWeekAccent.GOLD, outlined = true),
                ),
            ),
        ),
        startLabel = "12:30",
        middleLabel = "14:00",
        endLabel = "16:40",
    )

    val relocationOptions = listOf(
        FuguangRelocationOptionUi(
            id = "sat-evening",
            letter = "A",
            time = "周六 18:00–19:00",
            place = "图书馆 · 接在整块自习后面",
            cost = "周六从 17:50 延到 19:00 收工，整块自习不被打断。",
            recommended = true,
        ),
        FuguangRelocationOptionUi(
            id = "fri-late",
            letter = "B",
            time = "周五 21:10–22:10",
            place = "寝室 · 线上",
            cost = "周五从早八点连到晚上十点十分，当天已有三门课。",
        ),
        FuguangRelocationOptionUi(
            id = "next-mon",
            letter = "C",
            time = "下周一 10:00–11:00",
            place = "商学院自习区",
            cost = "跨周，本周雅思写作记 0 小时，下周补。",
        ),
    )

    val weekPlans = listOf(
        plan("mon", "周一", "11/2", false,
            entry("ethics", "企业伦理", "二主楼 B405", 8 * 60, 9 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("exchange", "留学与国际学术交流", "在线", 10 * 60, 11 * 60 + 40, FuguangAgendaTone.STUDY),
            entry("ma-class", "管理会计 MA", "二主楼 B301", 14 * 60, 15 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("france", "中国革命在法国", "主楼 211", 20 * 60 + 20, 22 * 60, FuguangAgendaTone.CLASS),
        ),
        plan("tue", "周二", "11/3", false,
            entry("macro", "宏观经济学", "二主楼 B301", 10 * 60, 11 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("fa2", "财务会计 FA2", "改到周四", 14 * 60, 16 * 60 + 40, FuguangAgendaTone.FIXED),
            entry("marketing", "市场营销", "二主楼 A514", 18 * 60 + 30, 20 * 60 + 10, FuguangAgendaTone.CLASS),
        ),
        plan("wed", "周三", "11/4", true,
            entry("nap", "午觉", "寝室", 13 * 60, 13 * 60 + 45, FuguangAgendaTone.PRIVATE),
            entry("history", "中国近现代史纲要", "二主楼 A403", 14 * 60, 15 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("listening", "雅思听力 50min", "二主楼自习区", 15 * 60 + 50, 16 * 60 + 40, FuguangAgendaTone.STUDY),
            entry("group-1", "群面练习 ①", "二主楼空教室", 16 * 60 + 50, 17 * 60 + 30, FuguangAgendaTone.STUDY),
            entry("big-data", "大数据与会计分析", "二主楼 B301", 18 * 60 + 30, 20 * 60 + 10, FuguangAgendaTone.CLASS),
            entry("group-discussion", "市场营销小组讨论", "寝室 · 线上", 20 * 60 + 30, 21 * 60 + 30, FuguangAgendaTone.STUDY),
        ),
        plan("thu", "周四", "11/5", true,
            entry("group-2", "群面练习 ②", "寝室", 9 * 60 + 10, 9 * 60 + 50, FuguangAgendaTone.STUDY),
            entry("reading", "雅思阅读 + 词汇", "商学院自习区", 10 * 60, 11 * 60 + 30, FuguangAgendaTone.STUDY),
            entry("lunch", "午餐", "—", 11 * 60 + 30, 12 * 60 + 20, FuguangAgendaTone.BUFFER),
            entry("party", "第一党支部例会", "商学院 A502", 12 * 60 + 30, 13 * 60 + 30, FuguangAgendaTone.FIXED),
            entry("fa2-new", "财务会计 FA2", "二主楼 B301", 14 * 60, 16 * 60 + 40, FuguangAgendaTone.CLASS, "新增"),
            entry("fa2-notes", "记 FA2 笔记", "二主楼自习区", 17 * 60, 18 * 60 + 30, FuguangAgendaTone.STUDY),
            entry("fa2-hw", "FA2 作业 ①", "二主楼自习区", 19 * 60 + 30, 20 * 60 + 30, FuguangAgendaTone.STUDY),
        ),
        plan("fri", "周五", "11/6", true,
            entry("prob-class", "概率论与数理统计", "二主楼 A311", 8 * 60, 9 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("nap-2", "午觉", "寝室", 13 * 60, 13 * 60 + 45, FuguangAgendaTone.PRIVATE),
            entry("prob-1", "概率论第三单元习题 ①", "二主楼自习区", 14 * 60, 15 * 60 + 30, FuguangAgendaTone.STUDY),
            entry("ob", "组织行为学", "二主楼 A104", 16 * 60, 17 * 60 + 40, FuguangAgendaTone.CLASS),
            entry("big-data-2", "大数据与会计分析", "二主楼 B301", 18 * 60 + 30, 20 * 60 + 10, FuguangAgendaTone.CLASS),
            entry("group-3", "群面练习 ③", "二主楼空教室", 20 * 60 + 20, 21 * 60, FuguangAgendaTone.STUDY),
            entry("speaking", "雅思口语", "寝室 · 线上", 21 * 60 + 10, 21 * 60 + 50, FuguangAgendaTone.STUDY),
        ),
        plan("sat", "周六", "11/7", true,
            entry("group-4", "群面练习 ④", "寝室", 9 * 60 + 10, 9 * 60 + 50, FuguangAgendaTone.STUDY),
            entry("prob-2", "概率论第三单元习题 ②", "图书馆", 10 * 60, 11 * 60 + 30, FuguangAgendaTone.STUDY),
            entry("lunch-2", "午餐", "—", 11 * 60 + 40, 12 * 60 + 40, FuguangAgendaTone.BUFFER),
            entry("macro-1", "宏观经济学刷题 ①", "图书馆", 12 * 60 + 50, 14 * 60 + 20, FuguangAgendaTone.STUDY),
            entry("ma-notes", "记 MA 笔记", "图书馆", 14 * 60 + 30, 16 * 60, FuguangAgendaTone.STUDY),
            entry("fa2-hw-2", "FA2 作业 ②", "图书馆", 16 * 60 + 10, 17 * 60 + 10, FuguangAgendaTone.STUDY),
            entry("macro-2", "宏观经济学刷题 ②", "图书馆", 17 * 60 + 20, 17 * 60 + 50, FuguangAgendaTone.STUDY),
            entry("writing", "雅思写作", "图书馆 · 从周四挪来", 18 * 60, 19 * 60, FuguangAgendaTone.STUDY, "新增"),
        ),
        plan("sun", "周日", "11/8", false,
            entry("train", "赴北京", "高铁", 6 * 60 + 30, 9 * 60, FuguangAgendaTone.COMMUTE),
            entry("interview", "面试 · 群面全英案例", "北京", 9 * 60, 12 * 60, FuguangAgendaTone.FIXED),
            entry("return", "返程", "高铁上补练习", 12 * 60, 16 * 60, FuguangAgendaTone.COMMUTE),
            entry("rest", "回校休息", "当天不留任务", 16 * 60 + 30, 22 * 60, FuguangAgendaTone.PRIVATE),
        ),
    )

    val legend = listOf(
        FuguangWeekLegendItemUi("课", FuguangWeekAccent.NEUTRAL),
        FuguangWeekLegendItemUi("学习任务", FuguangWeekAccent.GREEN),
        FuguangWeekLegendItemUi("雅思", FuguangWeekAccent.GOLD),
        FuguangWeekLegendItemUi("面试准备", FuguangWeekAccent.GOLD),
        FuguangWeekLegendItemUi("例会", FuguangWeekAccent.NEUTRAL),
        FuguangWeekLegendItemUi("通勤 / 午觉 / 用餐", FuguangWeekAccent.BLUE),
    )

    val narratives = listOf(
        FuguangWeekNarrativeUi("算得出来的，先算", "移出、新增、撞了哪一格，一次算完，不用翻四个日历。"),
        FuguangWeekNarrativeUi("算不出来的，交回来", "「周六晚上还是周五晚上」取决于你那天还剩多少精力，只摆代价，不下结论。"),
        FuguangWeekNarrativeUi("只改被撞的那一格", "其余整块一个没动，改完还能整条撤回——这就是跨日变更不在四次对话里问完的原因。"),
    )

    private fun entry(
        id: String,
        title: String,
        location: String,
        start: Int,
        end: Int,
        tone: FuguangAgendaTone,
        status: String? = null,
    ) = FuguangAgendaEntryUi(id, title, location, start, end, tone, status)

    private fun plan(
        id: String,
        weekday: String,
        date: String,
        changed: Boolean,
        vararg entries: FuguangAgendaEntryUi,
    ) = FuguangDayPlanUi(id, weekday, date, entries.toList(), changed)
}
