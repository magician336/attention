package com.attention.app.fuguang.chat

import com.attention.app.fuguang.components.FuguangAdjustmentCardUi
import com.attention.app.fuguang.components.FuguangAdjustmentItemUi
import com.attention.app.fuguang.components.FuguangComparisonRowUi
import com.attention.app.fuguang.components.FuguangFeishuCardUi
import com.attention.app.fuguang.components.FuguangRelocationOptionUi
import com.attention.app.fuguang.components.FuguangTextSegment
import com.attention.app.fuguang.components.FuguangWeekDayCardUi
import com.attention.app.fuguang.components.FuguangWeekEntryUi

/**
 * 对话页的示例数据。
 *
 * 真实内容应当来自 Agent 运行结果与用户数据；在接入推理层之前，这里提供与
 * 「一周重排」原型一致的措辞与结构，方便和规范 P10–P13 的样张逐句对照。
 */
internal object FuguangChatDemoData {
    /** 名称槽：规范 P9 要求它是 prop，出厂值「浮光」，用户可改。 */
    const val agentName = "浮光"

    const val agentRole = "主 Agent · 已接入飞书"

    /** S2 目标胶囊只放数字，规范限制 6 字以内。 */
    const val goalLabel = "7.5"

    const val sessionStamp = "今天 21:47"

    const val userText =
        "周日临时有个去北京的面试，一整天都不在。概率论第三单元的习题和管理会计的复习得挪到周中，" +
            "但一定要在这门课上完之后。"

    /** C2 的强调用角色色而非加粗：规范 P11「强调用 main-deep，不加粗体」。 */
    val agentRecognition = FuguangTextSegment(
        text = "收到了。要挪的是三件事：概率论作业 3 小时、MA 笔记 1.5 小时，还有你每天的群面练习 40 分钟。",
        emphasis = listOf("概率论作业 3 小时", "MA 笔记 1.5 小时", "群面练习 40 分钟"),
    )

    val agentConstraint = FuguangTextSegment(
        text = "有个硬约束先跟你说：概率论的课在周五上午，所以那 3 小时只能在周五下午和周六，" +
            "周中塞不进去。周中剩的只有周三晚和周四。",
        emphasis = listOf("周五上午", "周五下午和周六", "周三晚和周四"),
    )

    /** C6 最多三条，单条不超过 14 字（超了组件会截断）。 */
    val suggestions = listOf("把这个月重排一下", "周四下午别排了", "先看看我有哪些目标")

    /** K1 采纳之后，Agent 把「撞车」摊开的那句话。 */
    val agentConflict = FuguangTextSegment(
        text = "撞上了：周四 14:00 那格原来是自习，现在被 FA2 占了。要我把群面挪个位置吗？",
        emphasis = listOf("周四 14:00", "被 FA2 占了"),
    )

    /** 「我自己调」之后 Agent 的回路，不跳屏。 */
    val agentHandOver = FuguangTextSegment(
        text = "好，那你自己调。改完跟我说一声，我就不动了。",
        emphasis = listOf("你自己调"),
    )

    /** K1：一次改动的清单，规范上限 4 条。 */
    val adjustmentCard = FuguangAdjustmentCardUi(
        title = "4 处调整",
        status = "待确认",
        items = listOf(
            FuguangAdjustmentItemUi(
                id = "probability",
                title = "概率论 第三单元",
                detail = "周三 16:10–17:10 · 课后",
                accepted = true,
            ),
            FuguangAdjustmentItemUi(
                id = "fa2",
                title = "FA2 作业",
                detail = "周四 19:00–21:00",
                accepted = true,
            ),
            FuguangAdjustmentItemUi(
                id = "ma",
                title = "MA 复习",
                detail = "原本 周五 20:00",
                accepted = false,
            ),
            FuguangAdjustmentItemUi(
                id = "sunday",
                title = "周日下午面试",
                detail = "当天 7 处日程整体移出",
                accepted = true,
            ),
        ),
        primaryLabel = "就按这个改",
        secondaryLabel = "我自己调",
    )

