package com.attention.app.statistics

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Milestone
import com.attention.domain.Migration
import com.attention.domain.PeriodSnapshot
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.workspace.state.StatisticsActions
import com.attention.workspace.state.StatisticsScreenState
import com.attention.workspace.state.statisticsScreenState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 统计页的直接测试：只给它 fake screen state 与 fake actions，
 * 断言可见标签、选中周期、结算 intent 与导入导出 intent，不经过 ViewModel 与 Room。
 */
@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val now: Instant = Instant.parse("2026-03-05T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val selected: LocalDate = LocalDate.of(2026, 3, 5)

    @Test
    fun the_page_shows_the_projected_period_range_and_totals() {
        // 周期范围、实际/目标/缺口/超额、未归属与迁移都来自投影
        mount(screenState())

        node("统计与成长").assertIsDisplayed()
        node("2026-03-05 至 2026-03-05").assertIsDisplayed()
        node("实际 90 分钟 · 目标 120 分钟 · 缺口 30 分钟 · 超额 0 分钟").assertIsDisplayed()
        node("未归属活动：30 分钟").assertIsDisplayed()
        node("迁移目标：25 分钟").assertIsDisplayed()
    }

    @Test
    fun the_page_shows_snapshot_and_milestone_lines_from_the_projection() {
        // 快照、累计投入里程碑与最近里程碑的每一行都由投影给出
        mount(screenState())

        node("历史周期快照").assertIsDisplayed()
        node("写书 · 每日 2026-03-04～2026-03-04：80/60 分钟 · 达成").assertIsDisplayed()
        node("累计投入里程碑").assertIsDisplayed()
        node("累计投入里程碑：100 小时 · +10000 投入经验").assertIsDisplayed()
        node("里程碑 cumulative：+10000（cumulative:100）").assertIsDisplayed()
        node("里程碑 one_time_stage：+30（one_time:s1:2026-03-05）").assertIsDisplayed()
    }

    @Test
    fun the_page_shows_target_investment_and_level_lines() {
        // 目标树投入与等级进度逐行来自投影，归档目标带「（已归档）」
        mount(screenState())

        node("目标树投入", substring = true).assertIsDisplayed()
        node("写书：60 分钟 · 直接投入 60 分钟 · 子树汇总 60 分钟").assertIsDisplayed()
        node("旧计划（已归档）：0 分钟 · 直接投入 0 分钟 · 子树汇总 0 分钟").assertIsDisplayed()
        node("投入经验：1200 · 等级 2").assertIsDisplayed()
        node("当前等级区间进度：200/2000 点").assertIsDisplayed()
    }

    @Test
    fun the_page_shows_stage_progress_from_the_projection() {
        // 阶段进度行与一次性阶段汇总行由投影给出
        mount(screenState())

        node("一次性阶段汇总：实际 60/300 分钟 · 缺口 240 分钟 · 超额投入 0 分钟 · 未达成").assertIsDisplayed()
        node("每日目标 60/120 分钟 · 周期 2026-03-05 至 2026-03-05 · 缺口 60 分钟").assertIsDisplayed()
        node("一次性目标 60/300 分钟 · 范围 2026-03-05 至 2026-03-05 · 开放 · 缺口 240 分钟").assertIsDisplayed()
    }

    @Test
    fun an_empty_state_tells_the_user_what_will_show_up() {
        // 没有目标时给出「创建计划后」的引导语与空的快照提示
        mount(screenState(state = AttentionState()))

        node("还没有已保存的周期快照。").assertIsDisplayed()
        node("还没有累计投入里程碑。").assertIsDisplayed()
        node("创建计划后，这里会显示目标投入和阶段达成情况。").assertIsDisplayed()
    }

    @Test
    fun the_selected_cadence_is_the_projected_selection() {
        // 每日是当前选中的周期粒度；点它不再发出 intent
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("每日").assertIsDisplayed()
        node("每日").performClick()

        assertEquals(emptyList<String>(), intents.periodShifts)
    }

    @Test
    fun changing_the_cadence_emits_the_cadence_intent() {
        // 切换周期粒度只发出 intent
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("每周").performClick()

        assertEquals(listOf(GoalCadence.WEEKLY), intents.cadences)
    }

    @Test
    fun previous_and_next_period_emit_the_period_shift_intents() {
        // 上一/下一周期只发出位移 intent，位移由组合根算
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("上一周期").performClick()
        node("下一周期").performClick()

        assertEquals(listOf("previous", "next"), intents.periodShifts)
    }

    @Test
    fun settling_snapshots_and_milestones_emits_its_own_intents() {
        // 结算周期快照与结算可达成里程碑各自一个 intent
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("结算已结束周期").performClick()
        node("结算可达成里程碑").performClick()

        assertEquals(1, intents.settleSnapshots)
        assertEquals(1, intents.awardedMilestones)
    }

    @Test
    fun cancelling_a_visible_migration_emits_its_id() {
        // 取消只带可见迁移的 id
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("主动迁移 25 分钟 · 2026-03-05").assertIsDisplayed()
        node("取消").performClick()

        assertEquals(listOf("m1"), intents.cancelledMigrations)
    }

    @Test
    fun picking_a_launch_destination_and_restoring_the_backup_emit_intents() {
        // 首屏入口与恢复备份都只发出 intent
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("首屏入口", substring = true).assertIsDisplayed()
        node("目标树").performClick()
        node("恢复最近一次导入前备份").performClick()

        assertEquals(listOf(com.attention.domain.LaunchDestination.TARGETS), intents.launchDestinations)
        assertEquals(1, intents.restoredBackups)
    }

    @Test
    fun export_and_import_buttons_only_emit_intents() {
        // 可点击的导入导出按钮各发一个 intent，不触碰 SAF
        val intents = RecordingStatisticsActions()
        mount(screenState(), intents)

        node("预览 JSON 备份").performClick()
        node("导入 JSON").performClick()
        node("导出时间记录 CSV").performClick()

        assertEquals(listOf("previewJson", "importJson", "exportTimeEntries"), intents.dataIntents)
    }

    @Test
    fun every_export_button_is_rendered_by_the_page() {
        // 六个导入导出按钮都由页面渲染；后三个在窄屏上横向溢出，只能断言存在
        mount(screenState())

        listOf("预览 JSON 备份", "导入 JSON", "导出时间记录 CSV", "预览 CSV", "导出日程 CSV", "导出两个 CSV 文件")
            .forEach { label -> composeRule.onNodeWithText(label).assertExists() }
    }

    /** 统计页是可滚动长页，断言与点击前先滚到目标节点。 */
    private fun node(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring).performScrollTo()

    private fun screenState(
        state: AttentionState = AttentionState(
            targets = listOf(
                Target(id = "t1", title = "写书"),
                Target(id = "t2", title = "旧计划", archived = true),
            ),
            goalStages = listOf(
                GoalStage(id = "s1", targetId = "t1", cadence = GoalCadence.DAILY, targetMinutes = 120, startDate = "2026-03-05"),
                GoalStage(id = "s2", targetId = "t1", cadence = GoalCadence.ONE_TIME, targetMinutes = 300, startDate = "2026-03-05"),
            ),
            timeEntries = listOf(
                TimeEntry(planningDate = "2026-03-05", durationMinutes = 60, targetId = "t1"),
                TimeEntry(planningDate = "2026-03-05", durationMinutes = 30, targetId = null),
            ),
            migrations = listOf(
                Migration(id = "m1", targetId = "t1", sourceStageId = "s0", minutes = 25, destinationStartDate = "2026-03-05"),
            ),
            periodSnapshots = listOf(
                PeriodSnapshot(
                    id = "p1",
                    targetId = "t1",
                    cadence = GoalCadence.DAILY,
                    periodStart = "2026-03-04",
                    periodEnd = "2026-03-04",
                    targetMinutes = 60,
                    actualMinutes = 80,
                    gapMinutes = 0,
                    excessMinutes = 20,
                    completed = true,
                ),
            ),
            milestones = listOf(
                Milestone(id = "ms1", kind = "cumulative", instanceKey = "cumulative:100", reward = 10_000, achievedAtEpochMillis = 20),
                Milestone(id = "ms2", kind = "one_time_stage", instanceKey = "one_time:s1:2026-03-05", reward = 30, achievedAtEpochMillis = 30),
            ),
            experience = 1200,
        ),
        cadence: GoalCadence = GoalCadence.DAILY,
    ): StatisticsScreenState = statisticsScreenState(
        state = state,
        now = now,
        zone = zone,
        selectedDate = selected,
        cadence = cadence,
    )

    private fun mount(
        state: StatisticsScreenState,
        actions: StatisticsActions = RecordingStatisticsActions(),
    ) {
        composeRule.activity.setContent {
            AttentionTheme { StatisticsScreen(state = state, actions = actions) }
        }
    }

    private class RecordingStatisticsActions : StatisticsActions {
        val periodShifts = mutableListOf<String>()
        val cadences = mutableListOf<GoalCadence>()
        val cancelledMigrations = mutableListOf<String>()
        val launchDestinations = mutableListOf<com.attention.domain.LaunchDestination>()
        val dataIntents = mutableListOf<String>()
        var settleSnapshots = 0
        var awardedMilestones = 0
        var restoredBackups = 0

        override fun selectPreviousPeriod() {
            periodShifts += "previous"
        }

        override fun selectNextPeriod() {
            periodShifts += "next"
        }

        override fun selectCadence(cadence: GoalCadence) {
            cadences += cadence
        }

        override fun settlePeriodSnapshots() {
            settleSnapshots++
        }

        override fun cancelMigration(migrationId: String) {
            cancelledMigrations += migrationId
        }

        override fun awardMilestones() {
            awardedMilestones++
        }

        override fun selectLaunchDestination(destination: com.attention.domain.LaunchDestination) {
            launchDestinations += destination
        }

        override fun restoreLastImportBackup() {
            restoredBackups++
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
    }
}