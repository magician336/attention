package com.attention.app.agenda

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 日程页的日期选择跨页面切换存活、跨重启重置：语义由日程页 module 自己拥有，
 * 组合根只持有一个实例喂投影，因此这里直接测它的迁移语义。
 *
 * 关键区别是「用户还没显式选过」与「用户选了所有日期」：前者跟随当前规划日，
 * 后者是一个被用户选中的范围，会在会话内保留。
 */
class AgendaSelectionTest {
    private val planningDay = LocalDate.of(2026, 3, 5)

    @Test
    fun a_new_session_without_an_explicit_choice_follows_the_current_planning_day() {
        // 新会话（刚启动、用户还没动过筛选）以当前规划日为默认，而不是一个过期日期
        assertEquals(planningDay, AgendaSelection().selectedDate(planningDay))
        // 规划日随后（持久化设置到达、或跨过规划日界线）变化时，默认跟着走
        assertEquals(LocalDate.of(2026, 3, 4), AgendaSelection().selectedDate(LocalDate.of(2026, 3, 4)))
    }

    @Test
    fun an_explicit_choice_is_kept_for_the_rest_of_the_session() {
        val selection = AgendaSelection()
        val tomorrow = planningDay.plusDays(1)

        selection.selectDate(tomorrow)

        // 切到目标树或统计再回来：实例还是同一个，选中的日期不退回当前规划日
        assertEquals(tomorrow, selection.selectedDate(planningDay))
        assertEquals(tomorrow, selection.selectedDate(LocalDate.of(2026, 4, 30)))
    }

    @Test
    fun all_dates_is_an_explicit_range_and_does_not_fall_back_to_the_planning_day() {
        val selection = AgendaSelection()

        selection.selectAllDates()

        // 「所有日期」是用户选中的范围，因此投影拿到 null 而不是当前规划日
        assertNull(selection.selectedDate(planningDay))
    }

    @Test
    fun a_new_session_never_inherits_the_previous_sessions_range() {
        val previousSession = AgendaSelection().apply { selectDate(LocalDate.of(2025, 12, 24)) }

        // 上一个会话留下的是一个过期筛选；新会话不恢复它
        val restarted = AgendaSelection()
        assertEquals(planningDay, restarted.selectedDate(planningDay))
        assertEquals(
            LocalDate.of(2025, 12, 24),
            previousSession.selectedDate(planningDay),
        )
    }

    @Test
    fun an_external_schedule_request_opens_that_planning_day_and_then_sticks() {
        val requested = LocalDate.of(2026, 3, 9)
        val selection = AgendaSelection(requested)

        assertEquals(requested, selection.selectedDate(planningDay))
        // 深链定位之后，用户没有再动过筛选，因此仍然是那一天
        assertEquals(requested, selection.selectedDate(planningDay.plusDays(3)))
    }

    @Test
    fun shifting_moves_from_the_projected_selected_day() {
        val selection = AgendaSelection()

        selection.shiftDays(1, planningDay)
        assertEquals(planningDay.plusDays(1), selection.selectedDate(planningDay))

        selection.shiftDays(-3, planningDay.plusDays(1))
        assertEquals(planningDay.minusDays(2), selection.selectedDate(planningDay))
    }
}
