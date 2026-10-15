package com.attention.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.agenda.AgendaScreen
import com.attention.app.settings.SettingsScreen
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.LaunchDestination
import com.attention.domain.ScheduleEntry
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaWorkbenchSummary
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
 * #179 验收标准 5：平台副作用通过组合根 adapter 的可替换 seam 验证，而不是靠真实设备副作用。
 *
 * 计时器、提醒权限、通知权限、SAF 备份与恢复都属于平台副作用。它们全部由组合根 adapter 执行，
 * 页面只发出 intent（ADR-0005 第 4 节）。因此「页面行为保持不变」这件事可以在没有 Activity、
 * 没有权限对话框、没有文件选择器、没有前台服务的情况下验证：把 adapter 换成记录器即可。
 *
 * 逐页的既有验收用例（AgendaScreenTest.a_reminder_in_the_add_form_asks_for_permission_as_an_intent、
 * SettingsScreenTest.the_notification_permission_can_be_requested_from_here、
 * SettingsScreenTest.backup_and_restore_entries_emit_their_intents、
 * AgendaScreenTest.starting_a_timer_from_an_entry_keeps_the_entry_as_the_source）钉住各自的 intent；
 * 这条用例把跨页的 seam 收在一处：两个页面并排挂载，各自的平台副作用仍然只是一个可替换的 intent，
 * 换成记录器之后用户可见行为一字不变，而设备上没有任何副作用真的发生。
 */
@RunWith(AndroidJUnit4::class)
class WorkspacePlatformSideEffectSeamTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun every_platform_side_effect_the_navigation_touched_stays_a_replaceable_intent() {
        val agenda = RecordingAgendaActions()
        // 日程页单独挂载：两个整屏页面叠在同一个组合里，后挂载的会盖住先挂载的，
        // 点击坐标就会落到另一页上，因此每条用例只挂一个页面。
        composeRule.setContent {
            AttentionTheme { AgendaScreen(state = agendaScreenState(), actions = agenda) }
        }

