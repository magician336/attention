package com.attention.app.chat

import androidx.compose.ui.graphics.Color

private val Sage = Color(0xFF799B84)
private val SageSoft = Color(0xFFDCE7D6)
private val SageDeep = Color(0xFF4F6A53)
private val Gold = Color(0xFFF1D996)
private val Rose = Color(0xFFB77A7D)
private val RoseSoft = Color(0xFFF3E3DE)
private val Taupe = Color(0xFFA28F80)

object FuguangAppPreviewData {
    val daySchedule = FuguangDayScheduleState(
        title = "10月28日 周三",
        subtitle = "第 9 教学周 · 今天有 6 件事",
        notice = FuguangScheduleNotice("飞书 · 管理会计课程群", "周四课程临时调整，主 Agent 已拟好方案"),
        items = listOf(
            FuguangScheduleItem("morning-reading", "08:00", "08:45", "晨读 · 雅思听力精听", "45 分钟 · 已完成 · 雅思助手", FuguangScheduleTone.NORMAL),
            FuguangScheduleItem("accounting", "09:00", "10:40", "管理会计 MA", "三教 402 · 进行中 · 10:40 下课", FuguangScheduleTone.ACTIVE),
            FuguangScheduleItem("lunch", "12:30", "13:15", "午餐 & 午休", "45 分钟 · 弹性时间"),
            FuguangScheduleItem("fa2", "14:00", "15:40", "FA2 小组作业", "二教 3F 讨论区 · 100 分钟"),
            FuguangScheduleItem("probability", "16:00", "17:40", "概率统计", "4-201 · 教室已变更（飞书同步）", FuguangScheduleTone.WARNING),
            FuguangScheduleItem("ielts-writing", "19:30", "20:30", "雅思写作 Task 2 精练", "60 分钟 · 主 Agent 排入"),
        ),
        gaps = listOf(FuguangGapItem("walk-gap", "08:45 → 09:00  步行 15 分钟 · 寝室 → 三教 402（已预留）", afterItemIndex = 0)),
    )

    val weekSchedule = FuguangWeekScheduleState(
        title = "本周安排",
        subtitle = "10月26日 — 11月1日 · 4 项待完成",
        days = listOf(
            FuguangWeekDay(0, "一", "26"),
            FuguangWeekDay(1, "二", "27"),
            FuguangWeekDay(2, "三", "28", isToday = true),
            FuguangWeekDay(3, "四", "29"),
            FuguangWeekDay(4, "五", "30"),
            FuguangWeekDay(5, "六", "31"),
            FuguangWeekDay(6, "日", "1"),
        ),
        events = listOf(
            FuguangWeekEvent("mon-accounting", 0, 540, "09:00", "管理会计", FuguangScheduleTone.NORMAL),
            FuguangWeekEvent("mon-ielts", 0, 1170, "19:30", "雅思", FuguangScheduleTone.ACTIVE),
            FuguangWeekEvent("tue-fa2", 1, 840, "14:00", "FA2", FuguangScheduleTone.NORMAL),
            FuguangWeekEvent("wed-listening", 2, 480, "08:00", "听力", FuguangScheduleTone.ACTIVE),
            FuguangWeekEvent("wed-probability", 2, 960, "16:00", "概率统计", FuguangScheduleTone.WARNING),
            FuguangWeekEvent("wed-writing", 2, 1170, "19:30", "雅思写作", FuguangScheduleTone.ACTIVE),
            FuguangWeekEvent("thu-accounting", 3, 540, "09:00", "管理会计", FuguangScheduleTone.NORMAL),
            FuguangWeekEvent("thu-xingce", 3, 1260, "21:00", "行测", FuguangScheduleTone.NORMAL),
            FuguangWeekEvent("fri-fa2", 4, 600, "10:00", "FA2 改期", FuguangScheduleTone.WARNING),
            FuguangWeekEvent("fri-ielts", 4, 1170, "19:30", "雅思", FuguangScheduleTone.ACTIVE),
            FuguangWeekEvent("sat-mock", 5, 600, "10:00", "雅思模考", FuguangScheduleTone.NORMAL),
            FuguangWeekEvent("sun-review", 6, 1260, "21:00", "复盘", FuguangScheduleTone.NORMAL),
        ),
        weeklyActualHours = "18.5h",
        weeklyTargetHours = "20h",
        weeklyDetail = "雅思 12.5h   行测 6.0h   较上周 +2.3h",
    )

    val learning = FuguangLearningState(
        title = "学习",
        subtitle = "本周 18.5h / 目标 20h · 连续 12 天",
        reportLabel = "本周周报",
        longTermTitle = "长期目标",
        longTermMeta = "3 个 · 由小助手维护",
        goals = listOf(
            FuguangLearningGoal("ielts", "雅思 7.5", "今日 2/3 · 剩 42 天 · 68%", 0.68f, Sage),
            FuguangLearningGoal("xingce", "行测 75", "今日 1/2 · 剩 88 天 · 52%", 0.52f, Rose),
            FuguangLearningGoal("courses", "课业 4门", "本周 2.5h · 无截止", 0.36f, Taupe),
        ),
        weeklyTitle = "本周投入",
        weeklyActual = "18.5h",
        weeklyRate = "达标 93%",
        weeklyProgress = 0.93f,
        weeklySegments = listOf(
            FuguangInvestmentSegment("雅思", 0.68f, Sage),
            FuguangInvestmentSegment("行测", 0.32f, Rose),
        ),
        weeklyDetail = "雅思 12.5h   行测 6.0h   较上周 +2.3h",
        reviewTitle = "待巩固",
        reviewMeta = "你自己标记 · 3 项",
        reviewItems = listOf(
            FuguangReviewItem("map", "雅思 · 听力地图题定位", "你标记"),
            FuguangReviewItem("ratio", "行测 · 比重差速算", "自评：算得慢"),
            FuguangReviewItem("fa2", "FA2 · 合并报表抵消分录", "待复习"),
        ),
        chartTitle = "近 8 周投入",
        chartMeta = "打卡 52 天",
        chartValues = listOf(0.55f, 0.38f, 0.56f, 0.60f, 0.48f, 0.68f, 0.74f, 1f),
        countdowns = listOf(
            FuguangCountdown("雅思", "1月9日", "42 天"),
            FuguangCountdown("行测", "1月25日", "88 天"),
        ),
    )

