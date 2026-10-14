package com.attention.app.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * 界面 seam：六种状态组件共用一套标识，状态从不只靠颜色表达。
 *
 * 断言只读用户能观察到的结果：标识、文案与点击后的效果，不读组件层级实现。
 */
@RunWith(AndroidJUnit4::class)
class AttentionStateComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun every_state_carries_both_an_icon_and_a_text_label() {
        // 一次只挂载一种状态：六种状态叠在一起会超出屏幕高度，
        // 那样断言的是滚动位置而不是状态本身。
        AttentionStateKind.entries.forEach { kind ->
            mount { stateOf(kind) }

            composeRule.onNodeWithTag(kind.iconTag, useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag(kind.labelTag, useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(kind.label, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun every_state_lives_in_one_shared_tag_namespace() {
        val prefixes = AttentionStateKind.entries.map { it.rootTag.substringBeforeLast(".") }.toSet()
        assertEquals("六种状态必须共用同一个标识命名空间", setOf("attention.state"), prefixes)

        AttentionStateKind.entries.forEach { kind ->
            mount { stateOf(kind) }
            composeRule.onNodeWithTag(kind.rootTag, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun the_same_state_on_two_screens_shares_one_tag_and_one_label() {
        mount { twoScreensRenderingTheSameErrorState() }

        composeRule.onAllNodesWithTag(AttentionStateKind.ERROR.rootTag, useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithTag(AttentionStateKind.ERROR.iconTag, useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithTag(AttentionStateKind.ERROR.labelTag, useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test
    fun error_state_offers_retry_and_runs_it() {
        var retryCount = 0
        mount { AttentionErrorState(onRetry = { retryCount++ }) }

        composeRule.onNodeWithTag(AttentionStateKind.ERROR.actionTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals("点击重试必须真正触发一次重试", 1, retryCount)
    }

    @Test
    fun empty_state_offers_a_next_step_and_runs_it() {
        var nextStepCount = 0
        mount { AttentionEmptyState(onNextStep = { nextStepCount++ }) }

        composeRule.onNodeWithTag(AttentionStateKind.EMPTY.actionTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assertEquals("空状态必须给出可执行的下一步", 1, nextStepCount)
    }

    @Test
    fun states_are_readable_without_relying_on_color() {
        AttentionStateKind.entries.forEach { kind ->
            mount { stateOf(kind) }
            composeRule.onNodeWithText(kind.label, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun permission_state_explains_the_effect_and_whether_it_is_required() {
        mount { AttentionPermissionState(required = true, onAction = {}) }

        composeRule.onNodeWithTag(AttentionStateKind.PERMISSION_NOT_GRANTED.labelTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionStateKind.PERMISSION_NOT_GRANTED.descriptionTag, useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithTag(AttentionStateKind.PERMISSION_NOT_GRANTED.actionTag, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun local_data_notice_says_where_the_data_stays() {
        mount { AttentionLocalDataNotice() }

        composeRule.onNodeWithTag(AttentionStateKind.LOCAL_DATA_NOTICE.labelTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AttentionStateKind.LOCAL_DATA_NOTICE.descriptionTag, useUnmergedTree = true)
            .assertExists()
    }

    private fun mount(content: @Composable () -> Unit) {
        composeRule.activity.setContent {
            AttentionTheme {
                Column { content() }
            }
        }
    }
}

/** 同一个组件放在两个不同的“页面”容器里，模拟跨页面复用。 */
@Composable
private fun twoScreensRenderingTheSameErrorState() {
    Column {
        AttentionErrorState()
        Column { AttentionErrorState() }
    }
}

/** 按种类渲染对应的那一个状态组件。 */
@Composable
private fun stateOf(kind: AttentionStateKind) {
    when (kind) {
        AttentionStateKind.LOADING -> AttentionLoadingState()
        AttentionStateKind.SUCCESS -> AttentionSuccessState()
        AttentionStateKind.ERROR -> AttentionErrorState(onRetry = {})
        AttentionStateKind.EMPTY -> AttentionEmptyState(onNextStep = {})
        AttentionStateKind.PERMISSION_NOT_GRANTED -> AttentionPermissionState(required = true, onAction = {})
        AttentionStateKind.LOCAL_DATA_NOTICE -> AttentionLocalDataNotice()
    }
}
