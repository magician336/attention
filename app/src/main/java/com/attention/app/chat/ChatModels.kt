package com.attention.app.chat

/** 对话页只消费展示模型；真实消息类型和业务状态由任务 2 / ViewModel 负责。 */
sealed interface FuguangChatEntry {
    val id: String

    data class Timestamp(
        override val id: String,
        val label: String,
    ) : FuguangChatEntry

    data class UserText(
        override val id: String,
        val text: String,
    ) : FuguangChatEntry

    data class AssistantText(
        override val id: String,
        val text: String,
    ) : FuguangChatEntry

    data class AdjustmentCard(
        override val id: String,
        val title: String,
        val rows: List<AdjustmentRow>,
    ) : FuguangChatEntry

    data class NotificationCard(
        override val id: String,
        val source: String,
        val timestamp: String,
        val body: String,
        val conflict: String,
        val primaryAction: String,
        val secondaryAction: String,
    ) : FuguangChatEntry
}

data class AdjustmentRow(
    val title: String,
    val detail: String,
)

enum class FuguangSuggestionTone {
    NEUTRAL,
    GREEN,
    GOLD,
}

data class FuguangChatSuggestion(
    val id: String,
    val label: String,
    val tone: FuguangSuggestionTone = FuguangSuggestionTone.NEUTRAL,
)

data class FuguangChatUiState(
    val title: String,
    val subtitle: String,
    val agentLabel: String = "主 Agent",
    val input: String = "",
    val entries: List<FuguangChatEntry> = emptyList(),
    val suggestions: List<FuguangChatSuggestion> = emptyList(),
    val inputPlaceholder: String = "说点什么…",
)

data class FuguangHubAssistant(
    val id: String,
    val title: String,
    val icon: FuguangHubIcon,
    val targetLabel: String,
    val progress: Float,
    val footer: String,
)

enum class FuguangHubIcon {
    IELTS,
    PLAN,
}

data class FuguangHubActivity(
    val id: String,
    val icon: FuguangHubIcon,
    val title: String,
    val detail: String,
    val time: String,
    val highlighted: Boolean = false,
)

data class FuguangConversationHubState(
    val title: String = "对话",
    val readLabel: String = "全部已读",
    val mainAgentTitle: String = "浮光主 Agent",
    val mainAgentBadge: String = "飞书已接入",
    val mainAgentSummary: String,
    val pendingLabel: String,
    val openLabel: String = "进入对话",
    val assistants: List<FuguangHubAssistant>,
    val activities: List<FuguangHubActivity>,
    val syncLabel: String = "飞书 2 条待同步",
)

enum class FuguangBottomDestination {
    AGENDA,
    CHAT,
    STUDY,
    PROFILE,
}

data class FuguangAssistantTask(
    val id: String,
    val title: String,
    val duration: String,
    val completed: Boolean,
)

data class FuguangAssistantDetailState(
    val title: String,
    val subtitle: String,
    val goalLabel: String,
    val heroTitle: String,
    val heroDetail: String,
    val heroFootnote: String,
    val tasks: List<FuguangAssistantTask>,
    val planTitle: String,
    val planProgress: Float,
    val planFootnote: String,
    val chartTitle: String,
    val chartDetail: String,
    val chartBars: List<Float>,
    val syncMessage: String,
    val suggestions: List<FuguangChatSuggestion>,
    val inputPlaceholder: String,
)
