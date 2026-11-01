package com.attention.app.chat

import androidx.compose.foundation.layout.Column
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
    @Test
    fun day_schedule_exposes_add_and_schedule_actions() {
        var added = false
        var opened = ""
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangDayScheduleScreen(
                        state = FuguangAppPreviewData.daySchedule,
                        actions = object : FuguangAppActions {
                            override fun onAddSchedule() { added = true }
                            override fun onOpenSchedule(id: String) { opened = id }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("schedule-add").performClick()
        composeRule.onNodeWithText("管理会计 MA").performClick()
        composeRule.runOnIdle {
            assertTrue(added)
            assertEquals("accounting", opened)
        }
    }

    @Test
    fun learning_report_and_review_actions_are_wired() {
        var report = false
        var review = ""
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangLearningScreen(
                        state = FuguangAppPreviewData.learning,
                        actions = object : FuguangAppActions {
                            override fun onOpenWeeklyReport() { report = true }
                            override fun onOpenReviewItem(itemId: String) { review = itemId }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("本周周报").performClick()
        composeRule.onNodeWithText("雅思 · 听力地图题定位").performClick()
        composeRule.runOnIdle {
            assertTrue(report)
            assertEquals("map", review)
        }
    }

    @Test
    fun assistant_creation_hours_emit_selected_value() {
        var selectedHours = 0
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangAssistantCreationScreen(
                        state = FuguangAppPreviewData.assistantCreation,
                        actions = object : FuguangAppActions {
                            override fun onSelectWeeklyHours(hours: Int) { selectedHours = hours }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("12 小时").performClick()
        composeRule.runOnIdle { assertEquals(12, selectedHours) }
    }

    @Test
    fun agent_confirmation_choice_emits_selected_index() {
        var selectedChoice = -1
        composeRule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangChatTheme {
                    FuguangAgentConfirmationScreen(
                        state = FuguangAppPreviewData.agentConfirmation,
                        actions = object : FuguangAppActions {
                            override fun onSelectPlanChoice(index: Int) { selectedChoice = index }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("本周就少 45 分钟，下周补回来").performClick()
        composeRule.runOnIdle { assertEquals(2, selectedChoice) }
    }
}

