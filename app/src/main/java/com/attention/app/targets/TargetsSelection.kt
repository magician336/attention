package com.attention.app.targets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.domain.GoalCadence
import com.attention.workspace.state.TargetsFutureMove
import com.attention.workspace.state.TargetsFutureRuleRow
import com.attention.workspace.state.TargetsMigrationRow
import com.attention.workspace.state.TargetsMovePendingRow
import com.attention.workspace.state.TargetsSelectionSnapshot
import java.time.LocalDate

/**
 * 目标树页自己的选择与表单草稿状态。
 *
 * 它属于目标树页 module 而不是组合根：ADR-0005 第 1 节要求选择状态（选中目标、表单草稿、
 * 对话框开关）的类型与迁移语义留在页面一侧。组合根只调用 [rememberTargetsSelection]
 * 持有一个实例来喂投影，不认识字段，也不解释任何一条语义——语义只在这里。
 *
 * 「永久移除目标关系」的确认也属于本页：待删除集合在这里，破坏性动作本身是一个 intent。
 */
internal class TargetsSelection(planningDate: LocalDate) {

    /** 组合根注入的当前规划日，供切换父目标时重置进度日期。 */
    var planningDate: LocalDate = planningDate
        private set

    /** 当前展开详情的父目标；null 表示只做新增。 */
    var parentId: String? by mutableStateOf(null)
        private set

    var title by mutableStateOf("")
    var goalMinutes by mutableStateOf("")
    var goalCadence by mutableStateOf(GoalCadence.DAILY)
        private set
    var goalStartDate by mutableStateOf(planningDate.toString())
    var goalDueDate by mutableStateOf("")

    var futureDate by mutableStateOf(planningDate.plusDays(7).toString())
    var futureMinutes by mutableStateOf("")
    var futureDueDate by mutableStateOf("")
    var futureCadence by mutableStateOf(GoalCadence.WEEKLY)
        private set
    var editingFutureRuleId by mutableStateOf<String?>(null)
        private set

    /** 目标进度区的当前规划日文本，格式无效时页面给出提示并回落到当前规划日。 */
    var selectedPlanningDate by mutableStateOf(planningDate.toString())

    var appendGoalMinutes by mutableStateOf("")
    var appendGoalDueDate by mutableStateOf("")

    var migrationMinutes by mutableStateOf("")
    var migrationStartDate by mutableStateOf("")
    var migrationEndDate by mutableStateOf("")
    var editingMigrationId by mutableStateOf<String?>(null)
        private set

    /** 待永久移除的目标关系。非空即表示确认框开着，破坏性 intent 还没有发出。 */
    var pendingDeleteIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var renameTargetId by mutableStateOf<String?>(null)
        private set
    var renameText by mutableStateOf("")

    var movingTargetId by mutableStateOf<String?>(null)
        private set
    var editingMoveId by mutableStateOf<String?>(null)
        private set
    var moveParentId by mutableStateOf<String?>(null)
        private set
    var moveEffectiveDate by mutableStateOf("")

    var selectedUnownedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedArchivedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun updatePlanningDate(planningDate: LocalDate) {
        this.planningDate = planningDate
    }

    /** 选定（或取消）父目标。切换父目标时当前规划日回到注入的规划日。 */
    fun selectParent(targetId: String?) {
        parentId = targetId
        selectedPlanningDate = planningDate.toString()
    }

    fun targetAdded() {
        title = ""
    }

    fun selectGoalCadence(cadence: GoalCadence) {
        goalCadence = cadence
    }

    fun goalSaved() {
        goalMinutes = ""
    }

    fun selectFutureCadence(cadence: GoalCadence) {
        futureCadence = cadence
    }

    fun beginEditFutureRule(rule: TargetsFutureRuleRow) {
        editingFutureRuleId = rule.ruleId
        futureDate = rule.effectiveFrom
        futureMinutes = rule.targetMinutes.toString()
        futureCadence = rule.cadence
        futureDueDate = rule.dueDate.orEmpty()
    }

    fun futureRuleSaved() {
        futureMinutes = ""
        futureDueDate = ""
        editingFutureRuleId = null
    }

    fun beginEditMigration(migration: TargetsMigrationRow) {
        editingMigrationId = migration.migrationId
        migrationMinutes = migration.minutes.toString()
        migrationStartDate = migration.destinationStartDate
        migrationEndDate = migration.destinationEndDate.orEmpty()
    }

