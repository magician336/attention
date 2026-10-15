package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Migration
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目标树页投影的纯 Kotlin 测试：页面只读这份状态，不读完整领域状态，也不读系统时钟。
 * 当前时间与时区由组合根注入，跨规划日界线取样。
 */
class TargetsScreenStateTest {

    private val now: Instant = Instant.parse("2026-03-05T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val planningDate: LocalDate = LocalDate.parse("2026-03-05")

    private fun project(
        state: AttentionState,
        selection: TargetsSelectionSnapshot = TargetsSelectionSnapshot(
            selectedPlanningDate = planningDate.toString(),
        ),
        at: Instant = now,
        inZone: ZoneId = zone,
    ) = targetsScreenState(state = state, now = at, zone = inZone, selection = selection)

    @Test
    fun tree_nests_children_under_their_parent_in_sort_order() {
        // 目标树按 sortOrder 排列，子目标嵌在父目标之下
        val state = AttentionState(
            targets = listOf(
                Target(id = "b", title = "第二个根计划", sortOrder = 2),
                Target(id = "a1", title = "子计划甲", parentId = "a", sortOrder = 1),
                Target(id = "a", title = "第一个根计划", sortOrder = 1),
                Target(id = "a2", title = "子计划乙", parentId = "a", sortOrder = 2),
            ),
        )

        val screenState = project(state)

        assertEquals(listOf("第一个根计划", "第二个根计划"), screenState.treeRoots.map { it.title })
        assertEquals(
            listOf("子计划甲", "子计划乙"),
            screenState.treeRoots.first().children.map { it.title },
        )
        assertFalse(screenState.treeEmpty)
    }

    @Test
    fun archived_targets_leave_the_default_tree_but_keep_their_history() {
        // 归档目标不出现在默认目标列表，但仍保留层级与历史投入/历史进度
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划"),
                Target(id = "done", title = "已归档计划", parentId = "root", archived = true),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-01", durationMinutes = 30, targetId = "done"),
            ),
        )

        val screenState = project(state)

