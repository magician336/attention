package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Migration
import com.attention.domain.PeriodSnapshot
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 统计页投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
 */
class StatisticsScreenStateTest {

    private val now: Instant = Instant.parse("2026-03-05T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val selected = LocalDate.of(2026, 3, 5)

    private fun project(
        state: AttentionState = AttentionState(),
        instant: Instant = now,
        zone: ZoneId = this.zone,
        selectedDate: LocalDate? = selected,
        cadence: GoalCadence = GoalCadence.DAILY,
    ) = statisticsScreenState(
        state = state,
        now = instant,
        zone = zone,
        selectedDate = selectedDate,
        cadence = cadence,
    )

    @Test
    fun daily_cadence_covers_only_the_selected_planning_day() {
        // 每日周期的范围就是选中的那一个规划日
        val screenState = project(cadence = GoalCadence.DAILY)

        assertEquals("2026-03-05 至 2026-03-05", screenState.periodRangeLabel)
        assertEquals(GoalCadence.DAILY, screenState.cadence)
        assertEquals(
            listOf("每日", "每周", "每月"),
            screenState.cadenceOptions.map { it.label },
        )
        assertEquals(listOf("每日"), screenState.cadenceOptions.filter { it.selected }.map { it.label })
    }

    @Test
    fun weekly_cadence_starts_at_the_configured_week_start_day() {
        // 每周周期从设置里的周起始日（默认周一）开始
        val screenState = project(cadence = GoalCadence.WEEKLY)

        assertEquals("2026-03-02 至 2026-03-08", screenState.periodRangeLabel)
    }

    @Test
    fun weekly_cadence_follows_a_changed_week_start_day() {
        // 周起始日改成周日时，周期范围从周日开始
        val screenState = project(
            state = AttentionState(settings = StoredSettings(weekStartDay = 7)),
            cadence = GoalCadence.WEEKLY,
        )

        assertEquals("2026-03-01 至 2026-03-07", screenState.periodRangeLabel)
    }

    @Test
    fun monthly_cadence_covers_the_whole_calendar_month() {
        // 每月周期覆盖自然月：3 月 5 日落在 3 月 1 日到 3 月 31 日
        val screenState = project(cadence = GoalCadence.MONTHLY)

        assertEquals("2026-03-01 至 2026-03-31", screenState.periodRangeLabel)
    }

    @Test
    fun totals_line_reports_actual_target_gap_and_excess() {
        // 实际 90 / 目标 120 分钟时缺口 30、超额 0；再多的投入转成超额
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书")),
            timeEntries = listOf(TimeEntry(planningDate = "2026-03-05", durationMinutes = 90, targetId = "t1")),
            goalStages = listOf(GoalStage(id = "s1", targetId = "t1", cadence = GoalCadence.DAILY, targetMinutes = 120, startDate = "2026-03-05")),
        )

        assertEquals("实际 90 分钟 · 目标 120 分钟 · 缺口 30 分钟 · 超额 0 分钟", project(state).totalsLabel)
        assertEquals(
            "实际 150 分钟 · 目标 120 分钟 · 缺口 0 分钟 · 超额 30 分钟",
            project(state.copy(timeEntries = listOf(TimeEntry(planningDate = "2026-03-05", durationMinutes = 150, targetId = "t1")))).totalsLabel,
        )
    }

    @Test
    fun unowned_and_migration_minutes_are_reported_separately() {
        // 未归属活动与迁移目标各自成行，不混进目标投入
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书")),
            timeEntries = listOf(
                TimeEntry(planningDate = "2026-03-05", durationMinutes = 40, targetId = null),
                TimeEntry(planningDate = "2026-03-05", durationMinutes = 60, targetId = "t1"),
            ),
            migrations = listOf(
                Migration(targetId = "t1", sourceStageId = "s0", minutes = 25, destinationStartDate = "2026-03-05"),
            ),
        )

        val screenState = project(state)

