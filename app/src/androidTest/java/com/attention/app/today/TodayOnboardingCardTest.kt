package com.attention.app.today

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.ui.theme.AttentionTheme
import com.attention.domain.AttentionState
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.workspace.state.OnboardingScheduleDraft
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.todayScreenState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首次设置流程的直接测试：只给它 fake screen state 与 fake actions，断言五步各自发出的
 * intent 与用户可见的校验提示，不经过 ViewModel、Room 与系统时钟。
 *
 * 页面本身不需要 Activity 上下文，因此用轻量的 [createComposeRule] 挂载。
 */
@RunWith(AndroidJUnit4::class)
class TodayOnboardingCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 上海时间 2026-03-06 01:00，规划日仍是 3 月 5 日。 */
    private val now: Instant = Instant.parse("2026-03-05T17:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun the_boundary_step_emits_the_planning_settings_intent() {
        val intents = RecordingActions()
        mount(intents)

        typeTag("onboarding-boundary-input", "240")
        clickTag("onboarding-save-continue")

        assertEquals(listOf(PlannerSettings(240, 1, null)), intents.planningSettings)
    }

    @Test
    fun an_out_of_range_boundary_shows_the_error_and_emits_nothing() {
        val intents = RecordingActions()
        mount(intents)

        typeTag("onboarding-boundary-input", "1500")
        clickTag("onboarding-save-continue")

        composeRule.onNodeWithTag("onboarding-error").assertExists()
        assertEquals(emptyList<PlannerSettings>(), intents.planningSettings)
    }

    @Test
    fun the_week_step_emits_the_selected_week_start_day() {
        val intents = RecordingActions()
        mount(intents)

        clickTag("onboarding-save-continue")
        clickTag("onboarding-week-4")
        clickTag("onboarding-save-continue")

        assertEquals(listOf(PlannerSettings(180, 1, null), PlannerSettings(180, 4, null)), intents.planningSettings)
    }

    @Test
    fun the_capacity_step_accepts_a_blank_capacity_as_clearing_the_limit() {
        val intents = RecordingActions()
        mount(intents)

        clickTag("onboarding-save-continue")
        clickTag("onboarding-save-continue")
        clickTag("onboarding-save-continue")

        assertEquals(
            listOf(PlannerSettings(180, 1, null), PlannerSettings(180, 1, null), PlannerSettings(180, 1, null)),
            intents.planningSettings,
        )
    }

    @Test
    fun the_plan_step_emits_the_target_intent_with_the_trimmed_title() {
        val intents = RecordingActions()
        mount(intents)

        skipToStep(3)
        typeTag("onboarding-plan-title", "  写书  ")
        clickTag("onboarding-save-continue")

        assertEquals(listOf("写书"), intents.createdTargets)
    }

    @Test
    fun a_blank_plan_title_shows_the_error_and_emits_nothing() {
        val intents = RecordingActions()
        mount(intents)

        skipToStep(3)
        clickTag("onboarding-save-continue")

        composeRule.onNodeWithTag("onboarding-error").assertExists()
        assertEquals(emptyList<String>(), intents.createdTargets)
    }

    @Test
    fun the_schedule_step_defaults_to_the_projected_planning_day_and_emits_the_draft() {
        // 日程的规划日初值来自投影，不再读系统时钟
        val intents = RecordingActions()
        mount(intents)

        skipToStep(4)
        composeRule.onNodeWithTag("onboarding-schedule-date").performScrollTo().assertIsDisplayed()
        typeTag("onboarding-schedule-title", "写稿")
        typeTag("onboarding-schedule-estimate", "90")
        clickTag("onboarding-schedule-target-t1")
        clickTag("onboarding-save-continue")

        assertEquals(
            listOf(
                OnboardingScheduleDraft(
                    planningDate = "2026-03-05",
                    title = "写稿",
                    startMinute = null,
                    endMinute = null,
                    estimatedMinutes = 90,
                    note = "",
                    targetId = "t1",
                ),
            ),
            intents.createdSchedules,
        )
    }

    @Test
    fun an_invalid_schedule_time_range_shows_the_error_and_emits_nothing() {
        val intents = RecordingActions()
        mount(intents)

        skipToStep(4)
        typeTag("onboarding-schedule-title", "写稿")
        // 只填开始不填结束：时间段无效
        composeRule.onNodeWithText("开始分钟").performScrollTo().performTextInput("600")
        clickTag("onboarding-save-continue")

        composeRule.onNodeWithTag("onboarding-error").assertExists()
        assertEquals(emptyList<OnboardingScheduleDraft>(), intents.createdSchedules)
    }

    @Test
    fun skipping_a_step_advances_without_emitting_anything() {
        val intents = RecordingActions()
        mount(intents)

        clickTag("onboarding-skip-step")

        composeRule.onNodeWithText("第 2 步，共 5 步").assertIsDisplayed()
        assertEquals(emptyList<PlannerSettings>(), intents.planningSettings)
    }

    @Test
    fun skipping_the_last_step_finishes_onboarding() {
        val intents = RecordingActions()
        mount(intents)

        skipToStep(4)
        clickTag("onboarding-skip-step")

        assertEquals(listOf("finish"), intents.finished)
    }

    @Test
    fun skipping_the_remaining_steps_finishes_onboarding() {
        val intents = RecordingActions()
        mount(intents)

        clickTag("onboarding-skip-all")

        assertEquals(listOf("finish"), intents.finished)
    }

    /** 连续跳过若干步，直到落到目标步骤；跳过的步骤不发出任何 intent。 */
    private fun skipToStep(target: Int) {
        repeat(target) { clickTag("onboarding-skip-step") }
    }

    private fun clickTag(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun typeTag(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(tag).performTextInput(value)
    }

    private fun mount(actions: TodayActions = RecordingActions()) {
        val state = todayScreenState(
            state = AttentionState(
                targets = listOf(Target(id = "t1", title = "写书")),
                onboardingCompleted = false,
            ),
            now = now,
            zone = zone,
        )
        composeRule.setContent {
            AttentionTheme { TodayScreen(state = state, actions = actions) }
        }
        composeRule.onNodeWithTag("onboarding-card").assertExists()
    }

    private data class PlannerSettings(
        val boundaryMinutes: Int,
        val weekStartDay: Int,
        val dailyCapacityMinutes: Int?,
    )

    private class RecordingActions : TodayActions {
        val planningSettings = mutableListOf<PlannerSettings>()
        val createdTargets = mutableListOf<String>()
        val createdSchedules = mutableListOf<OnboardingScheduleDraft>()
        val finished = mutableListOf<String>()

        override fun recordTime(planningDate: String, minutes: Int, targetId: String?) = Unit
        override fun editTime(entryId: String, minutes: Int, targetId: String?, note: String) = Unit
        override fun deleteTime(entryId: String) = Unit
        override fun saveDailyPlanningSettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?) = Unit
        override fun selectWeekStartDay(day: Int) = Unit
        override fun startTimer(targetId: String?) = Unit
        override fun pauseTimer() = Unit
        override fun resumeTimer() = Unit
        override fun recoverTimer() = Unit
        override fun stopTimer() = Unit
        override fun switchTimer(targetId: String?) = Unit
        override fun requestNotificationPermission() = Unit

        override fun finishOnboarding() {
            finished += "finish"
        }

        override fun saveOnboardingPlanningSettings(
            boundaryMinutes: Int,
            weekStartDay: Int,
            dailyCapacityMinutes: Int?,
        ) {
            planningSettings += PlannerSettings(boundaryMinutes, weekStartDay, dailyCapacityMinutes)
        }

        override fun createOnboardingTarget(title: String, onSuccess: () -> Unit) {
            createdTargets += title
            onSuccess()
        }

        override fun createOnboardingSchedule(draft: OnboardingScheduleDraft) {
            createdSchedules += draft
        }

        override fun updateTodaySchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?) = Unit
        override fun confirmScheduleCompleted(entryId: String) = Unit
        override fun toggleScheduleCompleted(entryId: String) = Unit
    }
}