    /** 取消编辑迁移与保存迁移之后都回到「添加迁移」的空表单。 */
    fun migrationDraftCleared() {
        editingMigrationId = null
        migrationMinutes = ""
        migrationStartDate = ""
        migrationEndDate = ""
    }

    fun appendStageSaved() {
        appendGoalMinutes = ""
        appendGoalDueDate = ""
    }

    fun beginRename(targetId: String, currentTitle: String) {
        renameTargetId = targetId
        renameText = currentTitle
    }

    fun renameClosed() {
        renameTargetId = null
    }

    /** 从目标树发起移动：预填当前父目标与明日生效。 */
    fun beginMove(targetId: String, currentParentId: String?) {
        movingTargetId = targetId
        editingMoveId = null
        moveParentId = currentParentId
        moveEffectiveDate = planningDate.plusDays(1).toString()
    }

    fun beginEditFutureMove(move: TargetsFutureMove) {
        movingTargetId = move.targetId
        editingMoveId = move.moveId
        moveParentId = move.parentId
        moveEffectiveDate = move.effectiveFrom
    }

    fun beginEditPendingMove(move: TargetsMovePendingRow) {
        editingMoveId = move.moveId
        moveParentId = move.parentId
        moveEffectiveDate = move.effectiveFrom
    }

    fun selectMoveParent(targetId: String?) {
        moveParentId = targetId
    }

    fun moveSaved() {
        movingTargetId = null
        editingMoveId = null
    }

    fun cancelMove() {
        movingTargetId = null
    }

    fun selectUnowned(timeEntryId: String, checked: Boolean) {
        selectedUnownedIds = if (checked) selectedUnownedIds + timeEntryId else selectedUnownedIds - timeEntryId
    }

    fun selectAllUnowned(timeEntryIds: Set<String>) {
        selectedUnownedIds = timeEntryIds
    }

    fun clearUnownedSelection() {
        selectedUnownedIds = emptySet()
    }

    /** 未归属活动归入当前计划之后清空选择。 */
    fun unownedAssigned() {
        selectedUnownedIds = emptySet()
    }

    fun selectArchived(targetId: String, checked: Boolean) {
        selectedArchivedIds = if (checked) selectedArchivedIds + targetId else selectedArchivedIds - targetId
    }

    fun selectAllArchived(targetIds: Set<String>) {
        selectedArchivedIds = targetIds
    }

    fun clearArchivedSelection() {
        selectedArchivedIds = emptySet()
    }

    fun archivedRestored() {
        selectedArchivedIds = emptySet()
    }

    /** 请求永久移除一个目标关系：只打开确认框，还没有发出破坏性 intent。 */
    fun requestRemoval(targetId: String) {
        pendingDeleteIds = setOf(targetId)
    }

    /** 请求批量永久移除。 */
    fun requestBatchRemoval(targetIds: Set<String>) {
        pendingDeleteIds = targetIds
    }

    fun dismissRemoval() {
        pendingDeleteIds = emptySet()
    }

    /** 用户确认后才调用：破坏性 intent 已经发出，选择同时清空。 */
    fun removalConfirmed() {
        pendingDeleteIds = emptySet()
        selectedArchivedIds = emptySet()
    }

    /** 投影需要的页面选择快照。 */
    fun snapshot(): TargetsSelectionSnapshot = TargetsSelectionSnapshot(
        parentId = parentId,
        goalCadence = goalCadence,
        futureCadence = futureCadence,
        editingFutureRuleId = editingFutureRuleId,
        selectedPlanningDate = selectedPlanningDate,
        migrationMinutes = migrationMinutes,
        editingMigrationId = editingMigrationId,
        renameTargetId = renameTargetId,
        renameText = renameText,
        movingTargetId = movingTargetId,
        editingMoveId = editingMoveId,
        moveParentId = moveParentId,
        moveEffectiveDate = moveEffectiveDate,
        selectedUnownedIds = selectedUnownedIds,
        selectedArchivedIds = selectedArchivedIds,
        pendingDeleteIds = pendingDeleteIds,
    )
}

/** 编辑待生效移动时的字段已由投影给出的那一行携带，这里不再重复一份类型。 */

/**
 * 在组合根创建目标树页的选择状态。组合根因此不需要直接构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberTargetsSelection(planningDate: LocalDate): TargetsSelection {
    val selection = remember { TargetsSelection(planningDate) }
    selection.updatePlanningDate(planningDate)
    return selection
}
