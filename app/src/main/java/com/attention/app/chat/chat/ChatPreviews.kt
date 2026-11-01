package com.attention.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attention.app.ui.theme.AttentionTheme

@Preview(name = "浮光 · 主 Agent 对话", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AgentChatPreview() {
    AttentionTheme(darkTheme = false) {
        FuguangChatTheme {
            Box(Modifier.background(fuguangPalette().canvas)) {
                FuguangAgentChatScreen(
                    state = FuguangChatPreviewData.mainChat,
                    onInputChange = {},
                    onSend = {},
                    onSuggestionClick = {},
                )
            }
        }
    }
}

@Preview(name = "浮光 · 对话首页", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ConversationHubPreview() {
    AttentionTheme(darkTheme = false) {
        FuguangChatTheme {
            FuguangConversationHubScreen(
                state = FuguangChatPreviewData.hub,
                onOpenMainAgent = {},
                onOpenAssistant = {},
            )
        }
    }
}

@Preview(name = "浮光 · 雅思助手", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AssistantDetailPreview() {
    AttentionTheme(darkTheme = false) {
        FuguangChatTheme {
            FuguangAssistantDetailScreen(
                state = FuguangChatPreviewData.assistantDetail,
                input = "",
                onInputChange = {},
                onSend = {},
                onSuggestionClick = {},
            )
        }
    }
}

@Preview(name = "浮光 · 输入区方案 A", widthDp = 390, heightDp = 160, showBackground = true)
@Composable
private fun InputBarPreview() {
    AttentionTheme(darkTheme = false) {
        FuguangChatTheme {
            Box(Modifier.background(fuguangPalette().canvas).padding(20.dp)) {
                FuguangChatInputBar(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    agentLabel = "主 Agent",
                    modifier = Modifier.size(width = 350.dp, height = 56.dp),
                )
            }
        }
    }
}

@Preview(name = "浮光 · 今日日程", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DaySchedulePreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangDayScheduleScreen(FuguangAppPreviewData.daySchedule) } }
}

@Preview(name = "浮光 · 周安排", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WeekSchedulePreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangWeekScheduleScreen(FuguangAppPreviewData.weekSchedule) } }
}

@Preview(name = "浮光 · 学习", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LearningPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangLearningScreen(FuguangAppPreviewData.learning) } }
}

@Preview(name = "浮光 · 变更授权", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AuthorizationPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangAuthorizationScreen() } }
}

@Preview(name = "浮光 · 排程规则", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SchedulingRulesPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangSchedulingRulesScreen(FuguangAppPreviewData.schedulingRules) } }
}

@Preview(name = "浮光 · 新建小助手", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AssistantCreationPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangAssistantCreationScreen(FuguangAppPreviewData.assistantCreation) } }
}

@Preview(name = "浮光 · 小助手排计划", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AssistantPlanPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangAssistantPlanScreen(FuguangAppPreviewData.assistantPlan) } }
}

@Preview(name = "浮光 · 确认写入日程", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AgentConfirmationPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangAgentConfirmationScreen(FuguangAppPreviewData.agentConfirmation) } }
}

@Preview(name = "浮光 · 责任边界", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ResponsibilityPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangResponsibilityScreen(FuguangAppPreviewData.responsibility) } }
}

@Preview(name = "浮光 · 输入区方案", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun InputSpecPreview() {
    AttentionTheme(darkTheme = false) { FuguangChatTheme { FuguangInputSpecScreen() } }
}
