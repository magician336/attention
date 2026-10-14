package com.attention.app.settings

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.SettingsScreenState
import com.attention.workspace.state.settingsScreenState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中状态与发出的 intent，不经过 ViewModel 与 Room。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

    private fun screenState(launchDestination: LaunchDestination): SettingsScreenState = settingsScreenState(
        state = AttentionState(launchDestination = launchDestination),
        now = Instant.parse("2026-03-05T10:00:00Z"),
        zone = ZoneId.of("Asia/Shanghai"),
    )

    private fun mount(
        state: SettingsScreenState,
        actions: SettingsActions = RecordingSettingsActions(mutableListOf()),
    ) {
        composeRule.activity.setContent {
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
