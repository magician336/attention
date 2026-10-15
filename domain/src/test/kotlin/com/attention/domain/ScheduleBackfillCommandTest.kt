package com.attention.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存日程条目时顺带事后补记已经发生的实际投入（#187）。
 *
 * 用例只从用户视角断言：日程条目与那笔补记要么一起出现，要么都不出现；
 * 反复编辑同一条日程补填时间不会凭空多出第二笔投入，投入经验与达成里程碑也不被重复奖励。
 */
class ScheduleBackfillCommandTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 10, 8)
    private val now = Instant.parse("2026-10-08T12:00:00Z")

    private fun stateWithTarget(
        targetMinutes: Int = 30,
        archived: Boolean = false,
    ): Pair<AttentionState, Target> {
        val target = Target("reading", title = "阅读", archived = archived)
        val stage = GoalStage("stage", target.id, GoalCadence.ONE_TIME, targetMinutes, date.toString())
        return AttentionState(targets = listOf(target), goalStages = listOf(stage)) to target
    }

    private fun newEntry(targetId: String? = "reading") = ScheduleEntry(
        id = "schedule-1",
        planningDate = date.toString(),
        title = "读一章",
        estimatedMinutes = 30,
        targetId = targetId,
    )

    @Test
    fun saving_a_schedule_with_backfill_creates_one_linked_manual_record_in_one_step() {
        val (state, target) = stateWithTarget()

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id, note = "顺手补记"),
        ).execute(state, now, zone)

        assertEquals(listOf(newEntry(target.id)), result.state.schedules)
        val record = result.generatedTimeEntries.single()
        assertEquals(TimeEntrySource.MANUAL, record.source)
        assertEquals(newEntry(target.id).id, record.scheduleEntryId)
        assertEquals(date.toString(), record.planningDate)
        assertEquals(45, record.durationMinutes)
        assertEquals(target.id, record.targetId)
        assertEquals("顺手补记", record.note)
        // 事后补记没有精确发生时间：它是按日程条目确认的，不是计时测出来的。
        assertNull(record.occurredAtEpochMillis)
        assertEquals(listOf(record), result.state.timeEntries)
        assertEquals(listOf("one_time_goal:stage:${date}"), result.newMilestones.map { it.instanceKey })
        assertEquals(now.toEpochMilli(), result.newMilestones.single().achievedAtEpochMillis)
        assertEquals(10_045L, result.state.experience)
    }

    @Test
    fun backfill_can_stay_unowned_and_then_be_assigned_later() {
        val (state, target) = stateWithTarget()

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = ScheduleBackfill(durationMinutes = 45),
        ).execute(state, now, zone)

        val record = result.generatedTimeEntries.single()
        assertNull(record.targetId)
        assertEquals(newEntry(target.id).id, record.scheduleEntryId)
        // 未归属活动参与统计，但不推进具体计划，因此没有基础经验也没有达成里程碑。
        assertEquals(0L, result.state.experience)
        assertTrue(result.newMilestones.isEmpty())

        val assigned = AttentionCommand.AssignUnowned(setOf(record.id), target.id).execute(result.state, now, zone)
        assertEquals(target.id, assigned.state.timeEntries.single().targetId)
        assertEquals(newEntry(target.id).id, assigned.state.timeEntries.single().scheduleEntryId)
    }

    @Test
    fun backfill_rejects_archived_or_missing_target() {
        val (archivedState, archivedTarget) = stateWithTarget(archived = true)
        val archived = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(
                entry = newEntry(archivedTarget.id),
                backfill = ScheduleBackfill(durationMinutes = 45, targetId = archivedTarget.id),
            ).execute(archivedState, now, zone)
        }
        assertEquals("时间记录只能归入未归档目标", archived.message)

        val (state, _) = stateWithTarget()
        val missing = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(
                entry = newEntry(null),
                backfill = ScheduleBackfill(durationMinutes = 45, targetId = "missing"),
            ).execute(state, now, zone)
        }
        assertEquals("时间记录只能归入未归档目标", missing.message)
    }

    @Test
    fun backfill_rejects_non_positive_duration() {
        val (state, target) = stateWithTarget()

        val error = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(
                entry = newEntry(target.id),
                backfill = ScheduleBackfill(durationMinutes = 0, targetId = target.id),
            ).execute(state, now, zone)
        }

        assertEquals("时间记录时长必须为正数", error.message)
    }

    @Test
    fun saving_a_schedule_without_backfill_only_saves_the_schedule() {
        val (state, target) = stateWithTarget()

        val result = AttentionCommand.SaveSchedule(newEntry(target.id)).execute(state, now, zone)

        assertEquals(listOf(newEntry(target.id)), result.state.schedules)
        assertTrue(result.generatedTimeEntries.isEmpty())
        assertTrue(result.newMilestones.isEmpty())
        assertEquals(0L, result.state.experience)
    }

    @Test
    fun editing_the_same_schedule_again_never_creates_a_second_investment() {
        val (state, target) = stateWithTarget()
        val first = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id),
        ).execute(state, now, zone)

        val second = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id).copy(title = "读两章"),
            backfill = ScheduleBackfill(durationMinutes = 90, targetId = target.id),
        ).execute(first.state, now.plusSeconds(60), zone)

        // 日程条目本身照常保存。
        assertEquals("读两章", second.state.schedules.single().title)
        // 但第二笔投入不成立：第一笔仍是唯一的事实。
        assertEquals(first.generatedTimeEntries, second.state.timeEntries)
        assertTrue(second.generatedTimeEntries.isEmpty())
        assertTrue(second.newMilestones.isEmpty())
        assertEquals(10_045L, second.state.experience)
        assertEquals(first.state.milestones, second.state.milestones)
    }

    @Test
    fun a_schedule_already_backfilled_by_the_timer_also_blocks_a_second_record() {
        val (state, target) = stateWithTarget()
        val timed = AttentionState(
            targets = state.targets,
            goalStages = state.goalStages,
            schedules = listOf(newEntry(target.id)),
            timeEntries = listOf(
                TimeEntry(
                    planningDate = date.toString(),
                    durationMinutes = 20,
                    targetId = target.id,
                    source = TimeEntrySource.TIMER,
                    occurredAtEpochMillis = now.minusSeconds(1_200).toEpochMilli(),
                    scheduleEntryId = newEntry(target.id).id,
                ),
            ),
        )

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id),
        ).execute(timed, now, zone)

        assertEquals(timed.timeEntries, result.state.timeEntries)
        assertTrue(result.generatedTimeEntries.isEmpty())
    }

    @Test
    fun backfilling_an_existing_schedule_still_creates_the_record() {
        val (state, target) = stateWithTarget()
        val saved = state.addSchedule(newEntry(target.id))

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id).copy(note = "改过备注"),
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id),
        ).execute(saved, now, zone)

        assertEquals(1, result.state.schedules.size)
        assertEquals("改过备注", result.state.schedules.single().note)
        assertEquals(45, result.generatedTimeEntries.single().durationMinutes)
        assertEquals(newEntry(target.id).id, result.generatedTimeEntries.single().scheduleEntryId)
        assertEquals(10_045L, result.state.experience)
    }

    @Test
    fun confirming_a_backfilled_schedule_is_idempotent() {
        val (state, target) = stateWithTarget()
        val backfilled = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id),
        ).execute(state, now, zone)

        val confirmed = AttentionCommand.ConfirmSchedule(newEntry(target.id).id).execute(backfilled.state, now, zone)

        assertEquals(backfilled.generatedTimeEntries, confirmed.state.timeEntries)
        assertTrue(confirmed.generatedTimeEntries.isEmpty())
        assertTrue(confirmed.newMilestones.isEmpty())
        assertTrue(confirmed.state.schedules.single().completed)
        assertEquals(10_045L, confirmed.state.experience)
    }

    @Test
    fun confirming_a_schedule_without_any_duration_fails_once_a_record_exists() {
        val (state, _) = stateWithTarget()
        val noEstimate = newEntry("reading").copy(estimatedMinutes = null)
        val withRecord = state.addSchedule(noEstimate).let {
            AttentionCommand.SaveSchedule(
                entry = noEstimate,
                backfill = ScheduleBackfill(durationMinutes = 45),
            ).execute(it, now, zone).state
        }

        val confirmed = AttentionCommand.ConfirmSchedule(noEstimate.id).execute(withRecord, now, zone)

        assertEquals(1, confirmed.state.timeEntries.size)
        assertEquals(45, confirmed.state.timeEntries.single().durationMinutes)
        assertTrue(confirmed.state.schedules.single().completed)
    }

    @Test
    fun confirming_the_same_schedule_twice_does_not_duplicate_the_record_or_milestone() {
        val (state, target) = stateWithTarget()
        val saved = state.addSchedule(newEntry(target.id))

        val first = AttentionCommand.ConfirmSchedule(newEntry(target.id).id).execute(saved, now, zone)
        val second = AttentionCommand.ConfirmSchedule(newEntry(target.id).id)
            .execute(first.state, now.plusSeconds(60), zone)

        assertEquals(first.generatedTimeEntries, second.state.timeEntries)
        assertTrue(second.generatedTimeEntries.isEmpty())
        assertTrue(second.newMilestones.isEmpty())
        assertEquals(1, second.state.timeEntries.size)
        assertEquals(TimeEntrySource.SCHEDULE_CONFIRMED, second.state.timeEntries.single().source)
        assertEquals(10_030L, second.state.experience)
    }

    @Test
    fun the_domain_owns_the_schedule_link_check() {
        val (state, target) = stateWithTarget()
        val entry = newEntry(target.id)
        assertFalse(state.hasActualInvestmentFor(entry.id))

        val backfilled = AttentionCommand.SaveSchedule(
            entry = entry,
            backfill = ScheduleBackfill(durationMinutes = 45, targetId = target.id),
        ).execute(state, now, zone).state

        assertTrue(backfilled.hasActualInvestmentFor(entry.id))
        assertEquals(45, backfilled.actualInvestmentForSchedule(entry.id)?.durationMinutes)
        // 另一条日程不受影响。
        assertFalse(backfilled.hasActualInvestmentFor("schedule-2"))
    }

    @Test
    fun the_time_record_rejects_a_link_to_a_schedule_that_does_not_exist() {
        val (state, target) = stateWithTarget()

        val error = assertThrows(IllegalArgumentException::class.java) {
            state.addTimeEntry(
                planningDate = date.toString(),
                durationMinutes = 10,
                targetId = target.id,
                scheduleEntryId = "missing-schedule",
            )
        }

        assertEquals("时间记录只能关联存在的日程条目", error.message)
    }
}
