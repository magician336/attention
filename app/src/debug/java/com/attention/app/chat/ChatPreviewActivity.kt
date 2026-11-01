package com.attention.app.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.attention.app.ui.theme.AttentionTheme

internal enum class ChatPreviewScreen {
    DAY,
    WEEK,
    HUB,
    MAIN,
    ASSISTANT,
    INPUT,
    LEARNING,
    AUTHORIZATION,
    RULES,
    CREATE,
    PLAN,
    CONFIRM,
    RESPONSIBILITY;

    companion object {
        fun from(value: String?): ChatPreviewScreen = when (value?.lowercase()) {
            "day" -> DAY
            "week" -> WEEK
            "hub" -> HUB
            "assistant" -> ASSISTANT
            "input" -> INPUT
            "learning" -> LEARNING
            "authorization" -> AUTHORIZATION
            "rules" -> RULES
            "create" -> CREATE
            "plan" -> PLAN
            "confirm" -> CONFIRM
            "responsibility" -> RESPONSIBILITY
            else -> MAIN
        }
    }
}

/** Debug-only manifest activity for visual review of all 13 design boards. */
class ChatPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requested = ChatPreviewScreen.from(intent.getStringExtra(EXTRA_SCREEN))
        setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    ChatPreviewApp(initialScreen = requested)
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@Composable
private fun ChatPreviewApp(initialScreen: ChatPreviewScreen) {
    val demo = FuguangAppPreviewData.appState
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var mainInput by rememberSaveable { mutableStateOf("") }
    var assistantInput by rememberSaveable { mutableStateOf("") }
    var creationInput by rememberSaveable { mutableStateOf("") }
    var planInput by rememberSaveable { mutableStateOf("") }
    var confirmInput by rememberSaveable { mutableStateOf("") }
    var creationHours by rememberSaveable { mutableStateOf(demo.assistantCreation.selectedHours) }
    var planChoice by rememberSaveable { mutableStateOf(demo.agentConfirmation.selectedChoice) }
    var mainEntries by remember { mutableStateOf(demo.mainAgentChat.entries) }

    val actions = object : FuguangAppActions {
        override fun onNavigate(destination: FuguangBottomDestination) {
            screen = when (destination) {
                FuguangBottomDestination.AGENDA -> ChatPreviewScreen.DAY
                FuguangBottomDestination.CHAT -> ChatPreviewScreen.HUB
                FuguangBottomDestination.STUDY -> ChatPreviewScreen.LEARNING
                FuguangBottomDestination.PROFILE -> ChatPreviewScreen.RESPONSIBILITY
            }
        }

        override fun onBack() {
            screen = ChatPreviewScreen.HUB
        }

        override fun onOpenAgent(agentId: String) {
            screen = if (agentId == "main") ChatPreviewScreen.MAIN else ChatPreviewScreen.ASSISTANT
        }

        override fun onOpenWeeklyReport() {
            screen = ChatPreviewScreen.LEARNING
        }

        override fun onSelectWeeklyHours(hours: Int) {
            creationHours = hours
        }

        override fun onCreateAssistant() {
            screen = ChatPreviewScreen.PLAN
        }

        override fun onSelectPlanChoice(index: Int) {
            planChoice = index
        }

        override fun onConfirmPlan() {
            screen = ChatPreviewScreen.MAIN
        }

        override fun onCancelPlan() {
            screen = ChatPreviewScreen.MAIN
        }

        override fun onChatInputChange(value: String) {
            when (screen) {
                ChatPreviewScreen.MAIN, ChatPreviewScreen.INPUT -> mainInput = value
                ChatPreviewScreen.ASSISTANT -> assistantInput = value
                ChatPreviewScreen.CREATE -> creationInput = value
                ChatPreviewScreen.PLAN -> planInput = value
                ChatPreviewScreen.CONFIRM -> confirmInput = value
                else -> Unit
            }
        }

        override fun onSendChat() {
            val text = when (screen) {
                ChatPreviewScreen.MAIN, ChatPreviewScreen.INPUT -> mainInput
                ChatPreviewScreen.ASSISTANT -> assistantInput
                ChatPreviewScreen.CREATE -> creationInput
                ChatPreviewScreen.PLAN -> planInput
                ChatPreviewScreen.CONFIRM -> confirmInput
                else -> ""
            }.trim()
            if (text.isNotEmpty()) {
                mainEntries = mainEntries + listOf(
                    FuguangChatEntry.UserText("demo-user-" + mainEntries.size, text),
                    FuguangChatEntry.AssistantText(
                        "demo-ai-" + mainEntries.size,
                        "收到。我先按当前课程、目标和可用时间检查，再把结果交给你确认。",
                    ),
                )
                screen = ChatPreviewScreen.MAIN
                mainInput = ""
            }
        }

        override fun onSuggestionClick(suggestion: FuguangChatSuggestion) {
            when (screen) {
                ChatPreviewScreen.MAIN, ChatPreviewScreen.INPUT -> mainInput = suggestion.label
                ChatPreviewScreen.ASSISTANT -> assistantInput = suggestion.label
                ChatPreviewScreen.CREATE -> creationInput = suggestion.label
                ChatPreviewScreen.PLAN -> planInput = suggestion.label
                ChatPreviewScreen.CONFIRM -> confirmInput = suggestion.label
                else -> Unit
            }
        }
    }

    when (screen) {
        ChatPreviewScreen.DAY -> FuguangDayScheduleScreen(demo.daySchedule, actions)
        ChatPreviewScreen.WEEK -> FuguangWeekScheduleScreen(demo.weekSchedule, actions)
        ChatPreviewScreen.INPUT -> FuguangInputSpecScreen(demo.mainAgentChat.copy(input = mainInput), actions)
        ChatPreviewScreen.LEARNING -> FuguangLearningScreen(demo.learning, actions)
        ChatPreviewScreen.AUTHORIZATION -> FuguangAuthorizationScreen(demo.authorization, actions)
        ChatPreviewScreen.RULES -> FuguangSchedulingRulesScreen(demo.schedulingRules)
        ChatPreviewScreen.RESPONSIBILITY -> FuguangResponsibilityScreen(demo.responsibility)
        ChatPreviewScreen.CREATE -> FuguangAssistantCreationScreen(
            demo.assistantCreation.copy(selectedHours = creationHours, input = creationInput),
            actions,
        )
        ChatPreviewScreen.PLAN -> FuguangAssistantPlanScreen(
            demo.assistantPlan.copy(input = planInput),
            actions,
        )
        ChatPreviewScreen.CONFIRM -> FuguangAgentConfirmationScreen(
            demo.agentConfirmation.copy(selectedChoice = planChoice, input = confirmInput),
            actions,
        )
        ChatPreviewScreen.HUB -> FuguangConversationHubScreen(
            state = demo.conversationHub,
            onOpenMainAgent = { screen = ChatPreviewScreen.MAIN },
            onOpenAssistant = { screen = ChatPreviewScreen.ASSISTANT },
            onBottomDestination = actions::onNavigate,
        )
        ChatPreviewScreen.MAIN -> FuguangAgentChatScreen(
            state = demo.mainAgentChat.copy(input = mainInput, entries = mainEntries),
            onInputChange = { mainInput = it },
            onSend = actions::onSendChat,
            onSuggestionClick = actions::onSuggestionClick,
            onBack = actions::onBack,
            onPrimaryAction = { screen = ChatPreviewScreen.CONFIRM },
            onSecondaryAction = { screen = ChatPreviewScreen.MAIN },
        )
        ChatPreviewScreen.ASSISTANT -> FuguangAssistantDetailScreen(
            state = demo.assistantDetail,
            input = assistantInput,
            onInputChange = { assistantInput = it },
            onSend = actions::onSendChat,
            onSuggestionClick = actions::onSuggestionClick,
            onBack = actions::onBack,
        )
    }
}

