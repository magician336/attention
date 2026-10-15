package com.attention.app

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import com.attention.app.reminder.ReminderReceiver
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动优先级的验收：外部日程请求 > 保存的启动入口 > 上次打开 > 当前规划日的日程。
 *
 * 这条用例走真实的 MainActivity，因此验证的是用户看到与点到的东西：
 * 保存的四种入口各自直达对应页面，外部日程请求不被保存的入口拦截，
 * 并直接定位到指定规划日与指定日程条目，且不改写启动偏好、不产生业务数据。
 *
 * 每个用例自己播种设置再启动一次 Activity，因此 Activity 在断言期间保持存活。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceLaunchPriorityIntegrationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeLaunchedActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun without_a_saved_entry_the_agenda_of_the_current_planning_day_opens() {
        // 默认启动：旧入口值按兼容映射等价于日程，回退到当前规划日的日程
        launch(launchDestination = LaunchDestination.TODAY, lastOpened = LaunchDestination.AGENDA)

        awaitSelected("nav-agenda")
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
        awaitText(TODAY_ENTRY_TITLE)
        // 另一个规划日的条目不在这一天上：默认启动落在当前规划日
        composeRule.onNodeWithText(FAR_ENTRY_TITLE).assertDoesNotExist()
    }

    @Test
    fun the_saved_targets_entry_opens_the_targets_tree_directly() {
        launch(launchDestination = LaunchDestination.TARGETS, lastOpened = LaunchDestination.STATISTICS)

        awaitSelected("nav-targets")
        awaitTag("archived-empty-state")
        composeRule.onNodeWithTag("archived-empty-state").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-filter").assertDoesNotExist()
    }

    @Test
    fun the_saved_statistics_entry_opens_the_statistics_page_directly() {
        launch(launchDestination = LaunchDestination.STATISTICS, lastOpened = LaunchDestination.TARGETS)

        awaitSelected("nav-statistics")
        awaitText("统计与成长")
        composeRule.onNodeWithText("统计与成长").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-filter").assertDoesNotExist()
    }

    @Test
    fun the_saved_last_opened_entry_restores_the_page_that_was_open_last() {
        // 「上次打开」只解析为日程、目标树或统计
        launch(launchDestination = LaunchDestination.LAST_OPENED, lastOpened = LaunchDestination.TARGETS)

        awaitSelected("nav-targets")
        awaitTag("archived-empty-state")
        composeRule.onNodeWithTag("archived-empty-state").assertIsDisplayed()
    }

    @Test
    fun the_saved_last_opened_entry_falls_back_to_the_current_planning_day_without_usable_history() {
        // 没有有效历史时回退到当前规划日的日程
        launch(launchDestination = LaunchDestination.LAST_OPENED, lastOpened = LaunchDestination.LAST_OPENED)

        awaitSelected("nav-agenda")
        composeRule.onNodeWithTag("agenda-filter").assertIsDisplayed()
        awaitText(TODAY_ENTRY_TITLE)
    }

    @Test
    fun an_external_schedule_request_wins_over_the_saved_entry_and_locates_the_day_and_the_entry() {
        // 保存的入口是统计：外部日程请求不被它拦截，直接进入日程并定位指定规划日
        launch(
            launchDestination = LaunchDestination.STATISTICS,
            lastOpened = LaunchDestination.STATISTICS,
            planningDate = FAR_DATE,
            entryId = FAR_ENTRY_ID,
        )

        awaitSelected("nav-agenda")
        // 指定规划日：第三个规划日被打开，当天的条目可见
        awaitText("规划日 $FAR_DATE")
        awaitText(FAR_ENTRY_TITLE)
        // 指定规划日之外的那一天不出现，条目确实被定位到了
        composeRule.onNodeWithText(TODAY_ENTRY_TITLE).assertDoesNotExist()
        // 指定条目本身被定位：它的编辑对话框直接打开
        awaitTag("schedule-edit-save-$FAR_ENTRY_ID")
        composeRule.onNodeWithTag("schedule-edit-save-$FAR_ENTRY_ID", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun an_external_schedule_request_never_rewrites_the_saved_entry_or_creates_business_data() {
        launch(
            launchDestination = LaunchDestination.STATISTICS,
            lastOpened = LaunchDestination.STATISTICS,
            planningDate = FAR_DATE,
            entryId = FAR_ENTRY_ID,
        )
        awaitTag("schedule-edit-save-$FAR_ENTRY_ID")
        composeRule.waitForIdle()

        val state = readState()
        // 外部请求只用于这一次打开定位：保存的启动入口原样保留
        assertEquals(LaunchDestination.STATISTICS, state.launchDestination)
        // 不产生业务数据：目标与日程条目都还是种子里的那几条
        assertEquals(listOf(TARGET_ID), state.targets.map { it.id })
        assertEquals(listOf(TODAY_ENTRY_ID, FAR_ENTRY_ID).sorted(), state.schedules.map { it.id }.sorted())
        assertEquals(emptyList<String>(), state.timeEntries.map { it.id })
    }

    /** 启动落点用底部导航的选中语义表达，因此断言选中的是哪一项而不是页面内部变量。 */
    private fun awaitSelected(tag: String) {
        composeRule.waitUntil(TIMEOUT) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
                    .singleOrNull()?.config?.get(SemanticsProperties.Selected) == true
            }.getOrDefault(false)
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT) {
            runCatching { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            runCatching { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    /**
     * 播种一个目标与两条不同规划日的日程，然后按给定的启动入口启动真实 Activity。
     * [planningDate] 与 [entryId] 一起给出时就是一次外部日程请求（提醒通知点进来）。
     */
    private fun launch(
        launchDestination: LaunchDestination,
        lastOpened: LaunchDestination,
        planningDate: String? = null,
        entryId: String? = null,
    ) {
        seed(launchDestination = launchDestination, lastOpened = lastOpened)
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        if (planningDate != null) {
            intent.putExtra(ReminderReceiver.EXTRA_PLANNING_DATE, planningDate)
        }
        if (entryId != null) {
            intent.putExtra(ReminderReceiver.EXTRA_ID, entryId)
        }
        scenario = ActivityScenario.launch<MainActivity>(intent)
        composeRule.waitForIdle()
    }

    private fun seed(launchDestination: LaunchDestination, lastOpened: LaunchDestination) {
        val state = AttentionState()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AttentionDatabase.create(context)
        try {
            runBlocking {
                repository(database).replace(
                    state.copy(
                        targets = listOf(Target(TARGET_ID, title = "UI-启动目标")),
                        schedules = listOf(
                            ScheduleEntry(TODAY_ENTRY_ID, TODAY, TODAY_ENTRY_TITLE, estimatedMinutes = 30, targetId = TARGET_ID),
                            ScheduleEntry(FAR_ENTRY_ID, FAR_DATE, FAR_ENTRY_TITLE, estimatedMinutes = 20, targetId = TARGET_ID),
                        ),
                    ),
                )
            }
            // 同一安装下别的测试类的 Activity 会把「上次打开」写回共享 DataStore，
            // 因此播种之后必须读回确认它真的生效，否则本类的启动落点会被抢走。
            WorkspaceHostSettingsBarrier.quiesceAndSeed(
                SettingsSnapshot(
                    launchDestination = launchDestination,
                    lastOpenedDestination = lastOpened,
                    onboardingCompleted = true,
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

    private fun readState() = runBlocking {
        val database = AttentionDatabase.create(ApplicationProvider.getApplicationContext())
        try {
            repository(database).read()
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "launch-target"
        private const val TODAY_ENTRY_ID = "launch-today"
        private const val FAR_ENTRY_ID = "launch-far"
        private const val TODAY_ENTRY_TITLE = "UI-启动今日条目"
        private const val FAR_ENTRY_TITLE = "UI-启动远期条目"

        /** 远期条目放在第三个规划日：它与当前规划日一定不同，定位断言才有意义。 */
        private val TODAY: String = AttentionState().planningDate(Instant.now()).toString()
        private val FAR_DATE: String = AttentionState().planningDate(Instant.now()).plusDays(3).toString()
    }
}
