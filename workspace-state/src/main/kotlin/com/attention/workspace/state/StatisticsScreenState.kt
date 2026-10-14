package com.attention.workspace.state

import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.GoalStageSummary
import com.attention.domain.GoalSummary
import com.attention.domain.Milestone
import com.attention.domain.effectiveStage
import com.attention.domain.goalStagesForTarget
import com.attention.domain.goalSummary
import com.attention.domain.levelProgressForExperience
import com.attention.domain.oneTimeStageMilestones
import com.attention.domain.periodStats
import com.attention.domain.planningDate
import com.attention.domain.progress
import com.attention.domain.progressRange
import com.attention.domain.recentMilestones
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计页的一个周期粒度选项。标签由投影给出，页面只负责渲染。
 */
data class StatisticsCadenceOption(
    val cadence: GoalCadence,
    val label: String,
    val selected: Boolean,
)

/**
 * 统计页上的一条「可见迁移」。取消迁移的 intent 需要 id，展示需要的只有标签。
 */
data class StatisticsMigrationLine(
    val migrationId: String,
    val label: String,
)

/**
 * 统计页上单个阶段的一行进度与其阶段反馈。反馈行的缩进属于页面排版，因此不带样式。
 */
data class StatisticsStageProgress(
    val label: String,
    val feedbackLabels: List<String>,
)

/**
 * 统计页上一个未归档目标的阶段区块。[summaryLine] 为 null 表示该目标没有一次性阶段汇总行。
 */
data class StatisticsStageSection(
    val summaryLine: String?,
    val stages: List<StatisticsStageProgress>,
)

/**
 * 统计页专有的不可变状态。它只带页面需要的信息，不携带完整的 [AttentionState]，
 * 当前时间与时区由组合根注入，投影与页面都不读系统时钟。
 */
data class StatisticsScreenState(
    val planningDate: LocalDate,
    val selectedDate: LocalDate,
    val cadence: GoalCadence,
    val periodRangeLabel: String,
    val cadenceOptions: List<StatisticsCadenceOption>,
    val totalsLabel: String,
    val unownedMinutesLabel: String,
    val migrationSummaryLabel: String,
    val migrations: List<StatisticsMigrationLine>,
    val targetInvestmentLines: List<String>,
    val hasTargets: Boolean,
    val levelLines: List<String>,
    val snapshotsEmpty: Boolean,
    val snapshotLines: List<String>,
    val cumulativeMilestonesEmpty: Boolean,
    val cumulativeMilestoneLines: List<String>,
    val recentMilestoneLines: List<String>,
    val stageSections: List<StatisticsStageSection>,
)

/**
 * 按当前周期粒度把选中日期前后位移。周期导航是纯日期算术，放在这里便于固定时间下测试。
 */
fun shiftStatisticsPeriod(date: LocalDate, cadence: GoalCadence, amount: Long): LocalDate = when (cadence) {
    GoalCadence.DAILY -> date.plusDays(amount)
    GoalCadence.WEEKLY -> date.plusWeeks(amount)
    GoalCadence.MONTHLY -> date.plusMonths(amount)
    GoalCadence.ONE_TIME -> date
}

/** 周期粒度的可见标签。统计页与目标树共用同一份，避免各写一遍。 */
fun GoalCadence.label(): String = when (this) {
    GoalCadence.DAILY -> "每日"
    GoalCadence.WEEKLY -> "每周"
    GoalCadence.MONTHLY -> "每月"
    GoalCadence.ONE_TIME -> "一次性"
}

/**
 * 从领域状态投影出统计页状态。领域规则（周期范围、目标汇总、等级）仍然只在 `:domain`，
 * 这里只把它们读成页面要显示的行。
 *
 * @param selectedDate 用户选中的规划日；null 表示还没选过，跟随注入的当前规划日。
 */
