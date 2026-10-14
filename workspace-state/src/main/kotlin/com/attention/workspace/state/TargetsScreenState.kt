package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.availableMigrationMinutes
import com.attention.domain.descendantIds
import com.attention.domain.directMinutes
import com.attention.domain.futureTargetMoveParents
import com.attention.domain.goalRulesAt
import com.attention.domain.goalStagesForTarget
import com.attention.domain.goalSummary
import com.attention.domain.isFutureTargetMoveDate
import com.attention.domain.isPending
import com.attention.domain.parentAt
import com.attention.domain.parseTargetMoveDate
import com.attention.domain.pendingTargetMoves
import com.attention.domain.planningDate
import com.attention.domain.progress
import com.attention.domain.progressRange
import com.attention.domain.subtreeMinutes
import com.attention.domain.targetChildren
import com.attention.domain.targetDeletionImpact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 目标树页自己的选择与表单草稿快照。它是投影的输入，因此住在投影 module 里；
 * 可变的持有者与它的状态迁移在页面 module 内（`TargetsSelection`），
 * 组合根只调用页面 module 的工厂拿一个实例来喂这里。
 *
 * 它只是页面自己填的那些标量，不携带领域状态。
 */
data class TargetsSelectionSnapshot(
    val parentId: String? = null,
    val goalCadence: GoalCadence = GoalCadence.DAILY,
    val futureCadence: GoalCadence = GoalCadence.WEEKLY,
    val editingFutureRuleId: String? = null,
    val selectedPlanningDate: String = "",
    val migrationMinutes: String = "",
    val editingMigrationId: String? = null,
    val renameTargetId: String? = null,
    val renameText: String = "",
    val movingTargetId: String? = null,
    val editingMoveId: String? = null,
    val moveParentId: String? = null,
    val moveEffectiveDate: String = "",
    val selectedUnownedIds: Set<String> = emptySet(),
    val selectedArchivedIds: Set<String> = emptySet(),
    val pendingDeleteIds: Set<String> = emptySet(),
)

/** 一个周期粒度选项。标签由投影给出，页面只负责渲染。 */
data class TargetsCadenceOption(
    val cadence: GoalCadence,
    val label: String,
    val selected: Boolean,
)

/** 单个目标阶段的一行进度与其阶段反馈。反馈行的缩进属于页面排版，因此不带样式。 */
data class TargetsStageProgress(
    val progressLabel: String,
    val feedbackLabels: List<String>,
)

/** 未归属活动里的一条。[selected] 供勾选框渲染。 */
data class TargetsUnownedEntry(
    val timeEntryId: String,
    val label: String,
    val selected: Boolean,
)

/** 未归属活动的选择区。整段在一条未归属活动都没有时不出现。 */
data class TargetsUnownedSection(
    val summaryLabel: String,
    val entries: List<TargetsUnownedEntry>,
)

/** 一条未来目标规则。[pending] 为真时才提供编辑与取消。 */
data class TargetsFutureRuleRow(
    val ruleId: String,
    val label: String,
    val cadence: GoalCadence,
    val targetMinutes: Int,
    val effectiveFrom: String,
    val dueDate: String?,
    val pending: Boolean,
)

/** 一个阶段下的一条目标迁移。[cancellable] 为真时才提供编辑与取消。 */
data class TargetsMigrationRow(
    val migrationId: String,
    val label: String,
    val cancellable: Boolean,
    val minutes: Int,
    val destinationStartDate: String,
    val destinationEndDate: String?,
)

/**
 * 一个阶段下的目标迁移表单。[defaultStartDate] 是目的开始日留空时用的值，
 * 也就是这个阶段进度范围之后的第一个规划日。
 */
data class TargetsMigrationEditor(
    val targetId: String,
    val stageId: String,
    /** 目标迁移落地时使用的规划日，也就是目标进度区当前填写的那个日期。 */
    val onDate: LocalDate,
    val headerLabel: String,
    val defaultStartDate: String,
    val submitEnabled: Boolean,
    val submitLabel: String,
    val editing: Boolean,
    val migrations: List<TargetsMigrationRow>,
)