        assertEquals("未归属活动：40 分钟", screenState.unownedMinutesLabel)
        assertEquals("迁移目标：25 分钟", screenState.migrationSummaryLabel)
    }

    @Test
    fun target_investment_line_reports_direct_and_subtree_minutes() {
        // 父目标显示子树汇总，子目标显示直接投入；归档目标带「（已归档）」标记
        val state = AttentionState(
            targets = listOf(
                Target(id = "parent", title = "年度"),
                Target(id = "child", title = "写书", parentId = "parent", archived = true),
            ),
            timeEntries = listOf(TimeEntry(planningDate = "2026-03-05", durationMinutes = 60, targetId = "child")),
        )

        assertEquals(
            listOf(
                "年度：60 分钟 · 直接投入 0 分钟 · 子树汇总 60 分钟",
                "写书（已归档）：60 分钟 · 直接投入 60 分钟 · 子树汇总 60 分钟",
            ),
            project(state).targetInvestmentLines,
        )
    }

    @Test
    fun migrations_overlapping_the_stats_range_are_visible() {
        // 迁移落在周期内或跨周期边界时都可见，并带上可取消的 id
        val state = AttentionState(
            migrations = listOf(
                Migration(id = "m1", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-05"),
                Migration(id = "m2", targetId = "t1", sourceStageId = "s1", minutes = 10, destinationStartDate = "2026-03-01", destinationEndDate = "2026-03-31"),
            ),
        )

        assertEquals(
            listOf("m1" to "主动迁移 25 分钟 · 2026-03-05", "m2" to "主动迁移 10 分钟 · 2026-03-01"),
            project(state).migrations.map { it.migrationId to it.label },
        )
    }

    @Test
    fun migrations_outside_the_stats_range_or_cancelled_are_hidden() {
        // 周期外的迁移与已取消的迁移都不出现在统计页
        val state = AttentionState(
            migrations = listOf(
                Migration(id = "before", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-04"),
                Migration(id = "after", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-06"),
                Migration(id = "cancelled", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-05", cancelled = true),
            ),
        )

        assertEquals(emptyList<String>(), project(state).migrations.map { it.migrationId })
    }

    @Test
    fun weekly_period_keeps_the_migration_that_only_touches_its_boundary() {
        // 每周周期跨到 3 月 8 日，因此 3 月 8 日的迁移也算可见，3 月 9 日的不算
        val state = AttentionState(
            migrations = listOf(
                Migration(id = "onBoundary", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-08"),
                Migration(id = "pastRange", targetId = "t1", sourceStageId = "s1", minutes = 25, destinationStartDate = "2026-03-09"),
            ),
        )

        assertEquals(
            listOf("onBoundary"),
            project(state, cadence = GoalCadence.WEEKLY).migrations.map { it.migrationId },
        )
    }

    @Test
    fun level_progress_lines_report_the_current_level_band() {
        // 1200 点投入落在等级 2：门槛 1000、下一等级 3000、区间进度 200/2000、剩余 1800
        val screenState = project(AttentionState(experience = 1200))

        assertEquals(
            listOf(
                "投入经验：1200 · 等级 2",
                "当前等级区间进度：200/2000 点",
                "下一等级门槛：3000 点",
                "剩余经验：1800 点",
                "升级反馈：已达到等级 2",
            ),
            screenState.levelLines,
        )
    }

    @Test
    fun level_one_promises_the_next_threshold_instead_of_a_reached_level() {
        // 等级 1 还没有升级，因此反馈语说明达到门槛后才显示
        val screenState = project(AttentionState(experience = 120))

        assertEquals(listOf("投入经验：120 · 等级 1"), screenState.levelLines.take(1))
        assertEquals("升级反馈：达到下一等级门槛后会显示", screenState.levelLines.last())
    }

    @Test
    fun period_snapshot_line_reports_target_actual_gap_and_result() {
        // 快照行同时给出目标、实际、缺口或达成结果
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书")),
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
                PeriodSnapshot(
                    id = "p2",
                    targetId = "t1",
                    cadence = GoalCadence.WEEKLY,
                    periodStart = "2026-02-23",
                    periodEnd = "2026-03-01",
                    targetMinutes = 300,
                    actualMinutes = 120,
                    gapMinutes = 180,
                    excessMinutes = 0,
                    completed = false,
                ),
            ),
        )

        val screenState = project(state)

        assertEquals(false, screenState.snapshotsEmpty)
        assertEquals(
            listOf(
                "写书 · 每周 2026-02-23～2026-03-01：120/300 分钟 · 缺口 180",
                "写书 · 每日 2026-03-04～2026-03-04：80/60 分钟 · 达成",
            ),
            screenState.snapshotLines,
        )
    }

    @Test
    fun a_snapshot_of_a_deleted_target_says_which_target_is_gone() {
        // 目标被删后快照仍然可见，并标出已删除目标的 id 前缀
        val state = AttentionState(
            periodSnapshots = listOf(
                PeriodSnapshot(
                    id = "p1",
                    targetId = "0123456789abcdef",
                    cadence = GoalCadence.DAILY,
                    periodStart = "2026-03-04",
                    periodEnd = "2026-03-04",
                    targetMinutes = 60,
                    actualMinutes = 20,
                    gapMinutes = 40,
                    excessMinutes = 0,
                    completed = false,
                ),
            ),
        )

        assertEquals(
            listOf("已删除目标 01234567 · 每日 2026-03-04～2026-03-04：20/60 分钟 · 缺口 40"),
            project(state).snapshotLines,
        )
    }

    @Test
    fun cumulative_and_recent_milestones_each_get_a_line() {
        // 累计投入里程碑按 instanceKey 去重，最近里程碑单独成行
        val state = AttentionState(
            milestones = listOf(
                com.attention.domain.Milestone(id = "m1", kind = "cumulative", instanceKey = "cumulative:100", reward = 10_000, achievedAtEpochMillis = 20),
                com.attention.domain.Milestone(id = "m2", kind = "cumulative", instanceKey = "cumulative:100", reward = 10_000, achievedAtEpochMillis = 10),
                com.attention.domain.Milestone(id = "m3", kind = "one_time_stage", instanceKey = "one_time:s1:2026-03-05", reward = 30, achievedAtEpochMillis = 30),
            ),
        )

        val screenState = project(state)

        assertEquals(false, screenState.cumulativeMilestonesEmpty)
        assertEquals(listOf("累计投入里程碑：100 小时 · +10000 投入经验"), screenState.cumulativeMilestoneLines)
        assertEquals(
            listOf("里程碑 cumulative：+10000（cumulative:100）", "里程碑 one_time_stage：+30（one_time:s1:2026-03-05）"),
            screenState.recentMilestoneLines,
        )
    }

    @Test
    fun previous_and_next_period_shift_by_the_selected_cadence() {
        // 上一/下一周期按当前周期粒度位移：每日 1 天、每周 7 天、每月 1 个月
        assertEquals(LocalDate.of(2026, 3, 4), shiftStatisticsPeriod(selected, GoalCadence.DAILY, -1))
        assertEquals(LocalDate.of(2026, 3, 6), shiftStatisticsPeriod(selected, GoalCadence.DAILY, 1))
        assertEquals(LocalDate.of(2026, 2, 26), shiftStatisticsPeriod(selected, GoalCadence.WEEKLY, -1))
        assertEquals(LocalDate.of(2026, 3, 12), shiftStatisticsPeriod(selected, GoalCadence.WEEKLY, 1))
        assertEquals(LocalDate.of(2026, 2, 5), shiftStatisticsPeriod(selected, GoalCadence.MONTHLY, -1))
        assertEquals(LocalDate.of(2026, 4, 5), shiftStatisticsPeriod(selected, GoalCadence.MONTHLY, 1))
    }

    @Test
    fun monthly_shift_crosses_the_month_boundary() {
        // 跨月位移落到目标月的同一天：1 月 31 日的下一个月是 2 月 28 日
        assertEquals(LocalDate.of(2026, 2, 28), shiftStatisticsPeriod(LocalDate.of(2026, 1, 31), GoalCadence.MONTHLY, 1))
        assertEquals(LocalDate.of(2026, 1, 28), shiftStatisticsPeriod(LocalDate.of(2026, 2, 28), GoalCadence.MONTHLY, -1))
        assertEquals(LocalDate.of(2024, 2, 29), shiftStatisticsPeriod(LocalDate.of(2024, 1, 31), GoalCadence.MONTHLY, 1))
    }

    @Test
    fun a_shifted_period_reports_the_new_range_for_the_same_selection() {
        // 位移后重新投影，周期范围跟着移动，页面不需要自己算
        val nextPeriod = statisticsScreenState(
            state = AttentionState(),
            now = now,
            zone = zone,
            selectedDate = shiftStatisticsPeriod(selected, GoalCadence.DAILY, 1),
            cadence = GoalCadence.DAILY,
        )

        assertEquals("2026-03-06 至 2026-03-06", nextPeriod.periodRangeLabel)
    }

    @Test
    fun the_projection_is_deterministic_for_a_fixed_instant_and_zone() {
        // 同一瞬间、同一时区、同一选择反复投影得到同一份状态
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书")),
            timeEntries = listOf(TimeEntry(planningDate = "2026-03-05", durationMinutes = 60, targetId = "t1")),
        )

        assertEquals(project(state), project(state))
    }

    @Test
    fun injected_instant_and_zone_choose_the_period_when_the_page_has_not_selected_one() {
        // 尚未选择周期时跟随注入的时间与时区；同一个瞬间在不同时区落在不同规划日
        val instant = Instant.parse("2026-03-05T20:00:00Z")

        assertEquals(LocalDate.of(2026, 3, 6), project(instant = instant, selectedDate = null).selectedDate)
        assertEquals("2026-03-06 至 2026-03-06", project(instant = instant, selectedDate = null).periodRangeLabel)
        assertEquals(
            LocalDate.of(2026, 3, 5),
            project(instant = instant, zone = ZoneId.of("America/Los_Angeles"), selectedDate = null).selectedDate,
        )
    }

    @Test
    fun an_explicit_selection_is_not_overridden_by_the_injected_instant() {
        // 用户选过周期之后，时间往前推不会把页面拽回当前规划日
        val later = Instant.parse("2026-04-20T10:00:00Z")

        assertEquals(LocalDate.of(2026, 3, 5), project(instant = later).selectedDate)
        assertEquals("2026-03-05 至 2026-03-05", project(instant = later).periodRangeLabel)
    }

    @Test
    fun stage_sections_carry_the_progress_line_and_its_feedback() {
        // 阶段进度与阶段反馈都由投影给出，页面只负责缩进渲染
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书")),
            goalStages = listOf(
                GoalStage(id = "s1", targetId = "t1", cadence = GoalCadence.ONE_TIME, targetMinutes = 300, startDate = "2026-03-05"),
            ),
        )

        val sections = project(state).stageSections

        assertEquals(1, sections.size)
        assertEquals(
            "一次性阶段汇总：实际 0/300 分钟 · 缺口 300 分钟 · 超额投入 0 分钟 · 未达成",
            sections.single().summaryLine,
        )
        assertEquals(
            "一次性目标 0/300 分钟 · 范围 2026-03-05 至 2026-03-05 · 开放 · 缺口 300 分钟",
            sections.single().stages.single().label,
        )
        assertEquals(emptyList<String>(), sections.single().stages.single().feedbackLabels)
    }

    @Test
    fun archived_targets_keep_their_investment_line_but_leave_the_stage_listing() {
        // 归档目标仍出现在「目标树投入」里，但不再出现在阶段进度清单里
        val state = AttentionState(
            targets = listOf(Target(id = "t1", title = "写书", archived = true)),
            goalStages = listOf(
                GoalStage(id = "s1", targetId = "t1", cadence = GoalCadence.ONE_TIME, targetMinutes = 300, startDate = "2026-03-05"),
            ),
        )

        val screenState = project(state)

        assertEquals(listOf("写书（已归档）：0 分钟 · 直接投入 0 分钟 · 子树汇总 0 分钟"), screenState.targetInvestmentLines)
        assertEquals(true, screenState.hasTargets)
        assertEquals(emptyList<StatisticsStageSection>(), screenState.stageSections)
    }
}