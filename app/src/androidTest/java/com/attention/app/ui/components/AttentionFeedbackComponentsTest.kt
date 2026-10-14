package com.attention.app.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 界面 seam：反馈类组件共用一套标识，撤销真的能被触发并生效。
 *
 * 断言只读用户能观察到的结果：标识、文案、可用性与点击后的效果。
 */
@RunWith(AndroidJUnit4::class)
class AttentionFeedbackComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun snackbar_shows_the_message_and_undo_runs_its_effect() {
        var undoCount = 0
        mount {
            val state = rememberAttentionFeedbackHostState()
            AttentionFeedbackHost(state = state) { PageBody() }
            LaunchedEffect(Unit) {
                state.showUndoableMessage("已删除一条时间记录", undoLabel = "撤销") { undoCount++ }
            }
        }

        waitForTag(AttentionFeedbackTags.SNACKBAR_UNDO).performClick()
        waitUntilCountReached("撤销必须真正执行一次恢复", 1) { undoCount }

        assertEquals("撤销只能被触发一次", 1, undoCount)
    }

    @Test
    fun snackbar_message_is_readable_as_text_not_only_as_color() {
        mount {
            val state = rememberAttentionFeedbackHostState()
            AttentionFeedbackHost(state = state) { PageBody() }
            LaunchedEffect(Unit) { state.showMessage("已保存日程条目") }
        }

        waitForTag(AttentionFeedbackTags.SNACKBAR)
        composeRule.onNodeWithTag(AttentionFeedbackTags.SNACKBAR_MESSAGE, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("已保存日程条目").assertIsDisplayed()
    }

    @Test
    fun snackbar_without_undo_has_no_undo_entry() {
        mount {
            val state = rememberAttentionFeedbackHostState()
            AttentionFeedbackHost(state = state) { PageBody() }
            LaunchedEffect(Unit) { state.showMessage("已保存日程条目") }
        }

        waitForTag(AttentionFeedbackTags.SNACKBAR)
        composeRule.onAllNodesWithTag(AttentionFeedbackTags.SNACKBAR_UNDO, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun inline_error_shows_the_message_next_to_the_field_and_can_retry() {
        var retryCount = 0
        mount {
            Column {
                Text("目标名称")
                AttentionInlineError(message = "目标名称不能为空", onRetry = { retryCount++ })
            }
        }

        composeRule.onNodeWithTag(AttentionFeedbackTags.INLINE_ERROR, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("目标名称不能为空").assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.INLINE_ERROR_RETRY, useUnmergedTree = true)
            .performClick()

        assertEquals("内联错误必须能就地重试", 1, retryCount)
    }

    @Test
    fun inline_error_without_retry_offers_no_retry_entry() {
        mount { AttentionInlineError(message = "目标名称不能为空") }

        composeRule.onNodeWithTag(AttentionFeedbackTags.INLINE_ERROR, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(AttentionFeedbackTags.INLINE_ERROR_RETRY, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun confirmation_dialog_states_the_impact_and_confirms_on_demand() {
        var confirmCount = 0
        mount {
            AttentionConfirmDialog(
                visible = true,
                title = "删除这条时间记录？",
                impact = "删除后这条实际投入不再计入统计，可以用撤销找回一次。",
                onConfirm = { confirmCount++ },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_IMPACT, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("删除后这条实际投入不再计入统计，可以用撤销找回一次。").assertIsDisplayed()

        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM, useUnmergedTree = true)
            .performClick()

        assertEquals("确认按钮必须真的执行一次", 1, confirmCount)
    }

    @Test
    fun confirmation_dialog_can_be_declined() {
        var dismissCount = 0
        mount {
            AttentionConfirmDialog(
                visible = true,
                title = "删除这条时间记录？",
                impact = "删除后不再计入统计。",
                onConfirm = {},
                onDismiss = { dismissCount++ },
            )
        }

        composeRule.onNodeWithTag(AttentionFeedbackTags.CONFIRM_DIALOG_DISMISS, useUnmergedTree = true)
            .performClick()

        assertEquals("取消必须真的执行一次", 1, dismissCount)
    }

    @Test
    fun unavailable_action_is_shown_as_disabled_with_a_reason_not_hidden() {
        mount {
            Column {
                AttentionDisabledAction(label = "确认完成", reason = "先选择一条日程条目才能确认完成")
                Text("下面的控件仍然可见")
            }
        }

        composeRule.onNodeWithTag(AttentionFeedbackTags.DISABLED_ACTION, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(AttentionFeedbackTags.DISABLED_ACTION_REASON, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("先选择一条日程条目才能确认完成").assertIsDisplayed()
    }

    @Test
    fun selected_state_is_expressed_with_a_mark_and_text_as_well_as_color() {
        mount { AttentionSelectedIndicator(selected = true, label = "本周") }

        composeRule.onNodeWithTag(AttentionFeedbackTags.SELECTED, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionFeedbackTags.SELECTED_MARK, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("已选中：本周").assertIsDisplayed()
    }

    @Test
    fun unselected_state_does_not_claim_to_be_selected() {
        mount { AttentionSelectedIndicator(selected = false, label = "本周") }

        composeRule.onNodeWithTag(AttentionFeedbackTags.SELECTED, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AttentionFeedbackTags.SELECTED_MARK, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun mount(content: @Composable () -> Unit) {
        composeRule.activity.setContent {
            AttentionTheme { content() }
        }
    }

    private fun waitForTag(tag: String): SemanticsNodeInteraction {
        composeRule.waitUntil("标识 $tag 必须在超时前出现", 5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return composeRule.onNodeWithTag(tag, useUnmergedTree = true)
    }

    private fun waitUntilCountReached(message: String, target: Int, count: () -> Int) {
        composeRule.waitUntil(message, 5_000L) { count() == target }
    }
}

@Composable
private fun PageBody() {
    Column(modifier = Modifier) { Text("页面内容") }
}