/** 目标树上一个节点。子节点已按层级展开，[children] 为空表示折叠或没有子目标。 */
data class TargetsTreeNode(
    val targetId: String,
    val title: String,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val toggleLabel: String,
    val minutesLabel: String,
    val pendingMoveLabel: String?,
    val summaryLabel: String?,
    val stages: List<TargetsStageProgress>,
    val siblingIndex: Int,
    val siblingCount: Int,
    /** 该目标在当前规划日的父目标，由领域结论 `parentAt` 读出，用来预填移动表单。 */
    val currentParentId: String?,
    val children: List<TargetsTreeNode>,
) {
    val canMoveUp: Boolean get() = siblingIndex > 0

    val canMoveDown: Boolean get() = siblingIndex < siblingCount - 1
}

/** 归档区里的一个目标。归档目标保留层级与历史时间记录、进度。 */
data class TargetsArchivedRow(
    val targetId: String,
    val title: String,
    val selected: Boolean,
    val hierarchyLabel: String,
    val historicalMinutesLabel: String,
    val summaryLabel: String?,
    val stages: List<TargetsStageProgress>,
)

/**
 * 选中父目标时展示的一个阶段：一行进度、阶段反馈，以及该阶段下可能出现的目标迁移表单。
 * 迁移表单为 null 表示这个阶段既没有缺口也没有已有迁移，不出现。
 */
data class TargetsParentStage(
    val stageId: String,
    val progressLabel: String,
    val feedbackLabels: List<String>,
    val migration: TargetsMigrationEditor?,
)

/** 追加一次性阶段的区块，只在最新阶段已达成时出现。 */
data class TargetsAppendStage(
    val targetId: String,
    val startDateLabel: String,
    /** 追加阶段的起始规划日，也就是目标进度区当前填写的那个日期。 */
    val startDate: String,
)

/** 一个目标上待生效的未来移动。 */
data class TargetsFutureMove(
    val targetId: String,
    val moveId: String,
    val label: String,
    val parentId: String?,
    val effectiveFrom: String,
)

/** 未来目标移动表单里的一个候选父目标。 */
data class TargetsMoveParentOption(
    val targetId: String,
    val label: String,
    val selected: Boolean,
)

/** 未来目标移动表单里的一条待生效移动。 */
data class TargetsMovePendingRow(
    val moveId: String,
    val label: String,
    val parentId: String?,
    val effectiveFrom: String,
)

/** 未来目标移动表单。 */
data class TargetsMoveSheet(
    val targetId: String,
    val targetTitle: String,
    val effectiveDateValid: Boolean,
    val effectiveDateError: String,
    val rootSelected: Boolean,
    val parents: List<TargetsMoveParentOption>,
    val submitEnabled: Boolean,
    val submitLabel: String,
    val pendingMoves: List<TargetsMovePendingRow>,
)

/** 重命名表单。[initialText] 是用当前标题预填的值。 */
data class TargetsRenameRow(
    val targetId: String,
    val fieldLabel: String,
    val initialText: String,
)

/**
 * 永久移除目标关系的确认框。文案由投影给出，因为影响面是领域结论
 * （`:domain` 的 `targetDeletionImpact`）；页面只负责展示与收集确认。
 */
data class TargetsDeletionConfirmation(
    val targetIds: Set<String>,
    val title: String,
    val lines: List<String>,
)

/** 选中一个父目标之后展开的详情区块。 */
data class TargetsParentDetail(
    val targetId: String,
    val targetTitle: String,
    val goalCadenceOptions: List<TargetsCadenceOption>,
    val goalDueDateVisible: Boolean,
    val unowned: TargetsUnownedSection?,
    val currentRuleLabel: String,
    val futureCadenceOptions: List<TargetsCadenceOption>,
    val futureDueDateVisible: Boolean,
    val futureRuleSubmitLabel: String,
    val futureRules: List<TargetsFutureRuleRow>,
    val hasStages: Boolean,
    val planningDateInvalid: Boolean,
    val summaryLabel: String?,
    val stages: List<TargetsParentStage>,
    val appendStage: TargetsAppendStage?,
    val futureMove: TargetsFutureMove?,
    val showMoveToRootAction: Boolean,
    val moveToRootEffectiveDate: String,
)