        // 提醒权限：页面只发出申请意图，权限对话框由组合根 adapter 负责
        // 添加日程的表单住在底部面板里：先通过工作台的 `+` 菜单把它打开，
        // 提醒属于低频字段，因此还要展开「更多设置」。
        composeRule.onNodeWithTag("agenda-quick-add", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("agenda-quick-add-schedule", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-title-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("带提醒的日程")
        composeRule.onNodeWithTag("agenda-add-more-toggle", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("agenda-reminder-input", useUnmergedTree = true).performScrollTo()
            .performTextReplacement("09:00")
        composeRule.onNodeWithTag("agenda-add-submit", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(540, agenda.addedSchedules.single().reminderMinuteOfDay)
        assertEquals(1, agenda.permissionRequests)

        // 计时器：页面只发出带来源关联的 intent，前台服务由组合根 adapter 启动
        composeRule.onNodeWithTag("schedule-start-timer-e1", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("e1"), agenda.startedTimerEntries.map { it.id })
    }

    @Test
    fun the_notification_permission_and_the_backup_workflow_are_intents_from_the_settings_page() {
        val settings = RecordingSettingsActions()
        composeRule.setContent {
            AttentionTheme { SettingsScreen(state = settingsScreenState(notificationsEnabled = false), actions = settings) }
        }

        // 通知不可用提示：状态由组合根注入，页面只按它渲染并发出申请意图
        composeRule.onNodeWithText("通知未开启，日程提醒不会弹出。").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-request-notification").performScrollTo().performClick()
        assertEquals(1, settings.notificationRequests)

        // 备份与恢复：六个 SAF 入口与恢复入口全部只发出意图，文件选择器不在这条路径上
        composeRule.onNodeWithText("预览 JSON 备份").performScrollTo().performClick()
        composeRule.onNodeWithText("导入 JSON").performScrollTo().performClick()
        composeRule.onNodeWithText("导出时间记录 CSV").performScrollTo().performClick()
        composeRule.onNodeWithText("预览 CSV").performScrollTo().performClick()
        composeRule.onNodeWithText("导出日程 CSV").performScrollTo().performClick()
        composeRule.onNodeWithText("导出两个 CSV 文件").performScrollTo().performClick()
        composeRule.onNodeWithText("恢复最近一次导入前备份").performScrollTo().performClick()

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
            settings.dataIntents,
        )
    }

    /** 手写的日程页 screen state：只钉住本用例要点的标签与行，不走投影。 */
    private fun agendaScreenState(): AgendaScreenState {
        val day = LocalDate.parse("2026-03-05")
        val entry = ScheduleEntry("e1", day.toString(), "UI-条目", estimatedMinutes = 30, targetId = "t1")
        val row = AgendaScheduleRow(
            entry = entry,
            title = entry.title,
            detailLabel = "预计 30 分钟",
            note = "",
            startText = "",
            endText = "",
            estimateText = "",
            reminderText = "未设置",
            targetId = "t1",
            canStartTimer = true,
        )
        return AgendaScreenState(
            planningDate = day,
            selectedDate = day,
            headingLabel = "规划日 $day",
            filterLabel = "规划日 $day",
            availableDates = emptyList(),
            selectedCapacityLabel = null,
            workbenchSummary = AgendaWorkbenchSummary(
                planningDateLabel = "规划日 $day",
                boundaryLabel = "规划日界线 03:00",
                weekStartLabel = "周起始日 周一",
                capacitySettingLabel = "每日容量未设置",
                capacityUsageLabel = null,
            ),
            timelineRows = listOf(row),
            dateSections = emptyList(),
            emptyLabel = null,
            recurrenceRules = emptyList(),
            targets = listOf(AgendaTargetOption("t1", "UI-目标")),
            pendingEditRow = null,
        )
    }

    /** 手写的设置页 screen state：通知按不可用注入，用来断言提示与申请意图。 */
    private fun settingsScreenState(notificationsEnabled: Boolean = true): SettingsScreenState = SettingsScreenState(
        options = listOf(
            LaunchDestinationOption(LaunchDestination.AGENDA, "日程", true),
            LaunchDestinationOption(LaunchDestination.TARGETS, "目标树", false),
            LaunchDestinationOption(LaunchDestination.STATISTICS, "统计", false),
            LaunchDestinationOption(LaunchDestination.LAST_OPENED, "上次打开", false),
        ),
        selectedDestination = LaunchDestination.AGENDA,
        resolvedDestination = LaunchDestination.AGENDA,
        planningDay = LocalDate.parse("2026-03-05"),
        planningDayBoundaryMinutes = 180,
        weekDayOptions = (1..7).map { day -> SettingsWeekDayOption(day, weekDayLabel(day), day == 1) },
        dailyCapacityMinutes = 240,
        notificationsEnabled = notificationsEnabled,
    )

    /** 组合根 adapter 的替身：只记录 intent，不触达任何平台 API。 */
    private class RecordingAgendaActions : AgendaActions {
        val addedSchedules = mutableListOf<AgendaScheduleDraft>()
        val startedTimerEntries = mutableListOf<ScheduleEntry>()
        val startedFreeTimerTargets = mutableListOf<String?>()
        var permissionRequests = 0

        override fun selectPlanningDate(date: LocalDate?) = Unit
        override fun selectAllDates() = Unit
        override fun shiftPlanningDay(days: Long) = Unit
        override fun addSchedule(draft: AgendaScheduleDraft) { addedSchedules += draft }
        override fun updateSchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) = Unit
        override fun confirmScheduleCompleted(entryId: String) = Unit
        override fun toggleScheduleCompleted(entryId: String) = Unit
        override fun startTimerFromEntry(entry: ScheduleEntry) { startedTimerEntries += entry }
        override fun startFreeTimer(targetId: String?) { startedFreeTimerTargets += targetId }
        override fun stopRunningTimer() = Unit
        override fun pauseRunningTimer() = Unit
        override fun resumeRunningTimer() = Unit
        override fun recoverRunningTimerFromNow() = Unit
        override fun switchRunningTimerTarget(targetId: String?) = Unit
        override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) = Unit
        override fun deleteTime(entryId: String) = Unit
        override fun addRecurrenceAndMaterialize(draft: AgendaRecurrenceDraft) = Unit
        override fun stopRecurrence(ruleId: String) = Unit
        override fun requestReminderPermission() { permissionRequests += 1 }
        override fun retryLoad() = Unit
        override fun openSettings() = Unit
    }

    /** 设置页 adapter 的替身：同样只记录 intent，不触达 SAF、权限与 ViewModel。 */
    private class RecordingSettingsActions : SettingsActions {
        val dataIntents = mutableListOf<String>()
        var notificationRequests = 0

        override fun selectLaunchDestination(destination: LaunchDestination) = Unit
        override fun savePlanningDaySettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) = Unit
        override fun selectWeekStartDay(day: Int) = Unit
        override fun requestNotificationPermission() { notificationRequests += 1 }
        override fun previewJsonBackup() { dataIntents += "previewJson" }
        override fun importJson() { dataIntents += "importJson" }
        override fun exportTimeEntriesCsv() { dataIntents += "exportTimeEntries" }
        override fun previewCsv() { dataIntents += "previewCsv" }
        override fun exportSchedulesCsv() { dataIntents += "exportSchedules" }
        override fun exportCsvBundle() { dataIntents += "exportCsvBundle" }
        override fun restoreLastImportBackup() { dataIntents += "restoreImportBackup" }
        override fun openOnboarding() = Unit
        override fun close() = Unit
    }
}
