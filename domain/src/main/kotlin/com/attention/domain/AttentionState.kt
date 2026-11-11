package com.attention.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable

/*
 * 这个文件里的数据类一律不再标 `@Serializable`：备份格式由备份协议自己的模型承载（见 ADR 0005
 * 「备份协议的边界」），领域加一个字段不应该自动改变用户手上的备份文件。以前标注在这里的作用只剩
 * 「给字段对照测试读描述符」，那是测试自己的需求，不该反过来成为领域的义务——今天对照测试已经改用
 * 反射读构造参数，标注也就没有留下的理由。
 */

data class StoredSettings(
    val planningDayBoundaryMinutes: Int = PlannerSettings.DEFAULT_BOUNDARY_MINUTES,
    val weekStartDay: Int = DayOfWeek.MONDAY.value,
    val dailyCapacityMinutes: Int? = null,
) {
    fun toPlannerSettings(): PlannerSettings = PlannerSettings(
        planningDayBoundaryMinutes = planningDayBoundaryMinutes,
        weekStartDay = DayOfWeek.of(weekStartDay.coerceIn(1, 7)),
        dailyCapacityMinutes = dailyCapacityMinutes,
    )
}

/*
 * 下面四个枚举是**按名持久化**的枚举，但领域类型本身不再标 `@Serializable`：
 * 按名解析与「名字认不出来时读成什么」是持久化层的知识，规则写在 [PersistedEnumSerializer]
 * （Room 的枚举列与 DataStore 的启动入口偏好用它），备份 JSON 用的是备份协议自己那一份
 * （`app` 的 `BackupProtocolEnums`）。两侧对同一个名字必须给出同一个值，由单元测试对照。
 * 标 `@Serializable(with = ...)` 会让「为什么领域枚举身上挂着一个只对存储有意义的序列化器」
 * 变成一个看不出理由的既成事实。
 */
enum class GoalCadence { DAILY, WEEKLY, MONTHLY, ONE_TIME }

enum class TimeEntrySource { MANUAL, TIMER, SCHEDULE_CONFIRMED, IMPORT }

enum class ScheduleFrequency { DAILY, WEEKLY, MONTHLY }

/**
 * 启动入口。[AGENDA] 是「日程」的正式语义；[TODAY] 与 [ALL_DATES] 是旧版本持久化的入口值，
 * 它们仍然存在并按旧页面打开，删除它们属于后续的收缩工作。
 */
enum class LaunchDestination { TODAY, TARGETS, ALL_DATES, STATISTICS, LAST_OPENED, AGENDA }

data class Target(
    val id: String = newId(),
    val parentId: String? = null,
    val title: String,
    val note: String = "",
    val icon: String? = null,
    val colorHex: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val expanded: Boolean = true,
)

data class GoalStage(
    val id: String = newId(),
    val targetId: String,
    val cadence: GoalCadence,
    val targetMinutes: Int,
    val startDate: String,
    val dueDate: String? = null,
    val completed: Boolean = false,
)

data class TimeEntry(
    val id: String = newId(),
    val planningDate: String,
    val durationMinutes: Int,
    val targetId: String? = null,
    val source: TimeEntrySource = TimeEntrySource.MANUAL,
    val occurredAtEpochMillis: Long? = null,
    val note: String = "",
    val scheduleEntryId: String? = null,
    /**
     * 事后补记这一次提交意图的标识。
     *
     * 只有 [TimeEntrySource.MANUAL] 的事后补记带它；它回答的是「这一次提交是否已经落库」，
     * 而不是「这条日程是否已经有投入」。同一次保存重试、双击或事务重放时沿用同一个值，
     * 因此重放只会命中已有记录而不会再记一笔；下一次新的补记则是新的值，必然被记录。
     */
    val backfillRequestId: String? = null,
)