/** 目标树页专有的不可变状态。它不携带完整的 [AttentionState]， */
data class TargetsScreenState(
    val planningDate: LocalDate,
    val treeRoots: List<TargetsTreeNode>,
    val treeEmpty: Boolean,
    val archivedSelectedCountLabel: String,
    val archivedRows: List<TargetsArchivedRow>,
    val parentDetail: TargetsParentDetail?,
    val renameRow: TargetsRenameRow?,
    val moveSheet: TargetsMoveSheet?,
    val deletionConfirmation: TargetsDeletionConfirmation?,
)

/**
 * 从领域状态投影出目标树页状态。领域规则（目标层级、排序、归档、进度范围、
 * 迁移可用量、删除影响面、移动生效日校验）仍然只在 `:domain`，这里只把它们
 * 读成页面要显示的行与标签。
 *
 * @param now 组合根读一次的当前时间；投影不读系统时钟。
 * @param zone 组合根读一次的时区。
 * @param selection 页面 module 提供的选择与表单草稿快照。
 */
fun targetsScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    selection: TargetsSelectionSnapshot,
): TargetsScreenState {
    val planningDay = state.planningDate(now, zone)
    return TargetsScreenState(
        planningDate = planningDay,
        treeRoots = targetTreeNodes(state, null, planningDay),
        treeEmpty = state.targets.none { !it.archived },
        archivedSelectedCountLabel = "已选 ${selection.selectedArchivedIds.size} 个",
        archivedRows = state.targets.filter { it.archived }.map { target ->
            val summary = state.goalSummary(target.id, planningDay)
            TargetsArchivedRow(
                targetId = target.id,
                title = target.title,
                selected = target.id in selection.selectedArchivedIds,
                hierarchyLabel = "层级：${state.targetPathLabel(target.id)}",
                historicalMinutesLabel = "历史投入：${
                    state.timeEntries
                        .filter { it.targetId in state.descendantIds(target.id) }
                        .sumOf { it.durationMinutes }
                } 分钟",
                summaryLabel = summary.summaryLabel().takeIf { summary.stageProgresses.isNotEmpty() },
                stages = stageProgressRows(state, target.id, planningDay),
            )
        },
        parentDetail = state.targets.firstOrNull { it.id == selection.parentId }?.let { target ->
            parentDetail(state, target, planningDay, selection)
        },
        renameRow = state.targets.firstOrNull { it.id == selection.renameTargetId }?.let { target ->
            TargetsRenameRow(
                targetId = target.id,
                fieldLabel = "重命名 ${target.title}",
                initialText = selection.renameText,
            )
        },
        moveSheet = moveSheet(state, planningDay, selection),
        deletionConfirmation = deletionConfirmation(state, selection),
    )
}

/**
 * 目标层级标签。它按生效日用领域结论 [parentAt] 往上走，再把祖先链拼成「A / B / C」——
 * 走哪条父链是领域规则，拼成标签是展示约定，因此 helper 留在投影 module。
 */
fun AttentionState.targetPathLabel(targetId: String, onDate: LocalDate? = null): String {
    val names = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var current = targets.firstOrNull { it.id == targetId }
    while (current != null && seen.add(current.id)) {
        names += current.title
        val parentId = onDate?.let { parentAt(current.id, it) } ?: current.parentId
        current = parentId?.let { id -> targets.firstOrNull { it.id == id } }
    }
    return names.asReversed().joinToString(" / ")
}

private val targetsCadences = listOf(GoalCadence.DAILY, GoalCadence.WEEKLY, GoalCadence.MONTHLY, GoalCadence.ONE_TIME)

