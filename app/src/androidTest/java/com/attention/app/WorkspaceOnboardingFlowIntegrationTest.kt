package com.attention.app

import android.content.Intent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #177 首次使用引导的验收：独立、全屏、可跳过的说明式引导。
 *
 * 这条用例走真实的 MainActivity 宿主路径，因此验证的是用户看到、点到与最终存下来的东西：
 * 未完成引导时第一次启动看到的是独立全屏引导而不是工作区；说明页固定为日程、目标树、统计；
 * 三种离开方式都落到当前规划日的日程；完成后不再自动出现；设置页可以重新打开它。
 *
 * 最关键的一条是 [completing_the_guide_changes_no_business_data_at_all]：
 * 引导前后目标、日程、设置与时间记录集合必须完全不变——旧引导卡片会创建这些数据，新引导一个都不写。
 *
 * 每个用例自己准备一份 Activity 实例，因此「完成后不再自动出现」可以由重新启动同一个用例路径验证。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceOnboardingFlowIntegrationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeLaunchedActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun the_first_start_without_a_finished_guide_shows_the_full_screen_guide() {
        seed(onboardingCompleted = false)

        launch()

        awaitTag("onboarding-screen")
        // 独立全屏：底部导航与工作区都不在屏幕上，用户不能绕过它去别的页面
        composeRule.onNodeWithTag("nav-agenda").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-targets").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-statistics").assertDoesNotExist()
        composeRule.onNodeWithTag("agenda-filter").assertDoesNotExist()
        // 说明页顺序固定：日程 → 目标树 → 统计
        composeRule.onNodeWithText("第 1 页，共 3 页").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding-page-title").assertIsDisplayed()
    }

    @Test
    fun the_three_pages_explain_the_three_entries_in_a_stable_order() {
        seed(onboardingCompleted = false)

        launch()

        awaitTag("onboarding-screen")
        composeRule.onNodeWithText("日程").assertIsDisplayed()
        click("onboarding-skip-page")
        composeRule.onNodeWithText("第 2 页，共 3 页").assertIsDisplayed()
        composeRule.onNodeWithText("目标树").assertIsDisplayed()
        click("onboarding-skip-page")
        composeRule.onNodeWithText("第 3 页，共 3 页").assertIsDisplayed()
        composeRule.onNodeWithText("统计").assertIsDisplayed()
        // 走到最后一页也没有被迫创建任何数据：引导仍然在屏幕上
        composeRule.onNodeWithTag("onboarding-screen").assertIsDisplayed()
    }

    @Test
    fun skipping_the_remaining_steps_lands_on_the_agenda_of_the_current_planning_day() {
        seed(onboardingCompleted = false)

        launch()
        awaitTag("onboarding-screen")
        click("onboarding-skip-remaining")

        awaitSettings { it.onboardingCompleted }
        awaitGone("onboarding-screen")
        awaitTag("nav-agenda")
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
        // 「规划日 …」在日程页上有不止一处文案（容量摘要里也会出现），因此断言落在筛选标题上
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("规划日 $TODAY"))
    }

    @Test
    fun starting_beside_after_the_three_pages_lands_on_the_agenda_of_the_current_planning_day() {
        seed(onboardingCompleted = false)

        launch()
        awaitTag("onboarding-screen")
        click("onboarding-skip-page")
        click("onboarding-skip-page")
        click("onboarding-start")

        awaitSettings { it.onboardingCompleted }
        awaitGone("onboarding-screen")
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
        // 同上：规划日断言绑定在筛选标题这个元素上，不做全页文本搜索
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("规划日 $TODAY"))
    }

    @Test
    fun the_guide_does_not_appear_again_after_it_has_been_completed() {
        seed(onboardingCompleted = false)
        launch()
        awaitTag("onboarding-screen")
        click("onboarding-skip-remaining")
        awaitSettings { it.onboardingCompleted }
        awaitGone("onboarding-screen")
        // 关掉这次启动，再用同一份持久化状态重新启动：引导不再自动出现
        scenario?.close()
        scenario = null

        launch()

        composeRule.onNodeWithTag("onboarding-screen").assertDoesNotExist()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithTag("nav-agenda").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
    }

    @Test
    fun the_guide_can_be_reopened_from_the_settings_page() {
        seed(onboardingCompleted = true)

        launch()
        awaitTag("nav-statistics")
        composeRule.onNodeWithTag("nav-statistics").performClick()
        awaitTag("statistics-open-settings")
        click("statistics-open-settings")
        awaitTag("settings-open-onboarding")
        click("settings-open-onboarding")

        // 重新打开后仍然是同一套说明页，并且是全屏的独立引导
        awaitTag("onboarding-screen")
        composeRule.onNodeWithText("第 1 页，共 3 页").assertIsDisplayed()
        click("onboarding-start")

        awaitGone("onboarding-screen")
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
    }

    @Test
    fun completing_the_guide_changes_no_business_data_at_all() {
        seed(onboardingCompleted = false)
        val before = readBusinessState()

        launch()
        awaitTag("onboarding-screen")
        click("onboarding-skip-remaining")
        awaitSettings { it.onboardingCompleted }
        awaitGone("onboarding-screen")

        val after = readBusinessState()
        // 目标、日程、时间记录与规划设置一个字节都不能变：新引导只更新引导状态
        assertEquals(before.targets, after.targets)
        assertEquals(before.schedules, after.schedules)
        assertEquals(before.timeEntries, after.timeEntries)
        assertEquals(before.settings, after.settings)
        assertEquals(1, after.targets.size)
        assertEquals(1, after.schedules.size)
        assertEquals(1, after.timeEntries.size)
        // 唯一变化的是引导状态本身
        assertTrue(after.onboardingCompleted)
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun launch() {
        scenario = ActivityScenario.launch<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    }

    /** 等到设置快照真的变成期望的样子：引导的落地是异步写进 DataStore 的。 */
    private fun awaitSettings(predicate: (SettingsSnapshot) -> Boolean) {
        composeRule.waitUntil(TIMEOUT) { readSettings().let(predicate) }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitGone(tag: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "ui-onboarding-target"
        private const val SCHEDULE_ID = "ui-onboarding-schedule"
        private const val ENTRY_ID = "ui-onboarding-entry"
        private val TODAY: String = AttentionState().planningDate(Instant.now()).toString()

        /**
         * 每次启动前铺一份确定的业务数据，并把引导状态设成调用方要的那个值。
         * 业务数据本身刻意不为空：这样「引导前后集合完全不变」才真的被断言了。
         */
        private fun seed(onboardingCompleted: Boolean) {
            val state = AttentionState(
                targets = listOf(Target(TARGET_ID, title = "UI-引导目标")),
                schedules = listOf(
                    ScheduleEntry(
                        id = SCHEDULE_ID,
                        planningDate = TODAY,
                        title = "UI-引导日程",
                        startMinute = 540,
                        endMinute = 600,
                    ),
                ),
                timeEntries = listOf(
                    TimeEntry(
                        id = ENTRY_ID,
                        planningDate = TODAY,
                        durationMinutes = 25,
                        targetId = TARGET_ID,
                    ),
                ),
            )
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(state)
                }
                // 共享 DataStore 会被别的测试类的 Activity 写回：播种后读回确认才算数。
                WorkspaceHostSettingsBarrier.quiesceAndSeed(
                    SettingsSnapshot(
                        launchDestination = LaunchDestination.AGENDA,
                        lastOpenedDestination = LaunchDestination.AGENDA,
                        onboardingCompleted = onboardingCompleted,
                    ),
                )
            } finally {
                database.close()
            }
        }

        private fun repository(database: AttentionDatabase) = RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = ApplicationProvider.getApplicationContext<android.content.Context>().createSettingsStore(),
        )

        private fun readBusinessState(): AttentionState {
            val database = AttentionDatabase.create(ApplicationProvider.getApplicationContext())
            return try {
                runBlocking { repository(database).read() }
            } finally {
                database.close()
            }
        }

        private fun readSettings(): SettingsSnapshot = runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .settings
                .first()
        }
    }
}
