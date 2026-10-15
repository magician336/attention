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
 * 去重的粒度是**提交意图**，不是这条日程：用户每次显式提交补记都是一个新事实，必须被记录；
 * 只有同一次提交被重放时才不重复记账。日程完成确认的幂等则按 `SCHEDULE_CONFIRMED` 判据。
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

    private fun backfill(
        minutes: Int,
        targetId: String? = "reading",
        note: String = "",
        requestId: String = newId(),
    ) = ScheduleBackfill(durationMinutes = minutes, targetId = targetId, note = note, requestId = requestId)

    @Test
    fun saving_a_schedule_with_backfill_creates_one_linked_manual_record_in_one_step() {
        val (state, target) = stateWithTarget()
        val fill = backfill(45, target.id, "顺手补记", "request-1")

        val result = AttentionCommand.SaveSchedule(newEntry(target.id), fill).execute(state, now, zone)

        // 补记的 45 分钟已经超过计划时长 30 分钟，所以保存本身就把完成状态结算了。
        // 除了这一位，日程条目仍然是用户填进来的那一份。
        assertEquals(listOf(newEntry(target.id).copy(completed = true)), result.state.schedules)
        val record = result.generatedTimeEntries.single()
        assertEquals(TimeEntrySource.MANUAL, record.source)
        assertEquals(newEntry(target.id).id, record.scheduleEntryId)
        assertEquals("request-1", record.backfillRequestId)
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
    fun every_backfill_carries_its_own_submission_intent_by_default() {
        val (state, target) = stateWithTarget()

        val first = backfill(45, target.id)
        val second = backfill(45, target.id)

        // 两次新的补记是两个提交意图，因此是两个新事实。
        assertFalse(first.requestId == second.requestId)

        val applied = AttentionCommand.SaveSchedule(newEntry(target.id), first).execute(state, now, zone)
        val next = AttentionCommand.SaveSchedule(newEntry(target.id), second).execute(applied.state, now, zone)

        assertEquals(2, next.state.timeEntries.size)
        assertEquals(
            setOf(first.requestId, second.requestId),
            next.state.timeEntries.map { it.backfillRequestId }.toSet(),
        )
    }

    @Test
    fun backfill_can_stay_unowned_and_then_be_assigned_later() {
        val (state, target) = stateWithTarget()

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id),
            backfill = backfill(45, targetId = null),
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
        // 归入计划不会改写记录身份，提交意图标识原样保留。
        assertEquals(record.backfillRequestId, assigned.state.timeEntries.single().backfillRequestId)
    }

    @Test
    fun backfill_rejects_archived_or_missing_target() {
        val (archivedState, archivedTarget) = stateWithTarget(archived = true)
        val archived = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(newEntry(archivedTarget.id), backfill(45, archivedTarget.id))
                .execute(archivedState, now, zone)
        }
        assertEquals("时间记录只能归入未归档目标", archived.message)

        val (state, _) = stateWithTarget()
        val missing = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(newEntry(null), backfill(45, "missing"))
                .execute(state, now, zone)
        }
        assertEquals("时间记录只能归入未归档目标", missing.message)
    }

    @Test
    fun backfill_rejects_non_positive_duration() {
        val (state, target) = stateWithTarget()

        val error = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.SaveSchedule(newEntry(target.id), backfill(0, target.id))
                .execute(state, now, zone)
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
    fun replaying_the_same_submission_never_creates_a_second_investment() {
        val (state, target) = stateWithTarget()
        val fill = backfill(45, target.id, requestId = "request-replay")
        val command = AttentionCommand.SaveSchedule(newEntry(target.id), fill)

        val first = command.execute(state, now, zone)
        // 同一次保存被应用两次：重试、双击、事务重放都走这一条路。
        val second = command.execute(first.state, now.plusSeconds(60), zone)

        assertEquals(1, first.generatedTimeEntries.size)
        assertEquals(first.generatedTimeEntries, second.state.timeEntries)
        assertTrue(second.generatedTimeEntries.isEmpty())
        assertTrue(second.newMilestones.isEmpty())
        assertEquals(10_045L, second.state.experience)
        assertEquals(first.state.milestones, second.state.milestones)
    }

    @Test
    fun a_new_backfill_after_a_previous_one_is_still_recorded() {
        val (state, target) = stateWithTarget()
        val first = AttentionCommand.SaveSchedule(newEntry(target.id), backfill(45, target.id, requestId = "request-a"))
            .execute(state, now, zone)

        val second = AttentionCommand.SaveSchedule(
            newEntry(target.id).copy(title = "读两章"),
            backfill(30, target.id, requestId = "request-b"),
        ).execute(first.state, now.plusSeconds(60), zone)

        assertEquals("读两章", second.state.schedules.single().title)
        assertEquals(listOf(45, 30), second.state.timeEntries.map { it.durationMinutes })
        assertEquals(1, second.generatedTimeEntries.size)
        assertEquals("request-b", second.generatedTimeEntries.single().backfillRequestId)
        assertEquals(10_075L, second.state.experience)
        // 阶段里程碑在第一次就发过，这里不重复发放。
        assertTrue(second.newMilestones.isEmpty())
        assertEquals(1, second.state.milestones.size)
    }

    @Test
    fun a_schedule_that_was_timed_can_still_be_backfilled() {
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

        val result = AttentionCommand.SaveSchedule(newEntry(target.id), backfill(45, target.id))
            .execute(timed, now, zone)

        val record = result.generatedTimeEntries.single()
        assertEquals(TimeEntrySource.MANUAL, record.source)
        assertEquals(45, record.durationMinutes)
        assertEquals(newEntry(target.id).id, record.scheduleEntryId)
        // 计时那 20 分钟与这次补记的 45 分钟是两笔，合计 65 分钟。
        assertEquals(listOf(20, 45), result.state.timeEntries.map { it.durationMinutes })
        assertEquals(10_065L, result.state.experience)
        assertEquals(listOf("one_time_goal:stage:${date}"), result.newMilestones.map { it.instanceKey })
    }

    @Test
    fun backfilling_an_existing_schedule_still_creates_the_record() {
        val (state, target) = stateWithTarget()
        val saved = state.addSchedule(newEntry(target.id))

        val result = AttentionCommand.SaveSchedule(
            entry = newEntry(target.id).copy(note = "改过备注"),
            backfill = backfill(45, target.id),
        ).execute(saved, now, zone)

        assertEquals(1, result.state.schedules.size)
        assertEquals("改过备注", result.state.schedules.single().note)
        assertEquals(45, result.generatedTimeEntries.single().durationMinutes)
        assertEquals(newEntry(target.id).id, result.generatedTimeEntries.single().scheduleEntryId)
        assertEquals(10_045L, result.state.experience)
    }

    @Test
    fun confirming_after_a_backfill_still_records_the_confirmation() {
        val (state, target) = stateWithTarget()
        val backfilled = AttentionCommand.SaveSchedule(newEntry(target.id), backfill(45, target.id, requestId = "request-1"))
            .execute(state, now, zone)

        val confirmed = AttentionCommand.ConfirmSchedule(newEntry(target.id).id)
            .execute(backfilled.state, now, zone)

        // 补记是补记，确认是确认：两者都是事实，不会互相吞掉。
        assertEquals(2, confirmed.state.timeEntries.size)
        assertEquals(
            listOf(TimeEntrySource.MANUAL, TimeEntrySource.SCHEDULE_CONFIRMED),
            confirmed.state.timeEntries.map { it.source },
        )
        val confirmation = confirmed.generatedTimeEntries.single()
        assertEquals(30, confirmation.durationMinutes)
        assertEquals(newEntry(target.id).id, confirmation.scheduleEntryId)
        assertNull(confirmation.backfillRequestId)
        assertTrue(confirmed.state.schedules.single().completed)
        assertEquals(10_075L, confirmed.state.experience)
        // 里程碑在补记时就发过，确认不重复发放。
        assertTrue(confirmed.newMilestones.isEmpty())
    }

    @Test
    fun backfilling_after_a_confirmation_still_records_the_backfill() {
        val (state, target) = stateWithTarget()
        val saved = state.addSchedule(newEntry(target.id))
        val confirmed = AttentionCommand.ConfirmSchedule(newEntry(target.id).id).execute(saved, now, zone)
        assertEquals(1, confirmed.state.timeEntries.size)

        val result = AttentionCommand.SaveSchedule(
            // 页面编辑的是已存在的那条日程，完成标记随草稿一起带回，不被这次保存抹掉。
            entry = confirmed.state.schedules.single().copy(title = "读两章"),
            backfill = backfill(45, target.id),
        ).execute(confirmed.state, now.plusSeconds(60), zone)

        assertEquals(2, result.state.timeEntries.size)
        assertEquals(
            listOf(TimeEntrySource.SCHEDULE_CONFIRMED, TimeEntrySource.MANUAL),
            result.state.timeEntries.map { it.source },
        )
        assertEquals(45, result.generatedTimeEntries.single().durationMinutes)
        assertEquals("读两章", result.state.schedules.single().title)
        assertTrue(result.state.schedules.single().completed)
        assertEquals(10_075L, result.state.experience)
        assertTrue(result.newMilestones.isEmpty())
    }

    @Test
    fun a_backfill_does_not_become_the_confirmation_duration() {
        val (state, target) = stateWithTarget()
        val noEstimate = newEntry(target.id).copy(estimatedMinutes = null)
        val withBackfill = AttentionCommand.SaveSchedule(noEstimate, backfill(45, target.id, requestId = "request-1"))
            .execute(state, now, zone)
            .state

        // 补记的 45 分钟不是确认的时长来源：确认仍按日程自己的预计时长或时间段判定。
        val error = assertThrows(IllegalArgumentException::class.java) {
            AttentionCommand.ConfirmSchedule(noEstimate.id).execute(withBackfill, now, zone)
        }

        assertEquals("日程条目缺少预计时长，无法确认实际投入", error.message)
        assertEquals(1, withBackfill.timeEntries.size)
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
    fun confirming_twice_never_duplicates_a_replay_of_the_backfill() {
        val (state, target) = stateWithTarget()
        val fill = backfill(45, target.id, requestId = "request-replay")
        val save = AttentionCommand.SaveSchedule(newEntry(target.id), fill)
        val saved = save.execute(state, now, zone).state

        val firstConfirm = AttentionCommand.ConfirmSchedule(newEntry(target.id).id).execute(saved, now, zone)
        // 保存重放与重复确认都发生在同一条日程上，两笔都必须被拦住。
        val replayedSave = save.execute(firstConfirm.state, now.plusSeconds(60), zone)
        val secondConfirm = AttentionCommand.ConfirmSchedule(newEntry(target.id).id)
            .execute(replayedSave.state, now.plusSeconds(120), zone)

        assertTrue(replayedSave.generatedTimeEntries.isEmpty())
        assertTrue(secondConfirm.generatedTimeEntries.isEmpty())
        assertEquals(2, secondConfirm.state.timeEntries.size)
        assertEquals(listOf(45, 30), secondConfirm.state.timeEntries.map { it.durationMinutes })
        assertEquals(1, secondConfirm.state.milestones.size)
        assertEquals(10_075L, secondConfirm.state.experience)
    }

    @Test
    fun the_domain_owns_the_schedule_link_check() {
        val (state, target) = stateWithTarget()
        val entry = newEntry(target.id)

        assertFalse(state.hasConfirmedInvestmentFor(entry.id))
        assertTrue(state.backfillsForSchedule(entry.id).isEmpty())
        assertNull(state.appliedBackfill("request-1"))

        val backfilled = AttentionCommand.SaveSchedule(entry, backfill(45, target.id, requestId = "request-1"))
            .execute(state, now, zone)
            .state

        assertEquals(listOf(45), backfilled.backfillsForSchedule(entry.id).map { it.durationMinutes })
        assertEquals(45, backfilled.appliedBackfill("request-1")?.durationMinutes)
        assertNull(backfilled.appliedBackfill("request-2"))
        // 补记不构成「已确认完成」：这两个事实在领域里分开看。
        assertFalse(backfilled.hasConfirmedInvestmentFor(entry.id))

        val confirmed = AttentionCommand.ConfirmSchedule(entry.id).execute(backfilled, now, zone).state

        assertTrue(confirmed.hasConfirmedInvestmentFor(entry.id))
        assertEquals(30, confirmed.confirmedInvestmentForSchedule(entry.id)?.durationMinutes)
        // 确认完成也不会让补填入口消失：补记仍然记录在案。
        assertEquals(listOf(45), confirmed.backfillsForSchedule(entry.id).map { it.durationMinutes })
        assertFalse(confirmed.hasConfirmedInvestmentFor("schedule-2"))
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

    @Test
    fun only_a_backfill_may_carry_a_submission_intent() {
        val (state, target) = stateWithTarget()

        val error = assertThrows(IllegalArgumentException::class.java) {
            state.addTimeEntry(
                planningDate = date.toString(),
                durationMinutes = 10,
                targetId = target.id,
                source = TimeEntrySource.SCHEDULE_CONFIRMED,
                backfillRequestId = "request-1",
            )
        }

        assertEquals("只有事后补记带提交意图标识", error.message)

        val blank = assertThrows(IllegalArgumentException::class.java) {
            state.addTimeEntry(
                planningDate = date.toString(),
                durationMinutes = 10,
                targetId = target.id,
                backfillRequestId = "  ",
            )
        }
        assertEquals("提交意图标识不能为空", blank.message)
    }
}
