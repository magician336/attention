package com.attention.app.statistics

import com.attention.domain.GoalCadence
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 选择状态现在由统计页 module 拥有，因此它的迁移语义也由这里直接测；
 * 组合根不再能改动字段，只能通过 StatisticsSelection 的方法迁移。
 */
class StatisticsSelectionTest {
    private val current = LocalDate.of(2026, 3, 5)

    @Test
    fun default_selection_follows_the_injected_planning_day() {
        val selection = StatisticsSelection()
        assertNull(selection.selectedDate)
        assertEquals(GoalCadence.DAILY, selection.cadence)
    }

    @Test
    fun daily_shift_moves_one_planning_day() {
        val selection = StatisticsSelection()
        selection.shiftPeriod(1, current)
        assertEquals(LocalDate.of(2026, 3, 6), selection.selectedDate)
        selection.shiftPeriod(-2, current)
        assertEquals(LocalDate.of(2026, 3, 3), selection.selectedDate)
    }

    @Test
    fun shift_follows_the_selected_cadence_not_the_default_one() {
        val selection = StatisticsSelection()
        selection.selectCadence(GoalCadence.WEEKLY)
        assertEquals(GoalCadence.WEEKLY, selection.cadence)
        selection.shiftPeriod(1, current)
        assertEquals(LocalDate.of(2026, 3, 12), selection.selectedDate)

        selection.selectCadence(GoalCadence.MONTHLY)
        selection.shiftPeriod(1, current)
        assertEquals(LocalDate.of(2026, 4, 5), selection.selectedDate)
    }

    @Test
    fun one_time_cadence_does_not_move_the_selected_day() {
        val selection = StatisticsSelection()
        selection.selectCadence(GoalCadence.ONE_TIME)
        selection.shiftPeriod(1, current)
        assertEquals(current, selection.selectedDate)
    }
}