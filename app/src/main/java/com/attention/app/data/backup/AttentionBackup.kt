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
        milestones = milestones,
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
            val canonical = state.recalculateExperience()
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

object AttentionBackupCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: AttentionState): String = json.encodeToString(AttentionBackup.fromDomain(state))

    fun decode(encoded: String): AttentionState {
        val root = json.parseToJsonElement(encoded).jsonObject
        require("version" in root) { "备份缺少版本字段" }
        val backup = json.decodeFromString<AttentionBackup>(encoded)
        require(backup.version == AttentionBackup.CURRENT_VERSION) {
            "不支持的备份版本：${backup.version}"
        }
        return backup.toDomain()
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
        milestones = mergeById(current.milestones, incoming.milestones) { it.id },
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
}