    /** K2：外部消息转述。左强调条在说「这不是 AI 编的」。 */
    val feishuCard = FuguangFeishuCardUi(
        source = "飞书 · 会计课程群",
        time = "1 分钟前",
        body = "李老师：本周四的课调到周五 10:00，教室不变。",
        tags = listOf("与周四安排冲突", "已自动同步"),
    )

    /** K3：现在 / 原来两行时间，必须分开画。 */
    val comparisonRows = listOf(
        FuguangComparisonRowUi(label = "现在", text = "FA2 14:00–16:40", isOriginal = false),
        FuguangComparisonRowUi(label = "原来", text = "空档 · 原本自习", isOriginal = true),
    )

    const val comparisonTitle = "周四下午 现在 vs 原来"

    /** O1：最多三个落点，每个都写清代价。 */
    val relocationOptions = listOf(
        FuguangRelocationOptionUi(
            id = "sat1800",
            indexLabel = "A",
            title = "周六 18:00",
            subtitle = "饭后，图书馆还开着",
            cost = "周六晚不能出门",
            recommended = true,
        ),
        FuguangRelocationOptionUi(
            id = "fri2110",
            indexLabel = "B",
            title = "周五 21:10",
            subtitle = "睡前 · 会晚睡 40 分钟",
            cost = "当天已有三门课",
        ),
        FuguangRelocationOptionUi(
            id = "mon1000",
            indexLabel = "C",
            title = "下周一 10:00",
            subtitle = "空档 · 但要拖到下周",
            cost = "跨周，本周雅思写作记 0 小时",
        ),
    )

    /** W1 的七张日卡：确认落点之后给用户看「改好的这周」。 */
    val weekDays = listOf(
        weekDay("mon", "周一", "11.2", "3h 30m", listOf(entry("e1", "09:00", "10:40", "管理会计", "二主楼 B301"), entry("e2", "19:30", "21:00", "雅思精听", "图书馆 4F"))),
        weekDay("tue", "周二", "11.3", "2h 40m", listOf(entry("e3", "14:00", "16:40", "FA2", "改到周四"))),
        weekDay("wed", "周三", "11.4", "5h 20m", listOf(entry("e4", "08:00", "09:40", "听力精听", "图书馆", false), entry("e5", "16:10", "17:10", "概率论 第三单元", "课后"), entry("e6", "19:30", "21:00", "雅思写作", "图书馆", true))),
        weekDay("thu", "周四", "11.5", "3h 00m", listOf(entry("e7", "09:00", "10:40", "管理会计 MA", "三教 402"), entry("e8", "21:00", "21:30", "行测复盘", "寝室"))),
        weekDay("fri", "周五", "11.6", "4h 10m", listOf(entry("e9", "10:00", "11:40", "FA2 改期", "二主楼 B301", true), entry("e10", "19:30", "21:00", "雅思听力", "图书馆"))),
        weekDay("sat", "周六", "11.7", "4h 00m", listOf(entry("e11", "10:00", "12:00", "雅思模考", "图书馆"), entry("e12", "18:00", "19:00", "全英群面", "线上", true))),
        weekDay("sun", "周日", "11.8", "1h 00m", listOf(entry("e13", "21:00", "22:00", "本周复盘", "寝室"))),
    )

    private fun entry(id: String, start: String, end: String, title: String, location: String, isNew: Boolean = false) =
        FuguangWeekEntryUi(id = id, start = start, end = end, title = title, location = location, isNew = isNew)

    private fun weekDay(id: String, weekday: String, date: String, totalLabel: String, entries: List<FuguangWeekEntryUi>) =
        FuguangWeekDayCardUi(id = id, weekday = weekday, date = date, totalLabel = totalLabel, entries = entries)}
