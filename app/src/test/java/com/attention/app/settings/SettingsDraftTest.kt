package com.attention.app.settings

import com.attention.domain.LaunchDestination
import com.attention.domain.PlannerSettings
import com.attention.workspace.state.SettingsScreenState
import com.attention.workspace.state.SettingsWeekDayOption
import com.attention.workspace.state.weekDayLabel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页表单草稿的保存解析。
 *
 * 页面不自己复述领域规则：界线与每日容量合不合法由 `:domain` 判定，页面只负责把被拒绝的
 * 原因显示出来。这里钉住两件事——不合法的界线沿用当前值，以及不合法的容量给出可读的报错
 * 并且不发出保存意图。
 */
class SettingsDraftTest {

    private fun state(boundary: Int = 180, capacity: Int? = 480) = SettingsScreenState(
        options = emptyList(),
        selectedDestination = LaunchDestination.AGENDA,
        resolvedDestination = LaunchDestination.AGENDA,
        planningDay = LocalDate.of(2026, 3, 5),
        planningDayBoundaryMinutes = boundary,
        weekDayOptions = (1..7).map { SettingsWeekDayOption(it, weekDayLabel(it), it == 1) },
        dailyCapacityMinutes = capacity,
        notificationsEnabled = true,
    )

    private fun draftWith(boundary: String, capacity: String): SettingsDraft {
        val draft = SettingsDraft()
        draft.syncDefaults(state())
        draft.typeBoundary(boundary)
        draft.typeCapacity(capacity)
        return draft
    }

    @Test
    fun a_valid_input_becomes_the_saved_planning_day_settings() {
        val draft = draftWith("60", "300")

        assertEquals(
            PlanningSettingsSave.Valid(60, 300),
            draft.resolveSave(state()),
        )
    }

    @Test
    fun an_empty_capacity_clears_it() {
        assertEquals(
            PlanningSettingsSave.Valid(60, null),
            draftWith("60", "").resolveSave(state()),
        )
    }

    @Test
    fun a_boundary_outside_one_day_falls_back_to_the_current_boundary() {
        // 一天有多少分钟由 :domain 决定；页面只问一次这个输入合不合法，不自己复述上界
        assertEquals(
            PlannerSettings.MINUTES_PER_DAY,
            24 * 60,
        )
        assertEquals(
            PlanningSettingsSave.Valid(180, 300),
            draftWith((PlannerSettings.MINUTES_PER_DAY + 1).toString(), "300").resolveSave(state()),
        )
        assertEquals(
            PlanningSettingsSave.Valid(180, 300),
            draftWith("", "300").resolveSave(state()),
        )
    }

    @Test
    fun a_non_positive_capacity_is_reported_and_nothing_is_saved() {
        val draft = draftWith("60", "0")

        val result = draft.resolveSave(state())

        assertEquals(PlanningSettingsSave.Invalid, result)
        assertEquals("每日容量必须是正整数，或留空清除", draft.error)
    }
}
