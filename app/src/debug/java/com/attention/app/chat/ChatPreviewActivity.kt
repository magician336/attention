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
    HUB,
    MAIN,
    ASSISTANT;

    companion object {
        fun from(value: String?): ChatPreviewScreen = when (value?.lowercase()) {
            "hub" -> HUB
            "assistant" -> ASSISTANT
            else -> MAIN
        }
    }
}

/**
 * 仅 Debug 变体使用的静态页面入口，方便设计评审和 1b 截图验收。
 *
 * adb 启动：
 * adb shell am start -n com.attention.app/.chat.ChatPreviewActivity
 * adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen hub
 * adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen assistant
 */
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
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var mainInput by rememberSaveable { mutableStateOf("") }
    var assistantInput by rememberSaveable { mutableStateOf("") }
    var mainEntries by remember { mutableStateOf(FuguangChatPreviewData.mainChat.entries) }

    val mainState = FuguangChatPreviewData.mainChat.copy(
        input = mainInput,
        entries = mainEntries,
    )

    when (screen) {
        ChatPreviewScreen.HUB -> FuguangConversationHubScreen(
            state = FuguangChatPreviewData.hub,
            onOpenMainAgent = { screen = ChatPreviewScreen.MAIN },
            onOpenAssistant = { screen = ChatPreviewScreen.ASSISTANT },
            onBottomDestination = { destination ->
                if (destination == FuguangBottomDestination.CHAT) {
                    screen = ChatPreviewScreen.HUB
                }
            },
        )
        ChatPreviewScreen.MAIN -> FuguangAgentChatScreen(
            state = mainState,
            onInputChange = { mainInput = it },
            onSend = {
                val text = mainInput.trim()
                if (text.isNotEmpty()) {
                    mainEntries = mainEntries + listOf(
                        FuguangChatEntry.UserText("demo-user-${mainEntries.size}", text),
                        FuguangChatEntry.AssistantText(
                            "demo-ai-${mainEntries.size}",
                            "收到。我先按今天的课程和可用时间检查冲突，再把调整结果给你确认。",
                        ),
                    )
                    mainInput = ""
                }
            },
            onSuggestionClick = { suggestion -> mainInput = suggestion.label },
            onBack = { screen = ChatPreviewScreen.HUB },
        )
        ChatPreviewScreen.ASSISTANT -> FuguangAssistantDetailScreen(
            state = FuguangChatPreviewData.assistantDetail,
            input = assistantInput,
            onInputChange = { assistantInput = it },
            onSend = { assistantInput = "" },
            onSuggestionClick = { suggestion -> assistantInput = suggestion.label },
            onBack = { screen = ChatPreviewScreen.HUB },
        )
    }
}
