package com.attention.app.fuguang.spec

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.fuguang.components.FuguangAdjustmentCardUi
import com.attention.app.fuguang.components.FuguangAdjustmentItemUi
import com.attention.app.fuguang.components.FuguangC6SuggestionCapsules
import com.attention.app.fuguang.components.FuguangDateCellUi
import com.attention.app.fuguang.components.FuguangK1AdjustmentCard
import com.attention.app.fuguang.components.FuguangO1BottomSheet
import com.attention.app.fuguang.components.FuguangRelocationOptionUi
import com.attention.app.fuguang.components.FuguangS3InputArea
import com.attention.app.fuguang.components.FuguangS4SendButton
import com.attention.app.fuguang.components.FuguangT1DateCapsuleBar
import com.attention.app.fuguang.components.FuguangTheme
import com.attention.app.ui.theme.AttentionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuguangSpecComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun s4_is_disabled_when_empty() {
        rule.setContent {
            AttentionTheme(darkTheme = false) { FuguangTheme { FuguangS4SendButton(enabled = false, onClick = {}) } }
        }
        rule.onNodeWithTag("send-s4").assertIsNotEnabled()
    }

    @Test
    fun s3_emits_send() {
        var sent = false
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme {
                    var value by remember { mutableStateOf("") }
                    FuguangS3InputArea(value = value, onValueChange = { value = it }, onSend = { sent = true })
                }
            }
        }
        rule.onNodeWithTag("input-s3").performTextInput("重排本周")
        rule.onNodeWithTag("send-s4").assertIsEnabled().performClick()
        rule.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun t1_emits_selected_day() {
        var selected = ""
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme {
                    FuguangT1DateCapsuleBar(
                        days = listOf(FuguangDateCellUi("mon", "一", "3"), FuguangDateCellUi("thu", "四", "6")),
                        onSelect = { selected = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("t1-day-thu").performClick()
        rule.runOnIdle { assertEquals("thu", selected) }
    }

    @Test
    fun k1_emits_item_toggle() {
        var toggled = ""
        val card = FuguangAdjustmentCardUi(
            title = "1 处调整",
            status = "待确认",
            items = listOf(FuguangAdjustmentItemUi("demo", "概率论", "周三 16:10", true)),
            primaryLabel = "确定",
            secondaryLabel = "自调",
        )
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme {
                    FuguangK1AdjustmentCard(card, onToggleItem = { toggled = it }, onPrimary = {}, onSecondary = {})
                }
            }
        }
        rule.onNodeWithTag("k1-toggle-demo").performClick()
        rule.runOnIdle { assertEquals("demo", toggled) }
    }

    @Test
    fun o1_emits_selected_option() {
        var selected = "sat"
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme {
                    FuguangO1BottomSheet(
                        title = "放在哪里",
                        subtitle = "选一个代价",
                        options = listOf(
                            FuguangRelocationOptionUi("sat", "A", "周六 18:00", "图书馆", "晚出门"),
                            FuguangRelocationOptionUi("fri", "B", "周五 21:10", "寝室", "晚睡"),
                        ),
                        selectedId = selected,
                        onSelect = { selected = it },
                        onConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("o1-option-fri").performClick()
        rule.runOnIdle { assertEquals("fri", selected) }
    }

    @Test
    fun c6_emits_suggestion() {
        var selected = ""
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme { FuguangC6SuggestionCapsules(listOf("把这周重排一下"), onSelect = { selected = it }) }
            }
        }
        rule.onNodeWithTag("suggestion-把这周重排一下").performClick()
        rule.runOnIdle { assertEquals("把这周重排一下", selected) }
    }
}
