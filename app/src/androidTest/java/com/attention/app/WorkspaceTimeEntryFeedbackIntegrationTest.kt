package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.createSettingsStore
import com.attention.app.ui.components.AttentionFeedbackTags
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 反馈层的页面接入验收：删除实际投入必须先确认，确认之后仍然可以撤销。
 *
 * 这条用例走真实页面路径：日程页上的实际投入删除按钮 → 确认弹窗 → 撤销提示 → 数据回到原样。
 * 它证明撤销真的调用了恢复动作，而不只是弹出一个撤销入口。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceTimeEntryFeedbackIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "ui-feedback-target"
        private const val STAGE_ID = "ui-feedback-stage"
        private const val ENTRY_ID = "ui-feedback-owned"
        private const val TARGET_TITLE = "UI-反馈接入"

        @JvmStatic
        @BeforeClass
        fun seedFeedbackStateBeforeActivityStarts() {
            val state = AttentionState()
            val planningDate = state.planningDate(Instant.now()).toString()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomAttentionStateRepository(
                        business = RoomBusinessDataRepository(database),
                        settings = context.createSettingsStore(),
                    ).replace(
                        state.copy(
                            targets = listOf(Target(TARGET_ID, title = TARGET_TITLE)),
                            goalStages = listOf(
                                GoalStage(STAGE_ID, TARGET_ID, GoalCadence.ONE_TIME, 30, planningDate),
                            ),
                            timeEntries = listOf(TimeEntry(ENTRY_ID, planningDate, 20, TARGET_ID)),
                        ),
                    )
                }
            } finally {
                database.close()
            }
            // #177：未完成引导时第一次启动是全屏引导；这条用例要看的是引导之后的工作区
            WorkspaceGuideSeed.markCompleted()
        }
    }

    @Test
    fun deleting_a_time_record_requires_confirmation_and_the_undo_restores_it() {
        openToday()
        assertEntryExists()

        // 危险操作先确认：没有确认之前数据不动，也不出现撤销提示。
        clickDelete()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_IMPACT, useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEntryExists()

        // 确认之后才真的删除。
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM, useUnmergedTree = true).performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("delete-time-$ENTRY_ID").fetchSemanticsNodes().isEmpty()
        }

        // 删除是短时提示而不是打断式弹窗，并且带撤销入口。
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag(AttentionFeedbackTags.SNACKBAR).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AttentionFeedbackTags.SNACKBAR_MESSAGE, useUnmergedTree = true).assertIsDisplayed()

        // 撤销真的把这条实际投入找回来。
        composeRule.onNodeWithTag(AttentionFeedbackTags.SNACKBAR_UNDO, useUnmergedTree = true).performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("delete-time-$ENTRY_ID").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertEntryExists()
    }

    @Test
    fun dismissing_the_confirmation_keeps_the_time_record() {
        openToday()
        assertEntryExists()

        clickDelete()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_DISMISS, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEntryExists()
        val snackbars = composeRule.onAllNodesWithTag(AttentionFeedbackTags.SNACKBAR).fetchSemanticsNodes()
        check(snackbars.isEmpty()) { "取消确认不应该发出撤销提示" }
    }

    @Test
    fun the_target_picker_shows_the_selection_with_a_mark_not_only_a_colour() {
        openToday()
        // The manual ledger editor now lives on the agenda page, so the picker opens with 编辑.
        composeRule.onNodeWithTag("edit-time-$ENTRY_ID").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 目标选择器必须在颜色之外说清楚当前选中了谁：勾选图标 + “已选中：…”文案。
        // 查询限定在编辑表单里的那一个选择器上，避免与页面其它选择态混淆。
        val picker = composeRule.onNodeWithTag("edit-time-target-$ENTRY_ID").performScrollTo()
        picker.assert(hasAnyDescendant(hasText("已选中：$TARGET_TITLE")))
        picker.assert(hasAnyDescendant(hasTestTag(AttentionFeedbackTags.SELECTED_MARK)))
        picker.assert(hasAnyDescendant(hasTestTag(AttentionFeedbackTags.SELECTED_LABEL)))
    }

    private fun openToday() {
        // 「今天」不再是顶级入口：合并后的日程页默认打开当前规划日，手动账本在这一页上。
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun clickDelete() {
        composeRule.onNodeWithTag("delete-time-$ENTRY_ID").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun assertEntryExists() {
        composeRule.onNodeWithTag("delete-time-$ENTRY_ID").performScrollTo().assertIsDisplayed()
    }
}