private fun targetTreeNodes(
    state: AttentionState,
    parentId: String?,
    planningDay: LocalDate,
): List<TargetsTreeNode> {
    val siblings = state.targetChildren(parentId, planningDay)
    return siblings.mapIndexed { index, target ->
        val children = state.targetChildren(target.id, planningDay)
        val summary = state.goalSummary(target.id, planningDay)
        TargetsTreeNode(
            targetId = target.id,
            title = target.title,
            hasChildren = children.isNotEmpty(),
            expanded = target.expanded,
            toggleLabel = if (target.expanded) "折叠" else "展开",
            minutesLabel = "直接 ${state.directMinutes(target.id)} / 汇总 ${state.subtreeMinutes(target.id)} 分钟",
            pendingMoveLabel = state.pendingTargetMoves(target.id, planningDay).firstOrNull()?.let { move ->
                "待移动：${move.effectiveFrom} → ${state.moveDestinationLabel(move.parentId, move.effectiveFrom)}"
            },
            summaryLabel = summary.summaryLabel().takeIf { summary.stageProgresses.isNotEmpty() },
            stages = stageProgressRows(state, target.id, planningDay),
            siblingIndex = index,
            siblingCount = siblings.size,
            currentParentId = state.parentAt(target.id, planningDay),
            children = if (target.expanded) {
                targetTreeNodes(state, target.id, planningDay)
            } else {
                emptyList()
            },
        )
    }
}

private fun stageProgressRows(state: AttentionState, targetId: String, planningDay: LocalDate): List<TargetsStageProgress> {
    val summary = state.goalSummary(targetId, planningDay)
    return state.goalStagesForTarget(targetId).map { stage ->
        val detail = summary.stageProgresses.firstOrNull { it.stage.id == stage.id }
        TargetsStageProgress(
            progressLabel = detail?.let { state.goalProgressLabel(it, planningDay) }
                ?: state.goalProgressLabel(stage, planningDay),
            feedbackLabels = state.stageFeedbackLabels(stage),
        )
    }
}

private fun AttentionState.moveDestinationLabel(parentId: String?, effectiveFrom: String): String =
    parentId?.let { targetPathLabel(it, parseTargetMoveDate(effectiveFrom)) } ?: "根计划"