        assertEquals(listOf("根计划"), screenState.treeRoots.map { it.title })
        val archived = screenState.archivedRows.single()
        assertEquals("已归档计划", archived.title)
        assertEquals("层级：根计划 / 已归档计划", archived.hierarchyLabel)
        assertEquals("历史投入：30 分钟", archived.historicalMinutesLabel)
    }

    @Test
    fun parent_shows_direct_and_subtree_minutes_for_its_whole_subtree() {
        // 父目标显示直接投入与整个子树的汇总投入
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划"),
                Target(id = "child", title = "子计划", parentId = "root"),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-01", durationMinutes = 20, targetId = "root"),
                TimeEntry(id = "e2", planningDate = "2026-03-01", durationMinutes = 45, targetId = "child"),
            ),
        )

        val screenState = project(state)

        assertEquals("直接 20 / 汇总 65 分钟", screenState.treeRoots.single().minutesLabel)
    }

    @Test
    fun collapsed_target_hides_its_children_and_offers_to_expand() {
        // 折叠状态由投影给出：子节点不再展开，折叠/展开按钮的文案随状态变化
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划", expanded = false),
                Target(id = "child", title = "子计划", parentId = "root"),
            ),
        )

        val screenState = project(state)

        val root = screenState.treeRoots.single()
        assertFalse(root.expanded)
        assertEquals("展开", root.toggleLabel)
        assertTrue(root.hasChildren)
        assertEquals(emptyList<String>(), root.children.map { it.title })

        val expanded = project(state.copy(targets = state.targets.map { it.copy(expanded = true) })).treeRoots.single()
        assertTrue(expanded.expanded)
        assertEquals("折叠", expanded.toggleLabel)
        assertEquals(listOf("子计划"), expanded.children.map { it.title })
    }

    @Test
    fun tree_reports_sibling_position_so_reordering_is_offered_only_when_it_moves_something() {
        // 排序入口只在真的会移动同级位置时出现
        val state = AttentionState(
            targets = listOf(
                Target(id = "first", title = "甲", sortOrder = 1),
                Target(id = "second", title = "乙", sortOrder = 2),
                Target(id = "third", title = "丙", sortOrder = 3),
            ),
        )

        val roots = project(state).treeRoots

        assertFalse(roots.first().canMoveUp)
        assertTrue(roots.first().canMoveDown)
        assertTrue(roots.last().canMoveUp)
        assertFalse(roots.last().canMoveDown)
        assertEquals(listOf(0, 1, 2), roots.map { it.siblingIndex })
    }

    @Test
    fun stage_progress_is_projected_with_its_feedback_lines() {
        // 选中父目标后，阶段进度、一次性汇总与阶段反馈逐行来自投影
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            goalStages = listOf(
                GoalStage(
                    id = "s1",
                    targetId = "root",
                    cadence = GoalCadence.DAILY,
                    targetMinutes = 60,
                    startDate = "2026-03-01",
                ),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 25, targetId = "root"),
            ),
        )

        val detail = project(state, TargetsSelectionSnapshot(parentId = "root", selectedPlanningDate = "2026-03-05")).parentDetail

        assertNotNull(detail)
        assertEquals("根计划", detail!!.targetTitle)
        assertTrue(detail.hasStages)
        val stage = detail.stages.single()
        assertTrue(
            "阶段进度行应包含实际与目标分钟，实际结果是 ${stage.progressLabel}",
            stage.progressLabel.startsWith("每日目标 25/60 分钟"),
        )
    }

    @Test
    fun migration_editor_appears_only_when_there_is_a_gap_or_an_existing_migration() {
        // 有缺口时才出现目标迁移表单；默认目的开始日落在阶段进度范围之后
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            goalStages = listOf(
                GoalStage(
                    id = "s1",
                    targetId = "root",
                    cadence = GoalCadence.WEEKLY,
                    targetMinutes = 120,
                    startDate = "2026-03-02",
                ),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 30, targetId = "root"),
            ),
            migrations = listOf(
                Migration(
                    id = "m1",
                    targetId = "root",
                    sourceStageId = "s1",
                    minutes = 10,
                    destinationStartDate = "2026-03-09",
                ),
            ),
        )

        val detail = project(state, TargetsSelectionSnapshot(parentId = "root", selectedPlanningDate = "2026-03-05")).parentDetail

        val migration = detail!!.stages.single().migration
        assertNotNull(migration)
        assertEquals("目标迁移：缺口 90 分钟 · 仍可迁移 80 分钟", migration!!.headerLabel)
        assertEquals("2026-03-09", migration.defaultStartDate)
        assertEquals("添加迁移", migration.submitLabel)
        assertFalse(migration.submitEnabled)
        assertEquals(listOf("10 分钟 · 2026-03-09"), migration.migrations.map { it.label })
    }

    @Test
    fun editing_a_migration_changes_the_submit_label_and_enables_it() {
        // 进入编辑迁移后，提交按钮改为保存迁移并按已填分钟数启用
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            goalStages = listOf(
                GoalStage(
                    id = "s1",
                    targetId = "root",
                    cadence = GoalCadence.WEEKLY,
                    targetMinutes = 120,
                    startDate = "2026-03-02",
                ),
            ),
        )

        val detail = project(
            state,
            TargetsSelectionSnapshot(
                parentId = "root",
                selectedPlanningDate = "2026-03-05",
                editingMigrationId = "m1",
                migrationMinutes = "30",
            ),
        ).parentDetail

        val migration = detail!!.stages.single().migration
        assertEquals("保存迁移", migration!!.submitLabel)
        assertTrue(migration.submitEnabled)
    }

    @Test
    fun future_goal_rules_show_which_one_is_pending_against_the_selected_planning_day() {
        // 未来目标规则按当前规划日标出待生效，历史规则仍保留在列表里
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            futureGoalRules = listOf(
                FutureGoalRule(id = "r1", targetId = "root", cadence = GoalCadence.DAILY, targetMinutes = 90, effectiveFrom = "2026-03-01"),
                FutureGoalRule(id = "r2", targetId = "root", cadence = GoalCadence.WEEKLY, targetMinutes = 300, effectiveFrom = "2026-03-10"),
            ),
        )

        val detail = project(state, TargetsSelectionSnapshot(parentId = "root", selectedPlanningDate = "2026-03-05")).parentDetail

        assertEquals(
            listOf(
                "2026-03-01 · 每日 90 分钟 · 当前/历史",
                "2026-03-10 · 每周 300 分钟 · 待生效",
            ),
            detail!!.futureRules.map { it.label },
        )
        assertEquals(listOf("r2"), detail.futureRules.filter { it.pending }.map { it.ruleId })
        assertEquals("保存未来规则", detail.futureRuleSubmitLabel)
    }

    @Test
    fun saving_a_future_rule_switches_the_button_to_update() {
        // 正在编辑某条未来规则时提交按钮变为更新
        val detail = project(
            AttentionState(targets = listOf(Target(id = "root", title = "根计划"))),
            TargetsSelectionSnapshot(parentId = "root", editingFutureRuleId = "r1"),
        ).parentDetail

        assertEquals("更新未来规则", detail!!.futureRuleSubmitLabel)
    }

    @Test
    fun future_target_move_requires_an_effective_day_after_the_current_planning_day() {
        // 未来目标移动的生效日必须晚于当前规划日，校验信息来自投影
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划"),
                Target(id = "other", title = "另一个计划"),
            ),
        )

        val today = project(
            state,
            TargetsSelectionSnapshot(movingTargetId = "root", moveEffectiveDate = "2026-03-05"),
        ).moveSheet
        assertNotNull(today)
        assertFalse(today!!.effectiveDateValid)
        assertEquals("生效日必须晚于当前规划日 2026-03-05", today.effectiveDateError)

        val tomorrow = project(
            state,
            TargetsSelectionSnapshot(movingTargetId = "root", moveEffectiveDate = "2026-03-06"),
        ).moveSheet
        assertTrue(tomorrow!!.effectiveDateValid)
        assertFalse(tomorrow.submitEnabled == false)
        assertEquals(listOf("另一个计划"), tomorrow.parents.map { it.label })
    }

    @Test
    fun candidate_parents_exclude_the_target_its_own_subtree() {
        // 不能把目标移动到自己的子树下
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划"),
                Target(id = "child", title = "子计划", parentId = "root"),
            ),
        )

        val sheet = project(
            state,
            TargetsSelectionSnapshot(movingTargetId = "root", moveEffectiveDate = "2026-03-06"),
        ).moveSheet

        assertEquals(emptyList<String>(), sheet!!.parents.map { it.label })
    }

    @Test
    fun pending_move_of_a_target_is_shown_with_its_destination_path_on_the_effective_day() {
        // 待生效移动按生效日解析目标层级
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划"),
                Target(id = "newHome", title = "新归属", sortOrder = 2),
                Target(id = "child", title = "子计划", parentId = "root"),
            ),
            targetMoves = listOf(
                TargetMove(id = "mv1", targetId = "child", parentId = "newHome", effectiveFrom = "2026-03-07"),
            ),
        )

        val roots = project(state).treeRoots

        val movedChild = roots.first { it.title == "根计划" }.children.single()
        assertEquals("子计划", movedChild.title)
        assertEquals("待移动：2026-03-07 → 新归属", movedChild.pendingMoveLabel)
    }

    @Test
    fun permanent_removal_asks_for_confirmation_before_the_destructive_intent() {
        // 永久移除目标关系仍需确认，确认框文案由投影给出
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划", archived = true),
                Target(id = "child", title = "子计划", parentId = "root"),
            ),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-01", durationMinutes = 30, targetId = "child"),
            ),
            schedules = listOf(
                com.attention.domain.ScheduleEntry(id = "sc1", planningDate = "2026-03-01", title = "日程", targetId = "root"),
            ),
        )

        val confirmation = project(
            state,
            TargetsSelectionSnapshot(pendingDeleteIds = setOf("root")),
        ).deletionConfirmation

        assertNotNull(confirmation)
        assertEquals("删除目标关系？", confirmation!!.title)
        assertEquals(setOf("root"), confirmation.targetIds)
        assertTrue(confirmation.lines.first().startsWith("将删除所选目标及其后代，共 2 个目标。"))
    }

    @Test
    fun batch_removal_is_titled_as_a_batch_confirmation() {
        // 批量删除使用自己的确认标题
        val state = AttentionState(
            targets = listOf(
                Target(id = "a", title = "甲", archived = true),
                Target(id = "b", title = "乙", archived = true),
            ),
        )

        val confirmation = project(
            state,
            TargetsSelectionSnapshot(pendingDeleteIds = setOf("a", "b")),
        ).deletionConfirmation

        assertEquals("批量删除目标关系？", confirmation!!.title)
    }

    @Test
    fun no_confirmation_is_requested_when_nothing_is_pending_deletion() {
        // 没有待删除目标时不弹确认
        assertNull(project(AttentionState()).deletionConfirmation)
    }

    @Test
    fun parent_detail_is_absent_until_a_parent_target_is_chosen() {
        // 未选中父目标时不展示父目标详情
        assertNull(project(AttentionState(targets = listOf(Target(id = "root", title = "根计划")))).parentDetail)
    }

    @Test
    fun rename_row_prefills_with_the_current_title() {
        // 重命名表单用当前标题预填
        val state = AttentionState(targets = listOf(Target(id = "root", title = "根计划")))

        val rename = project(
            state,
            TargetsSelectionSnapshot(renameTargetId = "root", renameText = "新的名字"),
        ).renameRow

        assertEquals("重命名 根计划", rename!!.fieldLabel)
        assertEquals("新的名字", rename.initialText)
        assertNull(project(state).renameRow)
    }

    @Test
    fun unowned_entries_are_listed_with_their_selection() {
        // 未归属活动可被选中后归入当前计划
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            timeEntries = listOf(
                TimeEntry(id = "e1", planningDate = "2026-03-05", durationMinutes = 15, targetId = null),
                TimeEntry(id = "e2", planningDate = "2026-03-04", durationMinutes = 20, targetId = null),
            ),
        )

        val unowned = project(
            state,
            TargetsSelectionSnapshot(parentId = "root", selectedUnownedIds = setOf("e2")),
        ).parentDetail!!.unowned

        assertNotNull(unowned)
        assertEquals("未归属活动 2 条，已选 1 条", unowned!!.summaryLabel)
        assertEquals(
            listOf("2026-03-05 · 15 分钟", "2026-03-04 · 20 分钟"),
            unowned.entries.map { it.label },
        )
        assertEquals(listOf("e2"), unowned.entries.filter { it.selected }.map { it.timeEntryId })
    }

    @Test
    fun planning_day_boundary_moves_the_projected_planning_day() {
        // 同一个 instant 在规划日界线前后落在相邻两天，投影只依赖注入的时间与时区
        val state = AttentionState(targets = listOf(Target(id = "root", title = "根计划")))
        val at0315 = project(state, at = Instant.parse("2026-03-05T19:15:00Z"))
        val at0259 = project(state, at = Instant.parse("2026-03-05T18:59:00Z"))

        assertEquals(LocalDate.parse("2026-03-06"), at0315.planningDate)
        assertEquals(LocalDate.parse("2026-03-05"), at0259.planningDate)
    }

    @Test
    fun the_same_instant_lands_on_different_planning_days_in_different_zones() {
        // 时区也参与规划日切分，因此跨规划日界线要连同时区一起取样
        val state = AttentionState(targets = listOf(Target(id = "root", title = "根计划")))
        val instant = Instant.parse("2026-03-05T19:00:00Z")

        assertEquals(
            LocalDate.parse("2026-03-06"),
            project(state, at = instant, inZone = ZoneId.of("Asia/Shanghai")).planningDate,
        )
        assertEquals(
            LocalDate.parse("2026-03-05"),
            project(state, at = instant, inZone = ZoneId.of("America/Los_Angeles")).planningDate,
        )
    }

    @Test
    fun a_selected_planning_day_that_cannot_be_parsed_is_reported_without_breaking_the_projection() {
        // 用户把当前规划日填坏时，页面显示格式错误并回落到注入的当前规划日
        val state = AttentionState(
            targets = listOf(Target(id = "root", title = "根计划")),
            goalStages = listOf(
                GoalStage(id = "s1", targetId = "root", cadence = GoalCadence.DAILY, targetMinutes = 60, startDate = "2026-03-01"),
            ),
        )

        val detail = project(
            state,
            TargetsSelectionSnapshot(parentId = "root", selectedPlanningDate = "不是日期"),
        ).parentDetail

        assertTrue(detail!!.planningDateInvalid)
        assertFalse(detail.stages.isEmpty())
    }

    @Test
    fun an_archived_target_offers_move_to_root_only_when_it_has_a_parent() {
        // 已有父目标的目标才能一键移到根计划
        val state = AttentionState(
            targets = listOf(
                Target(id = "root", title = "根计划", archived = true),
                Target(id = "child", title = "子计划", parentId = "root", archived = true),
            ),
        )

        val detail = project(state, TargetsSelectionSnapshot(parentId = "child")).parentDetail

        assertTrue(detail!!.showMoveToRootAction)
        assertEquals("2026-03-06", detail.moveToRootEffectiveDate)
        assertFalse(project(state, TargetsSelectionSnapshot(parentId = "root")).parentDetail!!.showMoveToRootAction)
    }

    @Test
    fun archived_selection_summary_counts_the_selected_archived_targets() {
        // 归档区的批量操作按已选数量启用
        val state = AttentionState(
            targets = listOf(
                Target(id = "a", title = "甲", archived = true),
                Target(id = "b", title = "乙", archived = true),
            ),
        )

        val screenState = project(state, TargetsSelectionSnapshot(selectedArchivedIds = setOf("a", "b")))

        assertEquals("已选 2 个", screenState.archivedSelectedCountLabel)
        assertTrue(screenState.archivedRows.all { it.selected })
    }
}