data class ScheduleEntry(
    val id: String = newId(),
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

data class RecurrenceRule(
    val id: String = newId(),
    val title: String,
    val startDate: String,
    val frequency: ScheduleFrequency,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int? = null,
    val untilDate: String? = null,
    val active: Boolean = true,
    val estimatedMinutes: Int? = null,
    val targetId: String? = null,
    val note: String = "",
    val reminderMinuteOfDay: Int? = null,
)

data class Migration(
    val id: String = newId(),
    val targetId: String,
    val sourceStageId: String,
    val minutes: Int,
    val destinationStartDate: String,
    val destinationEndDate: String? = null,
    val cancelled: Boolean = false,
)

data class FutureGoalRule(
    val id: String = newId(),
    val targetId: String,
    val stageId: String = "",
    val cadence: GoalCadence,
    val targetMinutes: Int,
    val effectiveFrom: String,
    val dueDate: String? = null,
)

data class PeriodSnapshot(
    val id: String = newId(),
    val targetId: String,
    val cadence: GoalCadence,
    val periodStart: String,
    val periodEnd: String,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
    val stageId: String = "",
)

data class TargetMove(
    val id: String = newId(),
    val targetId: String,
    val parentId: String?,
    val effectiveFrom: String,
)

data class ActiveTimer(
    val targetId: String?,
    val startedAtEpochMillis: Long,
    val accumulatedMillis: Long = 0,
    val paused: Boolean = false,
    val lastPlanningDate: String,
    val completedSegments: List<TimerSegment> = emptyList(),
    val scheduleEntryId: String? = null,
)

/**
 * 计时器里已经完成的一段计时。
 *
 * 这个类型仍然可序列化，而且不是为备份：Room 的 `ActiveTimerEntity` 把计时片段整列存成 JSON，
 * 经 `BusinessMappers` 的 `roomJson` 编解码（生产路径）。摘掉标注会让运行时计时现场读不回来。
 */
@Serializable
data class TimerSegment(val startedAtEpochMillis: Long, val endedAtEpochMillis: Long)

data class Milestone(
    val id: String = newId(),
    val kind: String,
    val instanceKey: String,
    val reward: Long,
    val achievedAtEpochMillis: Long,
)

data class AttentionState(
    val settings: StoredSettings = StoredSettings(),
    val targets: List<Target> = emptyList(),
    val goalStages: List<GoalStage> = emptyList(),
    val timeEntries: List<TimeEntry> = emptyList(),
    val schedules: List<ScheduleEntry> = emptyList(),
    val recurrenceRules: List<RecurrenceRule> = emptyList(),
    val migrations: List<Migration> = emptyList(),
    val futureGoalRules: List<FutureGoalRule> = emptyList(),
    val periodSnapshots: List<PeriodSnapshot> = emptyList(),
    val targetMoves: List<TargetMove> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val experience: Long = 0,
    val activeTimer: ActiveTimer? = null,
    val onboardingCompleted: Boolean = false,
    // 空状态的入口默认是日程：旧值 TODAY 语义等价于日程，但继续以它为默认
    // 会让新写出的备份持续带旧入口名。旧枚举值本身仍然保留（见 [LaunchDestination]）。
    val launchDestination: LaunchDestination = LaunchDestination.AGENDA,
    val lastOpenedDestination: LaunchDestination = LaunchDestination.AGENDA,
    val notificationsEnabled: Boolean = true,
)

/** Counts the records affected by removing a target subtree. */
data class TargetDeletionImpact(
    val targetCount: Int,
    val timeEntryCount: Int,
    val scheduleEntryCount: Int,
    val recurrenceRuleCount: Int,
    val goalStageCount: Int,
    val futureGoalRuleCount: Int,
    val migrationCount: Int,
    val targetMoveCount: Int,
    val activeTimerCount: Int,
)

/**
 * 标识用的逻辑时钟。它只在 [newId] 内部被读写，不参与任何领域规则。
 *
 * 取「本次时刻」与「上一次加一」中的较大者，于是时钟回拨与同毫秒并发都被吸收：
 * 回拨时逻辑时钟停在原处继续加一，并发时靠 CAS 保证只有一条线程前进一步。
 * 重启后它归零，而系统时刻已经远大于零，所以归零不会与旧 id 撞车。
 */
private val idLogicalClock = java.util.concurrent.atomic.AtomicLong(0L)

private const val ID_CLOCK_WIDTH = 9
private const val ID_RANDOM_LENGTH = 10

/**
 * 生成一个全局唯一、并且字典序与创建顺序一致的标识。
 *
 * 随机 UUID 满足唯一性却不满足顺序：读路径大量用 `ORDER BY ... , id` 收尾，
 * 而同一规划日里没有精确发生时刻的时间记录——事后补记与日程完成确认——
 * `occurredAtEpochMillis` 为 null，会在排序里并列，于是先后落到 id 上。
 * id 随机就意味着同一份数据每次读出来的顺序都可能不同，重启之后还会换位置，
 * 用户看到的时间记录列表会自己跳动。
 *
 * 标识由「定长 base36 的逻辑时钟」加一段随机尾巴拼成：定长保证字典序就是创建顺序，
 * 尾段保证同一微秒内也不会重复。
 *
 * [nowMillis] 可注入，因此测试可以固定标识的时间前缀。
 */
fun newId(nowMillis: Long = System.currentTimeMillis()): String {
    while (true) {
        val previous = idLogicalClock.get()
        val next = if (nowMillis > previous) nowMillis else previous + 1
        if (idLogicalClock.compareAndSet(previous, next)) {
            val clock = next.toString(36).padStart(ID_CLOCK_WIDTH, '0')
            val random = UUID.randomUUID().toString().replace("-", "").take(ID_RANDOM_LENGTH)
            return clock + random
        }
    }
}

data class GoalProgress(
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
)

data class LevelProgress(
    val level: Int,
    val currentLevelThreshold: Long,
    val nextLevelThreshold: Long,
    val progressExperience: Long,
    val remainingExperience: Long,
)

data class GoalStageSummary(
    val stage: GoalStage,
    val progress: GoalProgress,
)

data class GoalSummary(
    val targetId: String,
    val stageProgresses: List<GoalStageSummary>,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val gapMinutes: Int,
    val excessMinutes: Int,
    val completed: Boolean,
)

data class CapacitySummary(val capacityMinutes: Int?, val usedMinutes: Int, val overloaded: Boolean)

data class PeriodStats(
    val range: ClosedRange<LocalDate>,
    val targetMinutes: Int,
    val actualMinutes: Int,
    val unownedMinutes: Int,
    val migrationMinutes: Int,
    val excessMinutes: Int,
    val byTargetMinutes: Map<String, Int>,
    val subtreeByTargetMinutes: Map<String, Int> = emptyMap(),
)

data class TimerResult(val state: AttentionState, val entries: List<TimeEntry>)

/**
 * Read model for one time record of a planning day. A record whose linked schedule entry no
 * longer exists stays visible and says so instead of being dropped or throwing.
 */
data class TimeEntryRow(
    val entry: TimeEntry,
    val source: TimeEntrySource,
    val schedule: ScheduleEntry?,
) {
    val hasPreciseTiming: Boolean get() = source == TimeEntrySource.TIMER && entry.occurredAtEpochMillis != null
    val scheduleMissing: Boolean get() = entry.scheduleEntryId != null && schedule == null
    val scheduleLabel: String get() = when {
        entry.scheduleEntryId == null -> ""
        schedule != null -> schedule.title
        else -> "所属日程已删除"
    }
}

data class TimerSettlementPreview(val entries: List<TimeEntry>) {
    val totalMinutes: Int get() = entries.sumOf { it.durationMinutes }
    val hasCompleteMinutes: Boolean get() = entries.isNotEmpty()
}
