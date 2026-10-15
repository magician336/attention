package com.attention.workspace.state

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.Target
import com.attention.domain.TimerSegment
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 活动计时器投影的纯 Kotlin 测试：页面只读这份状态，不读系统时钟，也不读完整领域状态。
 *
 * 实际投入与结算预览都由注入的 [now] / [zone] 决定，因此同样的输入永远得到同样的输出。
 * 取样跨过规划日界线（默认 03:00），断言在「凌晨两点」与「凌晨三点刚过界线」两个时刻上都成立。
 */
class TodayTimerStateTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 上海时间 2026-03-06 02:50：还没过 03:00 的规划日界线，规划日仍是 3 月 5 日。 */
    private val beforeBoundary: Instant = Instant.parse("2026-03-05T18:50:00Z")

    /** 上海时间 2026-03-06 03:10：刚过界线，规划日已是 3 月 6 日。 */
    private val afterBoundary: Instant = Instant.parse("2026-03-05T19:10:00Z")

    private fun project(
        state: AttentionState = AttentionState(),
        instant: Instant = beforeBoundary,
        notificationsEnabled: Boolean = true,
    ) = todayScreenState(state = state, now = instant, zone = zone, notificationsEnabled = notificationsEnabled)

    private fun runningTimer(
        targetId: String?,
        startedAt: Instant,
        accumulatedMillis: Long = 0,
        paused: Boolean = false,
        completedSegments: List<TimerSegment> = emptyList(),
    ) = ActiveTimer(
        targetId = targetId,
        startedAtEpochMillis = startedAt.toEpochMilli(),
        accumulatedMillis = accumulatedMillis,
        paused = paused,
        lastPlanningDate = "2026-03-05",
        completedSegments = completedSegments,
    )

    @Test
    fun no_active_timer_projects_no_timer_card() {
        assertNull(project().activeTimer)
    }

    @Test
    fun the_running_target_label_comes_from_the_shared_domain_fallback() {
        // 目标名与「未归属活动」都是领域层已有的结论，投影只是读出来
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                activeTimer = runningTimer(targetId = "t1", startedAt = beforeBoundary.minusSeconds(25 * 60)),
            ),
        )

        assertEquals("写书", screenState.activeTimer?.targetLabel)
        assertEquals("未归属活动", project(AttentionState(activeTimer = runningTimer(null, beforeBoundary))).activeTimer?.targetLabel)
    }

    @Test
    fun elapsed_minutes_are_derived_from_the_injected_time_rather_than_a_live_clock() {
        val screenState = project(
            AttentionState(activeTimer = runningTimer(targetId = null, startedAt = beforeBoundary.minusSeconds(25 * 60))),
            instant = beforeBoundary,
        )

        assertEquals(25, screenState.activeTimer?.elapsedMinutes)
    }

    @Test
    fun a_timer_that_has_not_reached_a_full_minute_reports_no_elapsed_minutes_and_no_record() {
        // 30 秒既不算 1 分钟实际投入，也不会生成时间记录：两者必须同时为 0
        val screenState = project(
            AttentionState(activeTimer = runningTimer(targetId = null, startedAt = beforeBoundary.minusSeconds(30))),
        )

        assertEquals(0, screenState.activeTimer?.elapsedMinutes)
        assertFalse(screenState.activeTimer?.settlementPreview?.hasCompleteMinutes ?: true)
        assertEquals(0, screenState.activeTimer?.settlementPreview?.totalMinutes)
    }

    @Test
    fun a_partially_elapsed_minute_still_settles_when_it_crosses_the_minute() {
        val screenState = project(
            AttentionState(activeTimer = runningTimer(targetId = null, startedAt = beforeBoundary.minusSeconds(90))),
        )

        assertEquals(1, screenState.activeTimer?.elapsedMinutes)
        assertTrue(screenState.activeTimer?.settlementPreview?.hasCompleteMinutes ?: false)
        assertEquals(1, screenState.activeTimer?.settlementPreview?.totalMinutes)
    }

    @Test
    fun a_paused_timer_freezes_elapsed_minutes_at_the_accumulated_total() {
        val screenState = project(
            AttentionState(
                activeTimer = runningTimer(
                    targetId = null,
                    startedAt = beforeBoundary.minusSeconds(10 * 60),
                    accumulatedMillis = 7 * 60_000L,
                    paused = true,
                ),
            ),
            instant = beforeBoundary,
        )

        assertTrue(screenState.activeTimer!!.paused)
        assertEquals(7, screenState.activeTimer?.elapsedMinutes)
    }

    @Test
    fun a_timer_with_previous_segments_reports_them_and_keeps_counting_from_the_last_resume() {
        val screenState = project(
            AttentionState(
                activeTimer = runningTimer(
                    targetId = null,
                    startedAt = beforeBoundary.minusSeconds(3 * 60),
                    accumulatedMillis = 30 * 60_000L,
                    completedSegments = listOf(TimerSegment(beforeBoundary.minusSeconds(33 * 60).toEpochMilli(), beforeBoundary.minusSeconds(3 * 60).toEpochMilli())),
                ),
            ),
        )

        assertEquals(1, screenState.activeTimer?.completedSegmentCount)
        assertEquals(33, screenState.activeTimer?.elapsedMinutes)
    }

    @Test
    fun the_settlement_preview_is_computed_from_the_injected_time() {
        val screenState = project(
            AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                activeTimer = runningTimer(targetId = "t1", startedAt = beforeBoundary.minusSeconds(20 * 60)),
            ),
            instant = beforeBoundary,
        )

        val preview = screenState.activeTimer!!.settlementPreview
        assertTrue(preview.hasCompleteMinutes)
        assertEquals(listOf("2026-03-05"), preview.segments.map { it.planningDate })
        assertEquals(listOf(20), preview.segments.map { it.durationMinutes })
        assertEquals(20, preview.totalMinutes)
    }

    @Test
    fun the_settlement_preview_splits_across_the_planning_day_boundary() {
        // 02:50 起计，03:10 看：结算预览必须拆成两个规划日，且页面不需要自己算
        val screenState = project(
            AttentionState(
                activeTimer = runningTimer(targetId = null, startedAt = beforeBoundary),
            ),
            instant = afterBoundary,
        )

        val preview = screenState.activeTimer!!.settlementPreview
        assertEquals(listOf("2026-03-05", "2026-03-06"), preview.segments.map { it.planningDate })
        assertEquals(listOf(10, 10), preview.segments.map { it.durationMinutes })
        assertEquals(20, preview.totalMinutes)
    }

    @Test
    fun notification_availability_is_reported_by_the_composition_root_not_by_the_projection() {
        // 通知是否可用是平台事实，由组合根查好后注入；投影不自己判断
        assertFalse(project(notificationsEnabled = false).notificationsEnabled)
        assertTrue(project(notificationsEnabled = true).notificationsEnabled)
    }

    @Test
    fun a_timer_started_before_the_boundary_shows_the_new_planning_day_after_it() {
        val screenState = project(
            AttentionState(activeTimer = runningTimer(targetId = null, startedAt = beforeBoundary)),
            instant = afterBoundary,
        )

        assertEquals("2026-03-06", screenState.planningDate.toString())
        assertEquals(20, screenState.activeTimer?.settlementPreview?.totalMinutes)
    }
}