private fun parentDetail(
    state: AttentionState,
    target: com.attention.domain.Target,
    planningDay: LocalDate,
    selection: TargetsSelectionSnapshot,
): TargetsParentDetail {
    val parsedPlanningDate = runCatching { LocalDate.parse(selection.selectedPlanningDate) }.getOrNull()
    val progressPlanningDate = parsedPlanningDate ?: planningDay
    val stages = state.goalStagesForTarget(target.id)
    val oneTimeSummary = state.goalSummary(target.id, progressPlanningDate)
    val currentRule = state.goalRulesAt(target.id, planningDay).lastOrNull { it.stageId.isBlank() }
    val latest = stages.lastOrNull()
    val appendStage = if (
        parsedPlanningDate != null &&
        !target.archived &&
        latest != null &&
        latest.cadence == GoalCadence.ONE_TIME &&
        state.progress(latest, progressPlanningDate).completed
    ) {
        TargetsAppendStage(
            targetId = target.id,
            startDateLabel = "起始规划日：$progressPlanningDate",
            startDate = progressPlanningDate.toString(),
        )
    } else {
        null
    }
    return TargetsParentDetail(
        targetId = target.id,
        targetTitle = target.title,
        goalCadenceOptions = targetsCadences.map { value ->
            TargetsCadenceOption(value, value.label(), selection.goalCadence == value)
        },
        goalDueDateVisible = selection.goalCadence == GoalCadence.ONE_TIME,
        unowned = unownedSection(state, selection),
        currentRuleLabel = currentRule?.let {
            "当前规则：${it.cadence.label()} ${it.targetMinutes} 分钟 · 生效于 ${it.effectiveFrom}"
        } ?: "当前规则：使用目标阶段的承诺",
        futureCadenceOptions = targetsCadences.map { value ->
            TargetsCadenceOption(value, value.label(), selection.futureCadence == value)
        },
        futureDueDateVisible = selection.futureCadence == GoalCadence.ONE_TIME,
        futureRuleSubmitLabel = if (selection.editingFutureRuleId == null) "保存未来规则" else "更新未来规则",
        futureRules = state.futureGoalRules.filter { it.targetId == target.id }.map { rule ->
            TargetsFutureRuleRow(
                ruleId = rule.id,
                label = "${rule.effectiveFrom} · ${rule.cadence.label()} ${rule.targetMinutes} 分钟" +
                    (rule.dueDate?.let { " · 截止 $it" } ?: "") +
                    if (rule.isPending(planningDay)) " · 待生效" else " · 当前/历史",
                cadence = rule.cadence,
                targetMinutes = rule.targetMinutes,
                effectiveFrom = rule.effectiveFrom,
                dueDate = rule.dueDate,
                pending = rule.isPending(planningDay),
            )
        },
        hasStages = stages.isNotEmpty(),
        planningDateInvalid = parsedPlanningDate == null,
        summaryLabel = oneTimeSummary.summaryLabel().takeIf { oneTimeSummary.stageProgresses.isNotEmpty() },
        stages = stages.map { stage ->
            val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
            TargetsParentStage(
                stageId = stage.id,
                progressLabel = detail?.let { state.goalProgressLabel(it, progressPlanningDate) }
                    ?: state.goalProgressLabel(stage, progressPlanningDate),
                feedbackLabels = state.stageFeedbackLabels(stage),
                migration = migrationEditor(state, target.id, stage, progressPlanningDate, selection),
            )
        },
        appendStage = appendStage,
        futureMove = state.pendingTargetMoves(target.id, planningDay).firstOrNull()?.let { move ->
            TargetsFutureMove(
                targetId = target.id,
                moveId = move.id,
                label = "待生效移动：${move.effectiveFrom} → ${state.moveDestinationLabel(move.parentId, move.effectiveFrom)}",
                parentId = move.parentId,
                effectiveFrom = move.effectiveFrom,
            )
        },
        showMoveToRootAction = target.parentId != null,
        moveToRootEffectiveDate = planningDay.plusDays(1).toString(),
    )
}

/**
 * 一个阶段的迁移表单。缺口或已有迁移任一成立才出现；
 * [TargetsMigrationEditor.stageId] 供页面挂稳定的 testTag。
 */
private fun migrationEditor(
    state: AttentionState,
    targetId: String,
    stage: com.attention.domain.GoalStage,
    progressPlanningDate: LocalDate,
    selection: TargetsSelectionSnapshot,
): TargetsMigrationEditor? {
    val progress = state.progress(stage, progressPlanningDate)
    if (progress.gapMinutes <= 0 && state.migrations.none { it.sourceStageId == stage.id }) return null
    val availableMigration = runCatching {
        state.availableMigrationMinutes(stage, progressPlanningDate)
    }.getOrDefault(0)
    return TargetsMigrationEditor(
        targetId = targetId,
        stageId = stage.id,
        onDate = progressPlanningDate,
        headerLabel = "目标迁移：缺口 ${progress.gapMinutes} 分钟 · 仍可迁移 $availableMigration 分钟",
        defaultStartDate = state.progressRange(stage, progressPlanningDate).endInclusive.plusDays(1).toString(),
        submitEnabled = selection.migrationMinutes.toIntOrNull()?.let { it > 0 } == true,
        submitLabel = if (selection.editingMigrationId == null) "添加迁移" else "保存迁移",
        editing = selection.editingMigrationId != null,
        migrations = state.migrations.filter { it.sourceStageId == stage.id }.map { migration ->
            val range = migration.destinationEndDate?.let { "${migration.destinationStartDate} 至 $it" }
                ?: migration.destinationStartDate
            TargetsMigrationRow(
                migrationId = migration.id,
                label = "${migration.minutes} 分钟 · $range${if (migration.cancelled) " · 已取消" else ""}",
                cancellable = !migration.cancelled,
                minutes = migration.minutes,
                destinationStartDate = migration.destinationStartDate,
                destinationEndDate = migration.destinationEndDate,
            )
        },
    )
}

