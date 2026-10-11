package com.attention.app.reminder

import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCoordinatorTest {
    @Test
    fun changed_reminder_cancels_old_alarm_before_scheduling_new_one() {
        val old = ScheduleEntry("schedule", "2026-10-11", "阅读", reminderEpochMillis = 1_000L)
        val next = old.copy(reminderEpochMillis = 2_000L)
        val alarms = RecordingAlarmPort()

        AlarmReminderCoordinator(alarms, nowMillis = { 0L }).sync(
            AttentionState(schedules = listOf(old)),
            AttentionState(schedules = listOf(next)),
        )

        assertEquals(listOf("cancel:schedule", "schedule:schedule:2000"), alarms.events)
    }

    @Test
    fun unchanged_reminder_is_idempotent_and_cleared_reminder_is_cancelled() {
        val entry = ScheduleEntry("schedule", "2026-10-11", "阅读", reminderEpochMillis = 2_000L)
        val alarms = RecordingAlarmPort()
        val coordinator = AlarmReminderCoordinator(alarms, nowMillis = { 0L })

        coordinator.sync(AttentionState(schedules = listOf(entry)), AttentionState(schedules = listOf(entry)))
        coordinator.sync(AttentionState(schedules = listOf(entry)), AttentionState(schedules = listOf(entry.copy(reminderEpochMillis = null))))

        assertEquals(listOf("cancel:schedule"), alarms.events)
    }

    @Test
    fun independent_schedule_ids_do_not_cancel_each_other() {
        val first = ScheduleEntry("first", "2026-10-11", "第一", reminderEpochMillis = 1_000L)
        val second = ScheduleEntry("second", "2026-10-11", "第二", reminderEpochMillis = 1_000L)
        val alarms = RecordingAlarmPort()

        AlarmReminderCoordinator(alarms, nowMillis = { 0L }).sync(
            AttentionState(schedules = listOf(first, second)),
            AttentionState(schedules = listOf(first.copy(reminderEpochMillis = 3_000L), second)),
        )

        assertEquals(listOf("cancel:first", "schedule:first:3000"), alarms.events)
    }

    @Test
    fun completion_change_keeps_existing_reminder_untouched() {
        val entry = ScheduleEntry("schedule", "2026-10-11", "阅读", reminderEpochMillis = 2_000L)
        val alarms = RecordingAlarmPort()

        AlarmReminderCoordinator(alarms).sync(
            AttentionState(schedules = listOf(entry)),
            AttentionState(schedules = listOf(entry.copy(completed = true))),
        )

        assertEquals(emptyList<String>(), alarms.events)
    }

    @Test
    fun past_reminder_is_cancelled_without_catch_up_schedule() {
        val entry = ScheduleEntry("schedule", "2026-10-11", "阅读", reminderEpochMillis = 1_000L)
        val alarms = RecordingAlarmPort()

        AlarmReminderCoordinator(alarms, nowMillis = { 2_000L }).sync(
            AttentionState(),
            AttentionState(schedules = listOf(entry)),
        )

        assertEquals(listOf("cancel:schedule"), alarms.events)
    }

    private class RecordingAlarmPort : ReminderAlarmPort {
        val events = mutableListOf<String>()

        override fun schedule(entry: ScheduleEntry) {
            events += "schedule:${entry.id}:${entry.reminderEpochMillis}"
        }

        override fun cancel(entryId: String) {
            events += "cancel:$entryId"
        }
    }
}
