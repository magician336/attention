package com.attention.workspace.state

import com.attention.domain.GoalCadence
import java.time.LocalDate

/**
 * 目标树页的 intent 接口。页面只发出 intent，实际落地（写 Room、构造领域对象）
 * 由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，也拿不到仓储、id 生成器或系统时钟。
 *
 * 校验仍然留在原地：阶段、未来目标规则、目标迁移与未来目标移动的约束都在 `:domain`，
 * adapter 只是把页面给出的字段交给既有的领域入口。
 */
interface TargetsActions {
    /** 创建根计划或子计划。 */
    fun addTarget(title: String, parentId: String?)

    /** 重命名计划。 */
    fun renameTarget(targetId: String, title: String)

    /** 为计划保存一个目标阶段。 */
    fun addGoal(
        targetId: String,
        cadence: GoalCadence,
        targetMinutes: Int,
        startDate: String,
        dueDate: String?,
    )

    /** 把选中的未归属活动归入当前计划。 */
    fun assignUnowned(timeEntryIds: Set<String>, targetId: String)

    /** 新增未来目标规则（[ruleId] 为 null）或更新已有规则（[ruleId] 非 null）。 */
    fun saveFutureGoal(
        targetId: String,
        ruleId: String?,
        cadence: GoalCadence,
        targetMinutes: Int,
        effectiveFrom: String,
        dueDate: String?,
        onDate: LocalDate,
    )

    /** 取消一条尚未生效的未来目标规则。 */
    fun cancelFutureGoal(ruleId: String, onDate: LocalDate)

    /** 追加一次性目标阶段；成功后页面清空表单。 */
    fun appendOneTimeGoal(
        targetId: String,
        targetMinutes: Int,
        startDate: String,
        dueDate: String?,
        onSuccess: () -> Unit,
    )

    /** 新增目标迁移（[migrationId] 为 null）或更新已有迁移（[migrationId] 非 null）。 */
    fun saveMigration(
        targetId: String,
        migrationId: String?,
        sourceStageId: String,
        minutes: Int,
        destinationStartDate: String,
        destinationEndDate: String?,
        onDate: LocalDate,
    )

    /** 取消一条目标迁移。 */
    fun cancelMigration(migrationId: String)

    /** 展开或折叠一个计划。 */
    fun toggleTarget(targetId: String, expanded: Boolean)

    /** 在同级中的新位置移动一个计划。 */
    fun reorderTarget(targetId: String, newIndex: Int)

    /** 归档一个计划。 */
    fun archiveTarget(targetId: String)

    /** 恢复一组已归档的计划。 */
    fun restoreTargets(targetIds: Set<String>)

    /** 永久移除目标关系。页面在用户确认之后才发出这个 intent。 */
    fun deleteTargetRelations(targetIds: Set<String>)

    /** 让计划从指定未来日期起挂到新的父目标下（[parentId] 为 null 表示根计划）。 */
    fun moveTargetFrom(targetId: String, parentId: String?, effectiveFrom: String)

    /** 修改一条尚未生效的未来目标移动。 */
    fun updateFutureTargetMove(moveId: String, parentId: String?, effectiveFrom: String)

    /** 取消一条尚未生效的未来目标移动。 */
    fun cancelFutureTargetMove(moveId: String)
}