private fun unownedSection(state: AttentionState, selection: TargetsSelectionSnapshot): TargetsUnownedSection? {
    val entries = state.timeEntries.filter { it.targetId == null }
    if (entries.isEmpty()) return null
    val visibleIds = entries.map { it.id }.toSet()
    val selected = selection.selectedUnownedIds intersect visibleIds
    return TargetsUnownedSection(
        summaryLabel = "未归属活动 ${entries.size} 条，已选 ${selected.size} 条",
        entries = entries.map { entry ->
            TargetsUnownedEntry(
                timeEntryId = entry.id,
                label = "${entry.planningDate} · ${entry.durationMinutes} 分钟",
                selected = entry.id in selected,
            )
        },
    )
}

private fun moveSheet(
    state: AttentionState,
    planningDay: LocalDate,
    selection: TargetsSelectionSnapshot,
): TargetsMoveSheet? {
    val target = state.targets.firstOrNull { it.id == selection.movingTargetId } ?: return null
    val validEffectiveDate = isFutureTargetMoveDate(selection.moveEffectiveDate, planningDay)
    val parsedEffectiveDate = parseTargetMoveDate(selection.moveEffectiveDate)
    val candidates = state.futureTargetMoveParents(target.id, selection.moveEffectiveDate, planningDay)
    return TargetsMoveSheet(
        targetId = target.id,
        targetTitle = target.title,
        effectiveDateValid = validEffectiveDate,
        effectiveDateError = "生效日必须晚于当前规划日 $planningDay",
        rootSelected = selection.moveParentId == null,
        parents = candidates
            .sortedBy { state.targetPathLabel(it.id, parsedEffectiveDate ?: planningDay) }
            .map { candidate ->
                TargetsMoveParentOption(
                    targetId = candidate.id,
                    label = state.targetPathLabel(candidate.id, parsedEffectiveDate ?: planningDay),
                    selected = selection.moveParentId == candidate.id,
                )
            },
        submitEnabled = validEffectiveDate,
        submitLabel = if (selection.editingMoveId == null) "保存未来移动" else "保存移动修改",
        pendingMoves = state.pendingTargetMoves(target.id, planningDay).map { move ->
            TargetsMovePendingRow(
                moveId = move.id,
                label = "${move.effectiveFrom} → ${state.moveDestinationLabel(move.parentId, move.effectiveFrom)}",
                parentId = move.parentId,
                effectiveFrom = move.effectiveFrom,
            )
        },
    )
}

private fun deletionConfirmation(
    state: AttentionState,
    selection: TargetsSelectionSnapshot,
): TargetsDeletionConfirmation? {
    val targetIds = selection.pendingDeleteIds
    if (targetIds.isEmpty()) return null
    val impact = state.targetDeletionImpact(targetIds)
    return TargetsDeletionConfirmation(
        targetIds = targetIds,
        title = if (targetIds.size == 1) "删除目标关系？" else "批量删除目标关系？",
        lines = listOf(
            "将删除所选目标及其后代，共 ${impact.targetCount} 个目标。",
            "影响：时间记录 ${impact.timeEntryCount} 条，日程 ${impact.scheduleEntryCount} 条，重复日程 ${impact.recurrenceRuleCount} 条。",
            "目标阶段 ${impact.goalStageCount} 个，未来规则 ${impact.futureGoalRuleCount} 条，迁移 ${impact.migrationCount} 条，未来移动 ${impact.targetMoveCount} 条。",
            "活动计时器 ${impact.activeTimerCount} 个。时间记录会转为未归属活动，日程保留但解除目标关联。投入经验和里程碑保留。",
        ),
    )
}
