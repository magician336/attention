package com.attention.app.data.backup

import kotlinx.serialization.Serializable

/*
 * version 1 备份协议**自己的**数据模型。
 *
 * 这些类型是备份文档的持久化载体，不是领域模型。协议以前直接用 `:domain` 的数据类承载字段，
 * 后果是「领域加一个字段就悄悄改变备份格式」，而且反序列化缺陷排查时会被引到一层并不存在的
 * 映射上。现在协议与领域之间隔着 [BackupDomainMapping] 里那层显式映射：领域可以自由演化，
 * 协议要变必须在这里改，并且改动的理由写在这里。
 *
 * 两条纪律：
 *
 * 1. 字段集合必须与对应领域类型一一对应。少一个字段，用户的恢复结果就少一块；多一个字段，
 *    它就是协议凭空发明的语义。两侧的一致性由 `BackupDomainMappingTest` 保证。
 * 2. 这里的默认值是**协议**的默认值，决定缺字段的历史备份读成什么。它们必须与领域类型的
 *    构造默认值一致，同一条测试也断言这一点。
 *
 * `cadence`、`frequency`、`source`、`launchDestination`、`lastOpenedDestination` 这五个字段由
 * 协议自己的枚举承载（见 [BackupProtocolEnums]），并由 [BackupDomainMapping] 与领域枚举互转。
 * 它们在线上就是字符串，所以「名字认不出来时读成什么」这条规则也归协议，不再借道领域类型。
 */

/**
 * 备份文档里的设置段：规划日界线、周起始日与每日容量。
 *
 * 默认值与领域 [com.attention.domain.StoredSettings] 相同：界线 03:00（180 分钟）、周一。
 */
@Serializable
data class BackupStoredSettings(
    val planningDayBoundaryMinutes: Int = DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: Int = DEFAULT_WEEK_START_DAY,
    val dailyCapacityMinutes: Int? = null,
) {
    companion object {
        /** 规划日界线默认 03:00，写死成字面量，避免协议模型反向依赖领域常量。 */
        const val DEFAULT_BOUNDARY_MINUTES = 3 * 60

        /** 周起始日默认周一（`DayOfWeek.MONDAY.value`）。 */
        const val DEFAULT_WEEK_START_DAY = 1
    }
}

/**
 * 目标树的一个节点。层级由 `parentId` 表达，归档由 `archived` 表达。
 *
 * `id` 没有默认值：备份文档里一行目标必须带着自己的 ID，缺失它是结构损坏，应当整份被拒，
 * 而不是导入时凭空生成一个新 ID 把用户的归属关系接到一个它从没出现过的节点上。
 */
@Serializable
data class BackupTarget(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val note: String = "",
    val icon: String? = null,
    val colorHex: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val expanded: Boolean = true,
)

/** 目标阶段：一段独立的目标量、周期与达成状态。 */
@Serializable
data class BackupGoalStage(
    val id: String,
    val targetId: String,
    // 协议自己的枚举：名字认不出来时读成声明过的默认值，见 BackupProtocolEnums。
    val cadence: BackupGoalCadence,
    val targetMinutes: Int,
    val startDate: String,
    val dueDate: String? = null,
    val completed: Boolean = false,
)

/** 时间记录：一笔实际投入。 */
@Serializable
data class BackupTimeEntry(
    val id: String,
    val planningDate: String,
    val durationMinutes: Int,
    val targetId: String? = null,
    // 协议自己的枚举：名字认不出来时读成声明过的默认值，见 BackupProtocolEnums。
    val source: BackupTimeEntrySource = BackupTimeEntrySource.MANUAL,
    val occurredAtEpochMillis: Long? = null,
    val note: String = "",
    val scheduleEntryId: String? = null,
)

/** 日程条目：用户安排在某个规划日要做的事。与时间记录不是一回事。 */
@Serializable
data class BackupScheduleEntry(
    val id: String,
    val planningDate: String,
    val title: String,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val estimatedMinutes: Int? = null,
    val note: String = "",
    val targetId: String? = null,
    val completed: Boolean = false,
    val reminderEpochMillis: Long? = null,
    val recurrenceRuleId: String? = null,
)

/** 重复日程的规则本身，决定未来还会生成哪些日程条目。 */
@Serializable
data class BackupRecurrenceRule(
    val id: String,
    val title: String,
    val startDate: String,
    // 协议自己的枚举：名字认不出来时读成声明过的默认值，见 BackupProtocolEnums。
    val frequency: BackupScheduleFrequency,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val untilDate: String? = null,
    val active: Boolean = true,
    val estimatedMinutes: Int? = null,
    val targetId: String? = null,
    val note: String = "",
    val reminderMinuteOfDay: Int? = null,
)

/** 目标迁移：把当前周期的剩余目标安排到未来周期或日期范围。 */
@Serializable
data class BackupMigration(
    val id: String,
    val targetId: String,
    val sourceStageId: String,
    val minutes: Int,
    val destinationStartDate: String,
    val destinationEndDate: String? = null,
    val cancelled: Boolean = false,
)

/** 未来目标规则：从指定日期或周期起生效的新目标量，不回写已经开始的周期。 */
@Serializable
data class BackupFutureGoalRule(
    val id: String,
    val targetId: String,
    val stageId: String = "",
    // 协议自己的枚举：名字认不出来时读成声明过的默认值，见 BackupProtocolEnums。
    val cadence: BackupGoalCadence,
    val targetMinutes: Int,
    val effectiveFrom: String,
    val dueDate: String? = null,
)

/** 周期快照：一个目标周期当时适用的目标值与结果，作为历史计算依据保留。 */
@Serializable
data class BackupPeriodSnapshot(
    val id: String,
    val targetId: String,
    // 协议自己的枚举：名字认不出来时读成声明过的默认值，见 BackupProtocolEnums。
    val cadence: BackupGoalCadence,
    val periodStart: String,
    val periodEnd: String,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
    val stageId: String = "",
)

/** 目标移动：从指定未来日期起改变目标树里的父子关系。 */
@Serializable
data class BackupTargetMove(
    val id: String,
    val targetId: String,
    val parentId: String?,
    val effectiveFrom: String,
)

/** 达成里程碑：一次性反馈，按 `instanceKey` 去重，属于用户的历史。 */
@Serializable
data class BackupMilestone(
    val id: String,
    val kind: String,
    val instanceKey: String,
    val reward: Long,
    val achievedAtEpochMillis: Long,
)

/** 正在运行的计时现场，以及已经完成的计时片段。 */
@Serializable
data class BackupActiveTimer(
    val targetId: String?,
    val startedAtEpochMillis: Long,
    val accumulatedMillis: Long = 0,
    val paused: Boolean = false,
    val lastPlanningDate: String,
    val completedSegments: List<BackupTimerSegment> = emptyList(),
    val scheduleEntryId: String? = null,
)

/** 计时器里已经完成的一段计时。累计时长由这些片段累加而来。 */
@Serializable
data class BackupTimerSegment(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
)
