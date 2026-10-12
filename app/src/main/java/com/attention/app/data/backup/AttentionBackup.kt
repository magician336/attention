package com.attention.app.data.backup

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.FutureGoalRule
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.Migration
import com.attention.domain.Milestone
import com.attention.domain.PeriodSnapshot
import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TargetMove
import com.attention.domain.TimeEntry
import com.attention.domain.recalculateExperience
import com.attention.domain.validateActiveTimerTarget
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Stable, versioned JSON protocol. Domain objects are never serialized directly for backups. */
@Serializable
data class AttentionBackup(
    val version: Int = CURRENT_VERSION,
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
    val launchDestination: LaunchDestination = LaunchDestination.TODAY,
    val lastOpenedDestination: LaunchDestination = LaunchDestination.TODAY,
    val notificationsEnabled: Boolean = true,
) {
    fun toDomain(): AttentionState = AttentionState(
        settings = settings,
        targets = targets,
        goalStages = goalStages,
        timeEntries = timeEntries,
        schedules = schedules,
        recurrenceRules = recurrenceRules,
        migrations = migrations,
        futureGoalRules = futureGoalRules,
        periodSnapshots = periodSnapshots,
        targetMoves = targetMoves,
        // The instance key is the domain ledger identity. Backups may have
        // been produced by older versions that retained duplicate rows, so
        // normalize them at the import boundary before deriving experience.
        milestones = milestones.distinctBy { it.instanceKey },
        experience = experience,
        activeTimer = activeTimer,
        onboardingCompleted = onboardingCompleted,
        launchDestination = launchDestination,
        lastOpenedDestination = lastOpenedDestination,
        notificationsEnabled = notificationsEnabled,
    ).recalculateExperience()

    companion object {
        const val CURRENT_VERSION = 1

        fun fromDomain(state: AttentionState): AttentionBackup {
            // Normalize the milestone ledger before deriving experience so the
            // serialized rows and the saved total are computed from the same
            // canonical set of instance keys.
            val canonical = state.copy(
                milestones = state.milestones.distinctBy { it.instanceKey },
            ).recalculateExperience()
            return AttentionBackup(
                settings = canonical.settings,
                targets = canonical.targets,
                goalStages = canonical.goalStages,
                timeEntries = canonical.timeEntries,
                schedules = canonical.schedules,
                recurrenceRules = canonical.recurrenceRules,
                migrations = canonical.migrations,
                futureGoalRules = canonical.futureGoalRules,
                periodSnapshots = canonical.periodSnapshots,
                targetMoves = canonical.targetMoves,
                milestones = canonical.milestones,
                experience = canonical.experience,
                activeTimer = canonical.activeTimer,
                onboardingCompleted = canonical.onboardingCompleted,
                launchDestination = canonical.launchDestination,
                lastOpenedDestination = canonical.lastOpenedDestination,
                notificationsEnabled = canonical.notificationsEnabled,
            )
        }
    }
}

/** Lightweight, validated metadata shown before an import is committed. */
data class BackupPreview(
    val version: Int,
    val targets: Int,
    val goalStages: Int,
    val timeEntries: Int,
    val schedules: Int,
    val recurrenceRules: Int,
    val migrations: Int,
    val futureGoalRules: Int,
    val periodSnapshots: Int,
    val targetMoves: Int,
    val milestones: Int,
    val hasActiveTimer: Boolean,
    val hasSettings: Boolean,
) {
    val totalRecords: Int
        get() = targets + goalStages + timeEntries + schedules + recurrenceRules + migrations +
            futureGoalRules + periodSnapshots + targetMoves + milestones
}

object AttentionBackupCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: AttentionState): String = json.encodeToString(AttentionBackup.fromDomain(state))

    fun decode(encoded: String): AttentionState {
        return parseValidated(encoded).toDomain()
    }

    /** Parses and validates a backup without mutating any repository state. */
    fun preview(encoded: String): BackupPreview {
        val root = json.parseToJsonElement(encoded).jsonObject
        val backup = parseValidated(encoded)
        return BackupPreview(
            version = backup.version,
            targets = backup.targets.size,
            goalStages = backup.goalStages.size,
            timeEntries = backup.timeEntries.size,
            schedules = backup.schedules.size,
            recurrenceRules = backup.recurrenceRules.size,
            migrations = backup.migrations.size,
            futureGoalRules = backup.futureGoalRules.size,
            periodSnapshots = backup.periodSnapshots.size,
            targetMoves = backup.targetMoves.size,
            milestones = backup.milestones.distinctBy { it.instanceKey }.size,
            hasActiveTimer = backup.activeTimer != null,
            hasSettings = "settings" in root,
        )
    }

    private fun parseValidated(encoded: String): AttentionBackup {
        val root = json.parseToJsonElement(encoded).jsonObject
        require("version" in root) { "备份缺少版本字段" }
        val backup = json.decodeFromString<AttentionBackup>(encoded)
        require(backup.version == AttentionBackup.CURRENT_VERSION) {
            "不支持的备份版本：${backup.version}"
        }
        validate(backup.toDomain())
        backup.activeTimer?.validate()
        return backup
    }

    /** Validates every cross-entity reference before adapters write any rows. */
    fun validate(state: AttentionState) {
        require(state.settings.planningDayBoundaryMinutes in 0 until 24 * 60) { "规划日边界无效" }
        require(state.settings.weekStartDay in 1..7) { "周起始日无效" }
        val dailyCapacity = state.settings.dailyCapacityMinutes
        require(dailyCapacity == null || dailyCapacity > 0) { "每日容量必须为正数" }
        fun <T> unique(values: List<T>, id: (T) -> String, name: String) {
            require(values.all { id(it).isNotBlank() }) { "$name ID 不能为空" }
            require(values.map(id).toSet().size == values.size) { "$name ID 必须唯一" }
        }
        unique(state.targets, Target::id, "目标")
        unique(state.goalStages, GoalStage::id, "阶段")
        unique(state.timeEntries, TimeEntry::id, "时间记录")
        unique(state.schedules, ScheduleEntry::id, "日程")
        unique(state.recurrenceRules, RecurrenceRule::id, "重复规则")
        unique(state.migrations, Migration::id, "迁移")
        unique(state.futureGoalRules, FutureGoalRule::id, "未来规则")
        unique(state.periodSnapshots, PeriodSnapshot::id, "周期快照")
        unique(state.targetMoves, TargetMove::id, "目标移动")
        unique(state.milestones, Milestone::id, "里程碑")
        val targets = state.targets.map { it.id }.toSet()
        state.targets.forEach { target ->
            require(target.title.isNotBlank()) { "目标标题不能为空" }
            require(target.parentId == null || target.parentId in targets) { "目标父级不存在" }
        }
        state.targets.forEach { target ->
            val seen = mutableSetOf<String>()
            var current: String? = target.id
            while (current != null) {
                require(seen.add(current)) { "目标层级不能形成循环" }
                current = state.targets.firstOrNull { it.id == current }?.parentId
            }
        }
        fun date(value: String): LocalDate = runCatching { LocalDate.parse(value) }
            .getOrElse { throw IllegalArgumentException("日期格式无效：$value", it) }
        state.goalStages.forEach { stage ->
            require(stage.targetId in targets) { "阶段目标不存在" }
            require(stage.targetMinutes > 0) { "阶段目标分钟必须为正数" }
            val start = date(stage.startDate)
            val due = stage.dueDate
            require(due == null || !date(due).isBefore(start)) { "阶段截止日期不能早于开始日期" }
        }
        val stages = state.goalStages.map { it.id }.toSet()
        state.timeEntries.forEach { entry ->
            date(entry.planningDate)
            require(entry.durationMinutes > 0) { "时间记录时长必须为正数" }
            require(entry.targetId == null || entry.targetId in targets) { "时间记录目标不存在" }
        }
        val rules = state.recurrenceRules.map { it.id }.toSet()
        state.schedules.forEach { entry ->
            require(entry.title.isNotBlank()) { "日程标题不能为空" }
            date(entry.planningDate)
            require((entry.startMinute == null) == (entry.endMinute == null)) { "日程时间段必须完整" }
            require(entry.startMinute == null || entry.startMinute in 0 until 1440) { "日程开始时间无效" }
            require(entry.endMinute == null || entry.endMinute in 1..1440) { "日程结束时间无效" }
            require(entry.startMinute == null || entry.endMinute!! > entry.startMinute!!) { "日程结束时间必须晚于开始时间" }
            val estimate = entry.estimatedMinutes
            require(estimate == null || estimate > 0) { "日程预计时长必须为正数" }
            require(entry.targetId == null || entry.targetId in targets) { "日程目标不存在" }
            require(entry.recurrenceRuleId == null || entry.recurrenceRuleId in rules) { "日程重复规则不存在" }
        }
        state.recurrenceRules.forEach { rule ->
            require(rule.title.isNotBlank()) { "重复规则标题不能为空" }
            val start = date(rule.startDate)
            val until = rule.untilDate
            require(until == null || !date(until).isBefore(start)) { "重复规则结束日期无效" }
            val estimate = rule.estimatedMinutes
            require(estimate == null || estimate > 0) { "重复规则预计时长必须为正数" }
            require(rule.reminderMinuteOfDay == null || rule.reminderMinuteOfDay in 0 until 1440) { "重复规则提醒时间无效" }
            require(rule.targetId == null || rule.targetId in targets) { "重复规则目标不存在" }
            when (rule.frequency) {
                com.attention.domain.ScheduleFrequency.DAILY -> require(rule.weekdays.isEmpty() && rule.dayOfMonth == null) { "每日规则参数无效" }
                com.attention.domain.ScheduleFrequency.WEEKLY -> require(rule.weekdays.isNotEmpty() && rule.weekdays.all { it in 1..7 } && rule.dayOfMonth == null) { "每周规则参数无效" }
                com.attention.domain.ScheduleFrequency.MONTHLY -> require(rule.weekdays.isEmpty() && rule.dayOfMonth in 1..31) { "每月规则参数无效" }
            }
        }
        state.migrations.forEach { migration ->
            require(migration.targetId in targets) { "迁移目标不存在" }
            require(migration.sourceStageId in stages && state.goalStages.first { it.id == migration.sourceStageId }.targetId == migration.targetId) { "迁移来源阶段不存在" }
            require(migration.minutes > 0) { "迁移分钟必须为正数" }
            val start = date(migration.destinationStartDate)
            val end = migration.destinationEndDate
            require(end == null || !date(end).isBefore(start)) { "迁移日期无效" }
        }
        state.futureGoalRules.forEach { rule ->
            require(rule.targetId in targets) { "未来规则目标不存在" }
            require(rule.stageId.isBlank() || rule.stageId in stages && state.goalStages.first { it.id == rule.stageId }.targetId == rule.targetId) { "未来规则阶段不存在" }
            require(rule.targetMinutes > 0) { "未来规则目标分钟必须为正数" }
            val effective = date(rule.effectiveFrom)
            val due = rule.dueDate
            require(due == null || !date(due).isBefore(effective)) { "未来规则日期无效" }
        }
        state.periodSnapshots.forEach { snapshot ->
            require(snapshot.targetId in targets) { "周期快照目标不存在" }
            require(snapshot.stageId.isBlank() || snapshot.stageId in stages) { "周期快照阶段不存在" }
            require(snapshot.stageId.isBlank() || state.goalStages.first { it.id == snapshot.stageId }.targetId == snapshot.targetId) {
                "周期快照阶段与目标不匹配"
            }
            require(!date(snapshot.periodEnd).isBefore(date(snapshot.periodStart))) { "周期快照日期无效" }
        }
        state.targetMoves.forEach { move ->
            require(move.targetId in targets) { "目标移动目标不存在" }
            require(move.parentId == null || move.parentId in targets) { "目标移动父级不存在" }
            require(move.parentId != move.targetId) { "目标移动不能指向自身" }
            date(move.effectiveFrom)
        }
        val timerTargetId = state.activeTimer?.targetId
        require(timerTargetId == null || state.targets.any { it.id == timerTargetId }) { "活动计时器目标不存在" }
        state.validateActiveTimerTarget()
    }

    /**
     * Timer context is persisted as user data, so reject impossible values
     * before an import reaches Room. This keeps malformed segments from being
     * treated as zero-duration work when the restored timer is stopped.
     */
    private fun ActiveTimer.validate() {
        require(accumulatedMillis >= 0) { "活动计时器累计时长不能为负数" }
        completedSegments.forEach { segment ->
            require(segment.startedAtEpochMillis <= segment.endedAtEpochMillis) {
                "活动计时器完成片段的结束时间不能早于开始时间"
            }
        }
    }

    fun merge(current: AttentionState, incoming: AttentionState): AttentionState = current.copy(
        settings = incoming.settings,
        targets = mergeById(current.targets, incoming.targets) { it.id },
        goalStages = mergeById(current.goalStages, incoming.goalStages) { it.id },
        timeEntries = mergeById(current.timeEntries, incoming.timeEntries) { it.id },
        schedules = mergeById(current.schedules, incoming.schedules) { it.id },
        recurrenceRules = mergeById(current.recurrenceRules, incoming.recurrenceRules) { it.id },
        migrations = mergeById(current.migrations, incoming.migrations) { it.id },
        futureGoalRules = mergeById(current.futureGoalRules, incoming.futureGoalRules) { it.id },
        periodSnapshots = mergeById(current.periodSnapshots, incoming.periodSnapshots) { it.id },
        targetMoves = mergeById(current.targetMoves, incoming.targetMoves) { it.id },
        milestones = mergeMilestones(current.milestones, incoming.milestones),
        experience = 0,
        activeTimer = incoming.activeTimer,
        onboardingCompleted = incoming.onboardingCompleted,
        launchDestination = incoming.launchDestination,
        lastOpenedDestination = incoming.lastOpenedDestination,
        notificationsEnabled = incoming.notificationsEnabled,
    ).recalculateExperience()

    private fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> String): List<T> {
        val result = existing.toMutableList()
        val ids = existing.mapTo(mutableSetOf(), id)
        incoming.forEach { if (ids.add(id(it))) result += it }
        return result
    }

    private fun mergeMilestones(existing: List<Milestone>, incoming: List<Milestone>): List<Milestone> {
        val result = existing.distinctBy { it.instanceKey }.toMutableList()
        val keys = result.mapTo(mutableSetOf()) { it.instanceKey }
        val ids = result.mapTo(mutableSetOf()) { it.id }
        incoming.forEach { milestone ->
            if (milestone.instanceKey !in keys && milestone.id !in ids) {
                keys += milestone.instanceKey
                ids += milestone.id
                result += milestone
            }
        }
        return result
    }
}
