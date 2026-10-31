package com.attention.app.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuguangChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun send_is_disabled_until_input_and_then_invokes_callback() {
        var sent = false
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    var input by remember { mutableStateOf("") }
                    FuguangChatInputBar(
                        value = input,
                        onValueChange = { input = it },
                        onSend = { sent = true },
                        agentLabel = "主 Agent",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chat-send").assertIsNotEnabled()
        composeRule.onNodeWithTag("chat-input").performTextInput("调整今晚")
        composeRule.onNodeWithTag("chat-send").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun suggestion_click_emits_the_selected_suggestion() {
        var selected: String? = null
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangQuickSuggestions(
                        suggestions = FuguangChatPreviewData.mainChat.suggestions,
                        onSuggestionClick = { selected = it.label },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chat-suggestion-adjust-tonight").performClick()
        composeRule.runOnIdle { assertEquals("调整今晚", selected) }
    }

    @Test
    fun conversation_hub_exposes_main_agent_entry() {
        var opened = false
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangConversationHubScreen(
                        state = FuguangChatPreviewData.hub,
                        onOpenMainAgent = { opened = true },
                        onOpenAssistant = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("浮光主 Agent").assertExists()
        composeRule.onNodeWithTag("chat-main-agent-card").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }
}


