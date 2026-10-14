package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.GoalStageSummary
import com.attention.domain.GoalSummary
import com.attention.domain.Milestone
import com.attention.domain.cumulativeHours
import com.attention.domain.effectiveStage
import com.attention.domain.oneTimeStageMilestones
import com.attention.domain.progress
import com.attention.domain.progressRange
import java.time.LocalDate

/**
 * 目标与阶段进度的展示约定，统计页与目标树页共用。
 *
 * 这几个 helper 原本住在 `StatisticsScreenState.kt`，但目标树页的树节点、归档行、迁移表单
 * 与阶段进度行同样消费它们——统计页只是先写出来的那一处，因此它们是跨页共享的目标进度
 * 展示约定，单独成文件而不是挂在某一页的名下。
 *
 * 与 ADR-0005 的分工不变：走哪条父链、进度怎么算、里程碑怎么筛选仍然是 `:domain` 的领域规则；
 * 这里只把领域结论读成可显示的文本。
 */

/** 周期粒度的可见标签。统计页与目标树共用同一份，避免各写一遍。 */
fun GoalCadence.label(): String = when (this) {
    GoalCadence.DAILY -> "每日"
    GoalCadence.WEEKLY -> "每周"
    GoalCadence.MONTHLY -> "每月"
    GoalCadence.ONE_TIME -> "一次性"
}

fun AttentionState.goalProgressLabel(stage: GoalStage, planningDate: LocalDate): String =
    goalProgressLabel(GoalStageSummary(stage, progress(stage, planningDate)), planningDate)

fun AttentionState.goalProgressLabel(detail: GoalStageSummary, planningDate: LocalDate): String {
    val stage = detail.stage
    val effectiveStage = effectiveStage(stage, planningDate)
    val progress = detail.progress
    val progressRange = progressRange(stage, planningDate)
    val stageStart = LocalDate.parse(effectiveStage.startDate)
    val rangeLabel = when (effectiveStage.cadence) {
        GoalCadence.ONE_TIME -> {
            val boundaryLabel = when {
                effectiveStage.dueDate != null -> " · 截止 ${effectiveStage.dueDate}"
                progressRange.endInclusive.isBefore(planningDate) -> " · 阶段封闭于 ${progressRange.endInclusive}"
                else -> " · 开放"
            }
            "范围 ${progressRange.start} 至 ${progressRange.endInclusive}$boundaryLabel"
        }
        else -> "周期 ${progressRange.start} 至 ${progressRange.endInclusive}"
    }
    return if (planningDate.isBefore(stageStart)) {
        "${effectiveStage.cadence.label()}目标 0/${effectiveStage.targetMinutes} 分钟 · 尚未开始 · 生效日 $stageStart · $rangeLabel"
    } else {
        val status = when {
            progress.excessMinutes > 0 -> "达成 · 超额 ${progress.excessMinutes} 分钟"
            progress.completed -> "达成"
            else -> "缺口 ${progress.gapMinutes} 分钟"
        }
        "${effectiveStage.cadence.label()}目标 ${progress.actualMinutes}/${progress.targetMinutes} 分钟 · $rangeLabel · $status"
    }
}

fun GoalSummary.summaryLabel(): String =
    "一次性阶段汇总：实际 $actualMinutes/$targetMinutes 分钟 · 缺口 $gapMinutes 分钟 · 超额投入 $excessMinutes 分钟 · ${if (completed) "达成" else "未达成"}"

fun AttentionState.stageFeedbackLabels(stage: GoalStage): List<String> =
    if (stage.cadence != GoalCadence.ONE_TIME) {
        emptyList()
    } else {
        oneTimeStageMilestones(stage.id).map { milestone ->
            "阶段达成反馈：${stage.startDate} · +${milestone.reward} 投入经验 · 来源：一次性阶段"
        }
    }

fun Milestone.cumulativeFeedbackLabel(): String {
    val hours = cumulativeHours()
    return if (hours != null) {
        "累计投入里程碑：$hours 小时 · +$reward 投入经验"
    } else {
        "累计投入里程碑：+$reward 投入经验"
    }
}