fun statisticsScreenState(
    state: AttentionState,
    now: Instant,
    zone: ZoneId,
    selectedDate: LocalDate?,
    cadence: GoalCadence,
): StatisticsScreenState {
    val planningDay = state.planningDate(now, zone)
    val date = selectedDate ?: planningDay
    val stats = state.periodStats(date, cadence)
    return StatisticsScreenState(
        planningDate = planningDay,
        selectedDate = date,
        cadence = cadence,
        periodRangeLabel = "${stats.range.start} 至 ${stats.range.endInclusive}",
        cadenceOptions = statisticsCadences.map { value ->
            StatisticsCadenceOption(value, value.label(), cadence == value)
        },
        totalsLabel = "实际 ${stats.actualMinutes} 分钟 · 目标 ${stats.targetMinutes} 分钟 · " +
            "缺口 ${stats.targetMinutes.minus(stats.actualMinutes).coerceAtLeast(0)} 分钟 · 超额 ${stats.excessMinutes} 分钟",
        unownedMinutesLabel = "未归属活动：${stats.unownedMinutes} 分钟",
        migrationSummaryLabel = "迁移目标：${stats.migrationMinutes} 分钟",
        migrations = state.migrations
            .filterNot { it.cancelled }
            .filter { migration ->
                val start = LocalDate.parse(migration.destinationStartDate)
                val end = migration.destinationEndDate?.let(LocalDate::parse) ?: start
                start <= stats.range.endInclusive && end >= stats.range.start
            }
            .map { migration ->
                StatisticsMigrationLine(
                    migrationId = migration.id,
                    label = "主动迁移 ${migration.minutes} 分钟 · ${migration.destinationStartDate}",
                )
            },
        targetInvestmentLines = state.targets.map { target ->
            val direct = stats.byTargetMinutes[target.id] ?: 0
            val subtree = stats.subtreeByTargetMinutes[target.id] ?: 0
            val archivedLabel = if (target.archived) "（已归档）" else ""
            "${target.title}$archivedLabel：$subtree 分钟 · 直接投入 $direct 分钟 · 子树汇总 $subtree 分钟"
        },
        hasTargets = state.targets.isNotEmpty(),
        levelLines = levelLines(state.experience),
        snapshotsEmpty = state.periodSnapshots.isEmpty(),
        snapshotLines = state.periodSnapshots
            .sortedWith(compareBy({ it.periodStart }, { it.id }))
            .map { snapshot ->
                val targetLabel = state.targets.firstOrNull { it.id == snapshot.targetId }?.title
                    ?: "已删除目标 ${snapshot.targetId.take(8)}"
                "$targetLabel · ${snapshot.cadence.label()} ${snapshot.periodStart}～${snapshot.periodEnd}：" +
                    "${snapshot.actualMinutes}/${snapshot.targetMinutes} 分钟" +
                    if (snapshot.completed) " · 达成" else " · 缺口 ${snapshot.gapMinutes}"
            },
        cumulativeMilestonesEmpty = cumulativeMilestones(state).isEmpty(),
        cumulativeMilestoneLines = cumulativeMilestones(state).map { it.cumulativeFeedbackLabel() },
        recentMilestoneLines = state.recentMilestones().map { milestone ->
            "里程碑 ${milestone.kind}：+${milestone.reward}（${milestone.instanceKey}）"
        },
        stageSections = state.targets.filter { !it.archived }.map { target ->
            val oneTimeSummary = state.goalSummary(target.id, date)
            val stages = state.goalStagesForTarget(target.id)
            StatisticsStageSection(
                summaryLine = oneTimeSummary.summaryLabel().takeIf { oneTimeSummary.stageProgresses.isNotEmpty() },
                stages = stages.map { stage ->
                    val detail = oneTimeSummary.stageProgresses.firstOrNull { it.stage.id == stage.id }
                    StatisticsStageProgress(
                        label = detail?.let { state.goalProgressLabel(it, date) } ?: state.goalProgressLabel(stage, date),
                        feedbackLabels = state.stageFeedbackLabels(stage),
                    )
                },
            )
        },
    )
}

private val statisticsCadences = listOf(GoalCadence.DAILY, GoalCadence.WEEKLY, GoalCadence.MONTHLY)

private fun cumulativeMilestones(state: AttentionState): List<Milestone> = state.milestones
    .filter { it.kind == "cumulative" }
    .sortedWith(compareBy({ it.achievedAtEpochMillis }, { it.id }))
    .distinctBy { it.instanceKey }

private fun levelLines(experience: Long): List<String> {
    val levelProgress = levelProgressForExperience(experience)
    val levelSpan = levelProgress.nextLevelThreshold - levelProgress.currentLevelThreshold
    return listOf(
        "投入经验：$experience · 等级 ${levelProgress.level}",
        "当前等级区间进度：${levelProgress.progressExperience}/$levelSpan 点",
        "下一等级门槛：${levelProgress.nextLevelThreshold} 点",
        "剩余经验：${levelProgress.remainingExperience} 点",
        if (levelProgress.level > 1) {
            "升级反馈：已达到等级 ${levelProgress.level}"
        } else {
            "升级反馈：达到下一等级门槛后会显示"
        },
    )
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
    val hours = instanceKey.substringAfter("cumulative:", "").toLongOrNull()
    return if (hours != null) {
        "累计投入里程碑：$hours 小时 · +$reward 投入经验"
    } else {
        "累计投入里程碑：+$reward 投入经验"
    }
}