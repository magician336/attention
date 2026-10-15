package com.attention.app.settings

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.LaunchDestination
import com.attention.workspace.state.LaunchDestinationOption
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.SettingsScreenState
import com.attention.workspace.state.SettingsWeekDayOption
import com.attention.workspace.state.weekDayLabel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中状态与发出的 intent，不经过 ViewModel 与 Room。
 *
 * 设置页是启动入口、规划日界线、周起始日、每日容量、通知与备份数据的唯一编辑入口，
 * 因此这里逐项钉住「用户看得到、点得动、改得动」。
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
        // 启动入口区块的说明文字与四项入口标签保持可见
        mount(state = screenState(LaunchDestination.TARGETS))

        node("设置").assertIsDisplayed()
        node("启动入口").assertIsDisplayed()
        node("应用下次启动时打开的页面").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-AGENDA").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-TARGETS").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-STATISTICS").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-LAST_OPENED").assertIsDisplayed()
    }

    @Test
    fun the_launch_destination_labels_are_agenda_targets_statistics_and_last_opened() {
        // 启动选项对用户统一呈现为日程、目标树、统计、上次打开。
        // 按标识逐项断言按钮上的文字：选中的那一项带「（当前）」后缀，所以按包含匹配。
        mount(state = screenState(LaunchDestination.TARGETS))

        listOf(
            LaunchDestination.AGENDA to "日程",
            LaunchDestination.TARGETS to "目标树",
            LaunchDestination.STATISTICS to "统计",
            LaunchDestination.LAST_OPENED to "上次打开",
        ).forEach { (destination, label) ->
            composeRule.onNodeWithTag("launch-destination-$destination")
                .performScrollTo()
                .assert(hasText(label, substring = true))
        }
        // 旧入口值不再作为选项出现：用户看到的只有上面四项
        composeRule.onNodeWithText("今天").assertDoesNotExist()
        composeRule.onNodeWithText("所有日期").assertDoesNotExist()
    }

    @Test
    fun selected_entry_is_marked_as_current_and_the_others_stay_selectable() {
        // 当前入口显示为「（当前）」，其余入口保持普通标签
        mount(state = screenState(LaunchDestination.TARGETS))

        node("目标树（当前）").assertIsDisplayed()
        node("日程").assertIsDisplayed()
        node("上次打开").assertIsDisplayed()
    }

    @Test
    fun last_opened_can_be_the_selected_entry() {
        // 「上次打开」本身也可以是当前入口
        mount(state = screenState(LaunchDestination.LAST_OPENED))

        node("上次打开（当前）").assertIsDisplayed()
    }

    @Test
    fun clicking_another_entry_emits_the_select_intent() {
        // 选择其它入口只发出 intent，不直接落地
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("launch-destination-STATISTICS").performClick()

        assertEquals(listOf(LaunchDestination.STATISTICS), intents.launchDestinations)
    }

    @Test
    fun clicking_the_current_entry_emits_no_intent() {
        // 当前入口不可再次选择，因此不发出 intent
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("launch-destination-TARGETS").performClick()

        assertEquals(emptyList<LaunchDestination>(), intents.launchDestinations)
    }

    @Test
    fun planning_day_boundary_capacity_and_week_start_are_editable_here() {
        // #172 下线今天页的每日容量卡片之后，规划日界线、周起始日与每日容量的编辑入口只在设置页
        mount(state = screenState(LaunchDestination.TARGETS))

        node("规划日与容量").assertIsDisplayed()
        node("规划日界线").assertIsDisplayed()
        node("每日容量").assertIsDisplayed()
        node("周起始日").assertIsDisplayed()
        composeRule.onNodeWithTag("planning-boundary-input").assertEditableTextIs("180")
        composeRule.onNodeWithTag("daily-capacity-input").assertEditableTextIs("240")
        listOf(1, 2, 3, 4, 5, 6, 7).forEach { day ->
            composeRule.onNodeWithTag("settings-week-$day").assertExists()
        }
    }

    @Test
    fun saving_the_planning_day_settings_emits_the_typed_intent() {
        // 保存把界线的分钟数与容量的分钟数（空则清除）一起发出
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("planning-boundary-input").performTextReplacement("60")
        composeRule.onNodeWithTag("daily-capacity-input").performTextReplacement("300")
        composeRule.onNodeWithTag("planning-settings-save").performScrollTo().performClick()

        assertEquals(listOf(PlanningSettings(60, 300)), intents.planningSettings)
    }

    @Test
    fun an_empty_capacity_clears_it_and_an_invalid_value_saves_nothing() {
        // 每日容量可留空（清除）；非正整数时报错并且不发出任何 intent
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("daily-capacity-input").performTextReplacement("")
        composeRule.onNodeWithTag("planning-settings-save").performScrollTo().performClick()
        assertEquals(listOf(PlanningSettings(180, null)), intents.planningSettings)

        composeRule.onNodeWithTag("daily-capacity-input").performTextReplacement("0")
        composeRule.onNodeWithTag("planning-settings-save").performScrollTo().performClick()

        node("每日容量必须是正整数，或留空清除").assertIsDisplayed()
        assertEquals(listOf(PlanningSettings(180, null)), intents.planningSettings)
    }

    @Test
    fun choosing_a_week_start_day_emits_the_week_start_intent() {
        // 周起始日是独立的一条公开设置命令，页面只发出它的序号
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("settings-week-6").performScrollTo().performClick()

        assertEquals(listOf(6), intents.weekStarts)
    }

    @Test
    fun an_available_notification_channel_is_reported_instead_of_a_request_button() {
        // 通知已开启时只报告状态，不再给一个没有意义的申请入口
        mount(state = screenState(LaunchDestination.TARGETS, notificationsEnabled = true))

        node("通知").assertIsDisplayed()
        node("通知已开启。").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-request-notification").assertDoesNotExist()
    }

    @Test
    fun the_notification_permission_can_be_requested_from_here() {
        // 通知不可用时给出说明与申请入口；权限申请由组合根执行
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS, notificationsEnabled = false), actions = intents)

        node("通知").assertIsDisplayed()
        node("通知未开启，日程提醒不会弹出。").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-request-notification").performScrollTo().performClick()

        assertEquals(1, intents.notificationRequests)
    }

    @Test
    fun backup_and_restore_entries_emit_their_intents() {
        // 备份与恢复集中在设置页，workflow 仍然由组合根完成（预览、确认对话框与 SAF 出口不变）
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        node("备份数据").assertIsDisplayed()
        node("预览 JSON 备份").performClick()
        node("导入 JSON").performClick()
        node("导出时间记录 CSV").performClick()
        node("预览 CSV").performClick()
        node("导出日程 CSV").performClick()
        node("导出两个 CSV 文件").performClick()
        node("恢复最近一次导入前备份").performClick()

        assertEquals(
            listOf(
                "previewJson",
                "importJson",
                "exportTimeEntries",
                "previewCsv",
                "exportSchedules",
                "exportCsvBundle",
                "restoreImportBackup",
            ),
            intents.dataIntents,
        )
    }

    @Test
    fun going_back_returns_to_the_statistics_page() {
        // 设置页是统计页底部的入口，返回动作回到统计页
        val intents = RecordingSettingsActions()
        mount(state = screenState(LaunchDestination.TARGETS), actions = intents)

        composeRule.onNodeWithTag("settings-back").performScrollTo().performClick()

        assertEquals(1, intents.closed)
    }

    /** 设置页是可滚动长页，断言与点击前先滚到目标节点。 */
    private fun node(text: String) = composeRule.onNodeWithText(text).performScrollTo()

    /**
     * 输入框的初值来自注入的 screen state。读 [SemanticsProperties.EditableText] 而不是找文案节点：
     * 同一个节点上还有 label 文案，按可见文案断言会把 label 一起算进来。
     */
    private fun SemanticsNodeInteraction.assertEditableTextIs(expected: String) {
        val actual = fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertEquals("输入框的初值必须是 '$expected'，实际是 '$actual'", expected, actual)
    }

    /** 手写的 fake screen state：标签与选中态由测试自己钉住，不经过投影。 */
    private fun screenState(
        selected: LaunchDestination,
        notificationsEnabled: Boolean = true,
    ): SettingsScreenState = SettingsScreenState(
        options = listOf(
            LaunchDestinationOption(LaunchDestination.AGENDA, "日程", selected == LaunchDestination.AGENDA),
            LaunchDestinationOption(LaunchDestination.TARGETS, "目标树", selected == LaunchDestination.TARGETS),
            LaunchDestinationOption(LaunchDestination.STATISTICS, "统计", selected == LaunchDestination.STATISTICS),
            LaunchDestinationOption(
                LaunchDestination.LAST_OPENED,
                "上次打开",
                selected == LaunchDestination.LAST_OPENED,
            ),
        ),
        selectedDestination = selected,
        resolvedDestination = selected,
        planningDay = LocalDate.parse("2026-03-05"),
        planningDayBoundaryMinutes = 180,
        weekDayOptions = (1..7).map { day ->
            SettingsWeekDayOption(day, weekDayLabel(day), day == 1)
        },
        dailyCapacityMinutes = 240,
        notificationsEnabled = notificationsEnabled,
    )

    private fun mount(state: SettingsScreenState, actions: SettingsActions = RecordingSettingsActions()) {
        composeRule.setContent {
            AttentionTheme { SettingsScreen(state = state, actions = actions) }
        }
    }

    /** 一次保存规划设置的 intent，用独立于 actions 接口的值类型记录，便于断言。 */
    private data class PlanningSettings(val boundaryMinutes: Int, val dailyCapacityMinutes: Int?)

    private class RecordingSettingsActions : SettingsActions {
        val launchDestinations = mutableListOf<LaunchDestination>()
        val planningSettings = mutableListOf<PlanningSettings>()
        val weekStarts = mutableListOf<Int>()
        val dataIntents = mutableListOf<String>()
        var notificationRequests = 0
        var closed = 0

        override fun selectLaunchDestination(destination: LaunchDestination) {
            launchDestinations += destination
        }

        override fun savePlanningDaySettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) {
            planningSettings += PlanningSettings(boundaryMinutes, dailyCapacityMinutes)
        }

        override fun selectWeekStartDay(day: Int) {
            weekStarts += day
        }

        override fun requestNotificationPermission() {
            notificationRequests++
        }

        override fun previewJsonBackup() {
            dataIntents += "previewJson"
        }

        override fun importJson() {
            dataIntents += "importJson"
        }

        override fun exportTimeEntriesCsv() {
            dataIntents += "exportTimeEntries"
        }

        override fun previewCsv() {
            dataIntents += "previewCsv"
        }

        override fun exportSchedulesCsv() {
            dataIntents += "exportSchedules"
        }

        override fun exportCsvBundle() {
            dataIntents += "exportCsvBundle"
        }

        override fun restoreLastImportBackup() {
            dataIntents += "restoreImportBackup"
        }

        override fun close() {
            closed++
        }
    }
}
