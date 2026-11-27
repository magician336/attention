package com.attention.app.fuguang.prototype

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuguangWeekPrototypeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun adjustment_row_toggles_and_relocation_selects() {
        var toggled = ""
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                AdjustScreen(
                    beat = PrototypeBeat.ADJUST,
                    items = listOf(
                        PrototypeAdjustmentItem(
                            id = "demo",
                            title = "概率论第三单元习题",
                            subtitle = "",
                            flow = listOf("周五 14:00", "周六 10:00"),
                            alternatives = listOf("周五 14:00", "周六 14:00"),
                        ),
                    ),
                    adopted = listOf(true),
                    selfEdit = false,
                    extraMessages = emptyList(),
                    onToggleAdopt = { toggled = it },
                    onToggleSelfEdit = {},
                    onSaveSelfEdit = {},
                    onChooseAlternative = { _, _ -> },
                    onContinue = {},
                    onBackToRecognize = {},
                )
            }
        }
        rule.onNodeWithTag("adjustment-demo").performClick()
        rule.runOnIdle { assertEquals("demo", toggled) }
    }

    @Test
    fun relocation_requires_a_pick_and_emits_choice() {
        var picked: Char? = null
        val options = listOf(
            PrototypeRelocationOption('A', "周六 18:00", "图书馆", "延后收工", true),
            PrototypeRelocationOption('B', "周五 21:10", "寝室", "晚睡"),
        )
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                RelocateScreen(
                    beat = PrototypeBeat.RELOCATE,
                    options = options,
                    picked = picked,
                    skipWeek = false,
                    extraMessages = emptyList(),
                    onPick = { picked = it },
                    onSkipWeek = {},
                    onApply = {},
                    onBackToRecognize = {},
                )
            }
        }
        rule.onNodeWithText("先挑一个落点").assertIsNotEnabled()
        rule.onNodeWithTag("relocation-B").performClick()
        rule.runOnIdle { assertEquals('B', picked) }
    }

    @Test
    fun recognition_card_exposes_three_items() {
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                RecognizeScreen(
                    beat = PrototypeBeat.RECOGNIZE,
                    recap = listOf(
                        PrototypeRecapItem("1", "周日一整天不排事", "整天在北京", "周日"),
                        PrototypeRecapItem("2", "两件挪到周中", "概率论和 MA", "2 件事"),
                        PrototypeRecapItem("3", "等这门课下课", "周五 12:45 后", "课后"),
                    ),
                    recapReady = false,
                    editingRecapId = null,
                    editingRecapText = "",
                    onEditingRecapTextChange = {},
                    extraMessages = emptyList(),
                    onToggleRecap = {},
                    onStartEditRecap = {},
                    onSaveRecapEdit = {},
                    onCancelRecapEdit = {},
                    onConfirmRecap = {},
                    onResetRecap = {},
                    onContinue = {},
                )
            }
        }
        rule.onNodeWithText("我来确定一下你的要求").assertExists()
        rule.onNodeWithText("还有 3 条没确认").assertIsNotEnabled()
        assertTrue(rule.onAllNodesWithText("改一下").fetchSemanticsNodes().size == 3)
    }

    @Test
    fun day_screen_has_timeline_and_continue_action() {
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                DayScreen(
                    beat = PrototypeBeat.DAY,
                    days = prototypeInitialWeek(),
                    selectedDayIndex = 3,
                    mealKept = false,
                    customized = false,
                    extraMessages = emptyList(),
                    onSelectDay = {},
                    onBackToAdjust = {},
                    onBackToRecognize = {},
                    onContinue = {},
                    onMealKeep = {},
                )
            }
        }
        rule.onNodeWithTag("day-screen").assertExists()
        rule.onNodeWithText("继续 · 下一条").assertIsEnabled()
        rule.onNodeWithTag("timeline-thu-writing").assertExists()
    }
}
