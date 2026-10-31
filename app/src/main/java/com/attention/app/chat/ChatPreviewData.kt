package com.attention.app.chat

/** 纯 Mock 数据：只用于 Preview、Debug 演示和 UI 测试，不进入真实业务链路。 */
object FuguangChatPreviewData {
    val mainChat = FuguangChatUiState(
        title = "浮光主 Agent",
        subtitle = "已接入飞书 · 3 个群 · 2 位小助手",
        agentLabel = "主 Agent",
        input = "",
        entries = listOf(
            FuguangChatEntry.Timestamp("main-time", "今天 09:20"),
            FuguangChatEntry.UserText("main-user-1", "这周雅思和行测都挤在晚上，帮我重新排一下"),
            FuguangChatEntry.AssistantText(
                "main-ai-1",
                "看了一遍课表：19:30 之后连着两件事，容易做不完。把雅思挪到早上 07:30，行测放 20:30。",
            ),
            FuguangChatEntry.AdjustmentCard(
                id = "main-adjustment",
                title = "调整后的安排",
                rows = listOf(
                    AdjustmentRow("雅思写作 · 19:30 → 明早 07:30（-30 分钟）", "写作时长按 45 分钟重新安排"),
                    AdjustmentRow("行测资料分析 · 21:00 → 20:30（不变时长）", "保留原来的 40 分钟"),
                ),
            ),
            FuguangChatEntry.NotificationCard(
                id = "main-feishu",
                source = "飞书 · 管理会计课程群",
                timestamp = "1 分钟前",
                body = "李老师：本周四（10/30）的课调到周五 10:00，教室不变。",
                conflict = "检测到与你 10/30 19:30「雅思写作」冲突",
                primaryAction = "顺延到周六 10:00",
                secondaryAction = "保持原样",
            ),
            FuguangChatEntry.AssistantText(
                "main-ai-2",
                "顺延的话，周六上午的模考我会往后挪 1 小时——要我一起改吗？",
            ),
        ),
        suggestions = listOf(
            FuguangChatSuggestion("adjust-tonight", "调整今晚", FuguangSuggestionTone.GREEN),
            FuguangChatSuggestion("reschedule-week", "重排本周"),
            FuguangChatSuggestion("feishu-two", "飞书 2 条", FuguangSuggestionTone.GOLD),
        ),
    )

    val hub = FuguangConversationHubState(
        mainAgentSummary = "今天替你协调了 3 件事：雅思提前到 08:00、概率统计换到 4-201、周四的课改到周五上午。",
        pendingLabel = "3 项待你确认",
        assistants = listOf(
            FuguangHubAssistant(
                id = "ielts",
                title = "雅思助手",
                icon = FuguangHubIcon.IELTS,
                targetLabel = "目标 7.5 · 剩 42 天",
                progress = 0.78f,
                footer = "今日 3/5 · 听力 78%",
            ),
            FuguangHubAssistant(
                id = "plan",
                title = "行策助手",
                icon = FuguangHubIcon.PLAN,
                targetLabel = "行测 65→75 · 剩 88 天",
                progress = 0.55f,
                footer = "今日 2/4 · 资料分析",
            ),
        ),
        activities = listOf(
            FuguangHubActivity("ielts-activity", FuguangHubIcon.IELTS, "雅思助手", "听力 Section 3 完成，正确率 78%", "09:38"),
            FuguangHubActivity("plan-activity", FuguangHubIcon.PLAN, "行策助手", "第 3 套资料分析已完成，用时 26 分钟", "08:52"),
            FuguangHubActivity("feishu-activity", FuguangHubIcon.IELTS, "飞书 · 管理会计课程群", "李老师：本周四的课调到周五 10:00", "08:10", highlighted = true),
        ),
    )

    val assistantDetail = FuguangAssistantDetailState(
        title = "雅思助手",
        subtitle = "小 Agent · 由主 Agent 协调",
        goalLabel = "目标 7.5",
        heroTitle = "距 1 月 9 日考试 42 天",
        heroDetail = "本周完成 68% · 4/5 天达标",
        heroFootnote = "下一次模考：周六 10:00",
        tasks = listOf(
            FuguangAssistantTask("listening", "听力 Section 3 精听", "40 min", completed = true),
            FuguangAssistantTask("writing", "写作 Task 2 提纲", "25 min", completed = true),
            FuguangAssistantTask("speaking", "口语 Part 2 录音 3 题", "20 min", completed = false),
        ),
        planTitle = "12 周长计划",
        planProgress = 0.46f,
        planFootnote = "第 6 周 / 共 12 周",
        chartTitle = "近 7 天投入时长",
        chartDetail = "日均 35 分钟 · 较上周 +6 分钟",
        chartBars = listOf(0.62f, 0.48f, 0.76f, 0.42f, 0.88f, 0.30f, 1f),
        syncMessage = "已同步给主 Agent：每天 07:30 与 20:30 各 1 小时；遇到课程冲突，主 Agent 会来找你确认。",
        suggestions = listOf(
            FuguangChatSuggestion("not-done", "今天没做完", FuguangSuggestionTone.GREEN),
            FuguangChatSuggestion("review-mistakes", "听力错题复盘"),
        ),
        inputPlaceholder = "汇报进度，或让我重排今日任务…",
    )

    fun mainChatWithInput(input: String): FuguangChatUiState = mainChat.copy(input = input)
}