    val schedulingRules = FuguangSchedulingRulesState(
        rules = listOf(
            FuguangSchedulingRule("quota", "①", "配额与档期分离", "小 Agent 只管「做什么 · 多久 · 锚点」，具体钟点由主 Agent 独占。小 Agent 永远不需要知道自己不知道的事。", Sage),
            FuguangSchedulingRule("gap", "②", "排不下就报差额", "按考试临近度分配 + 每目标周保底，可锁定某段不参与自动分配。排不下时报「差 4.5 小时」，给三个选项：砍目标 / 接受延期 / 周末补。绝不排到 23:00 后。", Gold),
            FuguangSchedulingRule("truth", "③", "只显示真数据", "完成状态、投入时长、考试锚点自动产生；正确率只在录入模考成绩后才出现。宁可空着，不编数字——一个假的 78% 会让整页数据失信。", Taupe),
            FuguangSchedulingRule("explain", "④", "解释必须查得出", "任何改动都要给出四行：约束 / 目标 / 权衡 / 代价。缺一行就不改。撤回时连改动理由和依赖链一起撤，不留孤立变化。", Rose),
            FuguangSchedulingRule("preference", "⑤", "学偏好要说出来", "你手动拖动 3 次同类改动才算偏好成立，且必须明说「以后默认排早上，不对点这里」。只学 1 次会过拟合，学了不说会显得日程自己乱跳。", Sage),
        ),
    )

    val assistantCreation = FuguangAssistantCreationState(
        suggestions = listOf(
            FuguangChatSuggestion("change-hours", "改成 12 小时", FuguangSuggestionTone.GREEN),
            FuguangChatSuggestion("preview", "先看草案"),
            FuguangChatSuggestion("feishu", "飞书 2 条", FuguangSuggestionTone.GOLD),
        ),
    )

    val assistantPlan = FuguangAssistantPlanState(
        availabilityRows = listOf(
            "课程" to "12 节，已避开（含周四那节调到周五）",
            "飞书" to "1 条变更，已按新时间重算",
            "你锁定" to "周日晚家教，不排",
        ),
        allocationSegments = listOf(
            FuguangInvestmentSegment("听力", 0.35f, Sage),
            FuguangInvestmentSegment("阅读", 0.25f, SageSoft),
            FuguangInvestmentSegment("写作", 0.25f, Gold),
            FuguangInvestmentSegment("口语", 0.15f, Taupe),
        ),
        anchors = listOf(
            "起床后 45 min  记忆类 · 听力精听，趁脑子最空",
            "午休前 25 min  轻任务 · 口语录音，不用桌子",
            "睡前 30 min  复盘 · 只回看错题，不学新的",
        ),
        suggestions = listOf(
            FuguangChatSuggestion("too-early", "早上太赶", FuguangSuggestionTone.GREEN),
            FuguangChatSuggestion("no-energy", "晚上没精力"),
        ),
    )

    val agentConfirmation = FuguangAgentConfirmationState(
        planRows = listOf(
            FuguangPlanRow("07:30–08:15   听力精听", "45 min"),
            FuguangPlanRow("12:20–12:45   口语录音 3 题", "25 min"),
            FuguangPlanRow("21:00–21:30   错题复盘", "30 min"),
        ),
        choices = listOf(
            "塞进周六 19:00–19:45，晚上连两场",
            "让行测顺延到周日，我另外去问它",
            "本周就少 45 分钟，下周补回来",
        ),
        suggestions = listOf(
            FuguangChatSuggestion("no-saturday", "周六不补", FuguangSuggestionTone.GREEN),
            FuguangChatSuggestion("change-time", "换个时间"),
            FuguangChatSuggestion("view-week", "看全部 7 天", FuguangSuggestionTone.GOLD),
        ),
    )

    val responsibility = FuguangResponsibilityState(
        roles = listOf(
            "① 用户定 · 底线" to "考试日 · 每周投入 · 不能占的时段。硬约束。谁都不能替你改，改一次得你亲口说。",
            "② 小 Agent 定 · 内容" to "做什么 · 多久 · 锚点。只交草案；不写钟点，不碰日程。它连你的课表都看不到。",
            "③ 主 Agent 定 · 落位" to "几点 · 跟谁抢 · 排不下怎么办。独占钟点，也独占报差额的义务；不改配额，只报差多少。",
        ),
    )

    val appState = FuguangAppState(
        daySchedule = daySchedule,
        weekSchedule = weekSchedule,
        conversationHub = FuguangChatPreviewData.hub,
        mainAgentChat = FuguangChatPreviewData.mainChat,
        assistantDetail = FuguangChatPreviewData.assistantDetail,
        learning = learning,
        authorization = FuguangAuthorizationState(),
        schedulingRules = schedulingRules,
        assistantCreation = assistantCreation,
        assistantPlan = assistantPlan,
        agentConfirmation = agentConfirmation,
        responsibility = responsibility,
    )
}

