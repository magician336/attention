package com.attention.app.settings

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.LaunchDestination
import com.attention.workspace.state.LaunchDestinationOption
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.SettingsScreenState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中状态与发出的 intent，不经过 ViewModel 与 Room。
 *
 * screen state 是手写的，不走 settingsScreenState 投影：页面测试只钉住页面自己要渲染的
 * 标签与选中态，投影本身的规则由 workspace-state 的纯 Kotlin 测试负责。页面版本因此不需要
 * Activity 宿主，用 [createComposeRule] 挂载即可，也不会把平台副作用拉起来。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launch_destination_section_lists_every_entry() {
        // 启动入口区块的说明文字与全部入口标签保持可见
        mount(state = screenState(LaunchDestination.TARGETS))

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("启动入口").assertIsDisplayed()
        composeRule.onNodeWithText("应用下次启动时打开的页面").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-TODAY").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-TARGETS").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-ALL_DATES").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-STATISTICS").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-LAST_OPENED").assertIsDisplayed()
    }

    @Test
    fun selected_entry_is_marked_as_current_and_the_others_stay_selectable() {
        // 当前入口显示为「（当前）」，其余入口保持普通标签
        mount(state = screenState(LaunchDestination.TARGETS))

        composeRule.onNodeWithText("目标树（当前）").assertIsDisplayed()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("上次打开").assertIsDisplayed()
    }

    @Test
    fun last_opened_can_be_the_selected_entry() {
        // 「上次打开」本身也可以是当前入口
        mount(state = screenState(LaunchDestination.LAST_OPENED))

        composeRule.onNodeWithText("上次打开（当前）").assertIsDisplayed()
    }

    @Test
    fun clicking_another_entry_emits_the_select_intent() {
        // 选择其它入口只发出 intent，不直接落地
        val intents = mutableListOf<LaunchDestination>()
        mount(
            state = screenState(LaunchDestination.TARGETS),
            actions = RecordingSettingsActions(intents),
        )

        composeRule.onNodeWithTag("launch-destination-STATISTICS").performClick()

        assertEquals(listOf(LaunchDestination.STATISTICS), intents)
    }

    @Test
    fun clicking_the_current_entry_emits_no_intent() {
        // 当前入口不可再次选择，因此不发出 intent
        val intents = mutableListOf<LaunchDestination>()
        mount(
            state = screenState(LaunchDestination.TARGETS),
            actions = RecordingSettingsActions(intents),
        )

        composeRule.onNodeWithTag("launch-destination-TARGETS").performClick()

        assertEquals(emptyList<LaunchDestination>(), intents)
    }

    /** 手写的 fake screen state：标签与选中态由测试自己钉住，不经过投影。 */
    private fun screenState(selected: LaunchDestination): SettingsScreenState = SettingsScreenState(
        options = listOf(
            LaunchDestinationOption(LaunchDestination.TODAY, "今天", selected == LaunchDestination.TODAY),
            LaunchDestinationOption(LaunchDestination.TARGETS, "目标树", selected == LaunchDestination.TARGETS),
            LaunchDestinationOption(LaunchDestination.ALL_DATES, "所有日期", selected == LaunchDestination.ALL_DATES),
            LaunchDestinationOption(LaunchDestination.STATISTICS, "统计", selected == LaunchDestination.STATISTICS),
            LaunchDestinationOption(LaunchDestination.LAST_OPENED, "上次打开", selected == LaunchDestination.LAST_OPENED),
        ),
        selectedDestination = selected,
        resolvedDestination = selected,
        planningDay = LocalDate.parse("2026-03-05"),
    )

    private fun mount(
        state: SettingsScreenState,
        actions: SettingsActions = RecordingSettingsActions(mutableListOf()),
    ) {
        composeRule.setContent {
            AttentionTheme { SettingsScreen(state = state, actions = actions) }
        }
    }

    private class RecordingSettingsActions(
        private val intents: MutableList<LaunchDestination>,
    ) : SettingsActions {
        override fun selectLaunchDestination(destination: LaunchDestination) {
            intents += destination
        }
    }
}
