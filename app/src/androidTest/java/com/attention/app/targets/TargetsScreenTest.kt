package com.attention.app.targets

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.MainActivity
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.workspace.state.TargetsActions
import com.attention.workspace.state.targetsScreenState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 目标树页的直接测试：只给它投影后的 screen state、fake actions 与页面自己的选择状态，
 * 断言可见标签、树结构与发出的 intent，不经过 ViewModel 与 Room。
 */
@RunWith(AndroidJUnit4::class)
class TargetsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val now: Instant = Instant.parse("2026-03-05T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val planningDate: LocalDate = LocalDate.parse("2026-03-05")

    @Test
    fun the_page_shows_the_projected_tree_with_its_hierarchy_and_subtree_totals() {
        // 层级、父子嵌套与子树汇总逐行来自投影
        mount()

        node("目标树").assertIsDisplayed()
        node("写书").assertIsDisplayed()
        node("第三章").assertIsDisplayed()
        node("直接 25 / 汇总 35 分钟").assertIsDisplayed()
        // 阶段进度行可能落在折叠下方，这里只断言它已经被渲染出来；
        // 阶段进度按整棵子树汇总，所以是 25（直接）+ 10（第三章）= 35
        node("每日目标 35/60 分钟 · 周期 2026-03-05 至 2026-03-05 · 缺口 25 分钟").assertExists()
    }

    @Test
    fun archived_targets_leave_the_default_tree_but_keep_their_history() {
        // 归档目标不在默认目标树里，但仍显示层级与历史投入
        mount()

        node("写书").assertIsDisplayed()
        node("已归档目标").performScrollTo().assertIsDisplayed()
        node("旧计划").assertIsDisplayed()
        node("层级：旧计划").assertIsDisplayed()
        node("历史投入：0 分钟").assertIsDisplayed()
    }

    @Test
    fun the_archived_empty_state_is_shown_when_nothing_is_archived() {
        // 没有归档目标时给出空状态
        mount(domesticState().copy(targets = domesticState().targets.filter { !it.archived }))

        node("暂无归档目标").assertIsDisplayed()
    }

    @Test
    fun collapsing_a_target_emits_the_toggle_intent() {
        // 折叠只发出 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        node("折叠").performScrollTo().performClick()

        assertEquals(listOf("root"), intents.toggled)
    }

    @Test
    fun archiving_a_target_emits_the_archive_intent() {
        // 归档按钮发出归档 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        nodes("归档")[0].performScrollTo().performClick()

        assertEquals(listOf("root"), intents.archived)
    }

    @Test
    fun moving_a_target_emits_a_future_move_intent_from_tomorrow() {
        // 从目标树发起移动：预填当前父目标与明日生效，保存时发出未来移动 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        nodes("移动")[1].performScrollTo().performClick()
        node("移动 第三章：选择新的父目标").performScrollTo().assertIsDisplayed()
        node("保存未来移动").performScrollTo().performClick()

        assertEquals(listOf("child|root|2026-03-06"), intents.moved)
    }

    @Test
    fun a_future_move_on_the_current_planning_day_is_rejected_with_a_visible_message() {
        // 生效日必须晚于当前规划日：不合法时给出可见提示且保存按钮不可用
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        nodes("移动")[1].performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction() and hasText("2026-03-06", substring = true))
            .performTextReplacement("2026-03-05")

        node("生效日必须晚于当前规划日 2026-03-05").performScrollTo().assertIsDisplayed()
        node("保存未来移动").performScrollTo().assertIsNotEnabled()
        assertTrue(intents.moved.isEmpty())
    }

    @Test
    fun saving_a_stage_migration_emits_the_migration_intent_with_the_default_start_day() {
        // 目标阶段有缺口时出现迁移表单；目的开始日留空即用阶段进度范围之后的那天
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        nodes("添加子计划")[0].performScrollTo().performClick()
        node("目标迁移：缺口 25 分钟 · 仍可迁移 25 分钟").assertExists()
        composeRule.onNodeWithTag("migration-minutes-s1").performScrollTo().performTextReplacement("15")
        composeRule.onNodeWithTag("migration-submit-s1").performScrollTo().performClick()

        assertEquals(listOf("root|-|s1|15|2026-03-06|-|2026-03-05"), intents.savedMigrations)
    }

    @Test
    fun editing_a_pending_future_goal_rule_emits_the_update_intent() {
        // 待生效的未来目标规则可编辑并更新，表单按规则本身预填
        val intents = RecordingTargetsActions()
        mount(state = domesticState().copy(futureGoalRules = listOf(
            FutureGoalRule(
                id = "r1",
                targetId = "root",
                cadence = GoalCadence.WEEKLY,
                targetMinutes = 300,
                effectiveFrom = "2026-03-10",
            ),
        )), actions = intents)

        nodes("添加子计划")[0].performScrollTo().performClick()
        node("2026-03-10 · 每周 300 分钟 · 待生效").assertExists()

        node("编辑").performScrollTo().performClick()

        node("更新未来规则").performScrollTo().assertIsDisplayed()
        node("更新未来规则").performScrollTo().performClick()

        assertEquals(listOf("root|r1|WEEKLY|300|2026-03-10|-|2026-03-05"), intents.savedFutureGoals)
    }

    @Test
    fun permanent_removal_asks_for_confirmation_before_the_destructive_intent() {
        // 永久移除目标关系仍需确认：确认之前不发出破坏性 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        node("安全删除").performScrollTo().performClick()

        node("删除目标关系？").assertIsDisplayed()
        node("将删除所选目标及其后代，共 1 个目标。").assertIsDisplayed()
        assertTrue(intents.deleted.isEmpty())

        node("确认删除").performClick()

        assertEquals(listOf(setOf("old")), intents.deleted)
    }

    @Test
    fun dismissing_the_removal_confirmation_emits_nothing() {
        // 取消确认不会发出破坏性 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        node("安全删除").performScrollTo().performClick()
        node("取消").performClick()

        // 取消只关掉确认框：破坏性 intent 一次都不该发出，删除入口仍然可用
        assertTrue(intents.deleted.isEmpty())
        node("安全删除").assertExists()
    }

    @Test
    fun batch_removal_uses_the_batch_confirmation_title() {
        // 勾选多个归档目标后请求批量删除，确认框与批量 intent 都带上这一批目标
        val intents = RecordingTargetsActions()
        mount(state = twoArchivedState(), actions = intents)

        listOf(0, 1).forEach { index ->
            archivedCheckbox(index).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        }
        node("已选 2 个").assertExists()
        composeRule.onNodeWithTag("delete-archived-selected")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        node("批量删除目标关系？").assertExists()
        node("确认删除").performClick()

        assertEquals(listOf(setOf("old", "old2")), intents.deleted)
    }

    @Test
    fun restoring_an_archived_target_emits_the_restore_intent() {
        // 归档区恢复按钮发出恢复 intent
        val intents = RecordingTargetsActions()
        mount(actions = intents)

        node("恢复").performScrollTo().performClick()

        assertEquals(listOf(setOf("old")), intents.restored)
    }

    private fun node(text: String, substring: Boolean = false) =
        composeRule.onNodeWithText(text, substring = substring)

    /** 同级目标各自带同名按钮，用下标选中目标树里第几个。 */
    private fun nodes(text: String) = composeRule.onAllNodesWithText(text)

    /** 归档区每个目标一个勾选框。 */
    private fun archivedCheckbox(index: Int) = composeRule.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox),
    )[index]

    /** 这一组数据带两个归档目标，用来覆盖批量移除这条路径。 */
    private fun twoArchivedState(): AttentionState = domesticState().copy(
        targets = domesticState().targets + Target(id = "old2", title = "旧计划二", archived = true, sortOrder = 3),
    )

    private fun domesticState(): AttentionState = AttentionState(
        targets = listOf(
            Target(id = "root", title = "写书", sortOrder = 1),
            Target(id = "child", title = "第三章", parentId = "root", sortOrder = 1, expanded = false),
            Target(id = "old", title = "旧计划", archived = true, sortOrder = 2),
        ),
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
            TimeEntry(id = "e2", planningDate = "2026-03-05", durationMinutes = 10, targetId = "child"),
        ),
    )

    /**
     * 页面与组合根的接法一样：组合根注入时间与时区算出投影，并把页面 module 自己
     * 创建的选择状态喂进去。选择一变，投影跟着重算。
     */
    private fun mount(
        state: AttentionState = domesticState(),
        actions: TargetsActions = RecordingTargetsActions(),
    ) {
        composeRule.activity.setContent {
            AttentionTheme {
                val selection = rememberTargetsSelection(planningDate)
                TargetsScreen(
                    state = targetsScreenState(
                        state = state,
                        now = now,
                        zone = zone,
                        selection = selection.snapshot(),
                    ),
                    actions = actions,
                    selection = selection,
                )
            }
        }
    }

    private class RecordingTargetsActions : TargetsActions {
        val toggled = mutableListOf<String>()
        val archived = mutableListOf<String>()
        val moved = mutableListOf<String>()
        val savedMigrations = mutableListOf<String>()
        val savedFutureGoals = mutableListOf<String>()
        val restored = mutableListOf<Set<String>>()
        val deleted = mutableListOf<Set<String>>()

        override fun addTarget(title: String, parentId: String?) = Unit

        override fun renameTarget(targetId: String, title: String) = Unit

        override fun addGoal(
            targetId: String,
            cadence: GoalCadence,
            targetMinutes: Int,
            startDate: String,
            dueDate: String?,
        ) = Unit

        override fun assignUnowned(timeEntryIds: Set<String>, targetId: String) = Unit

        override fun saveFutureGoal(
            targetId: String,
            ruleId: String?,
            cadence: GoalCadence,
            targetMinutes: Int,
            effectiveFrom: String,
            dueDate: String?,
            onDate: LocalDate,
        ) {
            savedFutureGoals += "$targetId|${ruleId ?: "-"}|$cadence|$targetMinutes|$effectiveFrom|${dueDate ?: "-"}|$onDate"
        }

        override fun cancelFutureGoal(ruleId: String, onDate: LocalDate) = Unit

        override fun appendOneTimeGoal(
            targetId: String,
            targetMinutes: Int,
            startDate: String,
            dueDate: String?,
            onSuccess: () -> Unit,
        ) = onSuccess()

        override fun saveMigration(
            targetId: String,
            migrationId: String?,
            sourceStageId: String,
            minutes: Int,
            destinationStartDate: String,
            destinationEndDate: String?,
            onDate: LocalDate,
        ) {
            savedMigrations += "$targetId|${migrationId ?: "-"}|$sourceStageId|$minutes|$destinationStartDate|${destinationEndDate ?: "-"}|$onDate"
        }

        override fun cancelMigration(migrationId: String) = Unit

        override fun toggleTarget(targetId: String, expanded: Boolean) {
            toggled += targetId
        }

        override fun reorderTarget(targetId: String, newIndex: Int) = Unit

        override fun archiveTarget(targetId: String) {
            archived += targetId
        }

        override fun restoreTargets(targetIds: Set<String>) {
            restored += targetIds
        }

        override fun deleteTargetRelations(targetIds: Set<String>) {
            deleted += targetIds
        }

        override fun moveTargetFrom(targetId: String, parentId: String?, effectiveFrom: String) {
            moved += "$targetId|${parentId ?: "-"}|$effectiveFrom"
        }

        override fun updateFutureTargetMove(moveId: String, parentId: String?, effectiveFrom: String) = Unit

        override fun cancelFutureTargetMove(moveId: String) = Unit
    }
}
