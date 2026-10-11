package com.attention.app.reminder

import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry

/**
 * The one boundary between persisted schedule state and the platform alarm API.
 * Implementations must only observe a successfully persisted before/after pair.
 */
fun interface ReminderCoordinator {
    fun sync(previous: AttentionState, next: AttentionState)
}

interface ReminderAlarmPort {
    fun schedule(entry: ScheduleEntry)
    fun cancel(entryId: String)
}

/**
 * Reconciles alarms by stable schedule ID. A sync is deliberately idempotent:
 * unchanged entries produce no platform calls, while a changed entry first
 * removes its old PendingIntent and then schedules its new future trigger.
 */
class AlarmReminderCoordinator(
    private val alarms: ReminderAlarmPort,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ReminderCoordinator {
    override fun sync(previous: AttentionState, next: AttentionState) {
        val before = previous.schedules.associateBy(ScheduleEntry::id)
        val after = next.schedules.associateBy(ScheduleEntry::id)
        (before.keys + after.keys).forEach { id ->
            val old = before[id]
            val current = after[id]
            if (old?.reminderEpochMillis == current?.reminderEpochMillis &&
                old?.title == current?.title &&
                old?.planningDate == current?.planningDate
            ) return@forEach

            // Always cancel first so a changed title/time cannot retain the old
            // PendingIntent. The scheduler ignores cancellation when none exists.
            alarms.cancel(id)
            current?.let { entry ->
                if (entry.reminderEpochMillis?.let { it > nowMillis() } == true) alarms.schedule(entry)
            }
        }
    }
}
