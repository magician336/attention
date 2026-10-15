package com.attention.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.RoomAttentionStateRepository
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import com.attention.domain.AttentionState
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置与数据入口的验收：底部导航只有三个工作区，设置页从统计页底部的「设置与数据」进入，
 * 返回时回到统计页，并且临时进入设置不改变「上次打开」的顶级页面记录。
 *
 * 这条用例走真实的 Workspace 宿主路径，因此验证的是用户看到、点到与最终存下来的东西。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceSettingsEntryIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_bottom_bar_has_only_the_three_workspaces_and_no_settings_entry() {
        // 设置不再是顶级入口：底部只有日程、目标树、统计三项
        composeRule.onAllNodes(hasNavTagPrefix()).assertCountEquals(3)

        composeRule.onNodeWithTag("nav-agenda").assert(hasText("日程"))
        composeRule.onNodeWithTag("nav-targets").assert(hasText("目标树"))
        composeRule.onNodeWithTag("nav-statistics").assert(hasText("统计"))
        composeRule.onAllNodes(hasSettingsNavEntry()).assertCountEquals(0)
    }

    @Test
    fun settings_opens_from_the_statistics_page_and_going_back_returns_to_statistics() {
        // 入口在统计页底部；进入后是独立设置页，返回后回到统计页
        openSettingsFromStatistics()

        composeRule.onNodeWithTag("settings-back").assertIsDisplayed()
        composeRule.onNodeWithTag("launch-destination-STATISTICS").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("统计与成长").assertIsDisplayed()
    }

    @Test
    fun planning_day_boundary_and_capacity_are_editable_here_and_reach_the_store() {
        // #172 下线今天页的每日容量卡片之后，规划日语义这三项在设置页才重新有入口，改完要真的存下来
        openSettingsFromStatistics()

        composeRule.onNodeWithTag("planning-boundary-input").performTextReplacement("90")
        composeRule.onNodeWithTag("daily-capacity-input").performTextReplacement("300")
        composeRule.onNodeWithTag("planning-settings-save").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) {
            readSettings().let { it.planningDayBoundaryMinutes == 90 && it.dailyCapacityMinutes == 300 }
        }

        assertEquals(90, readSettings().planningDayBoundaryMinutes)
        assertEquals(300, readSettings().dailyCapacityMinutes)
    }

    @Test
    fun the_week_start_day_is_editable_here_and_reaches_the_store() {
        // 周起始日是独立的公开设置命令：点选即生效，不必先保存
        openSettingsFromStatistics()

        composeRule.onNodeWithTag("settings-week-6").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) { readSettings().weekStartDay == 6 }

        assertEquals(6, readSettings().weekStartDay)
    }

    @Test
    fun entering_and_leaving_the_settings_page_does_not_change_the_last_opened_record() {
        // 设置页不是顶级工作区：进进出出之后「上次打开」仍然是统计
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(TIMEOUT) { readSettings().lastOpenedDestination == LaunchDestination.STATISTICS }

        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-back").performClick()
        composeRule.waitForIdle()

        assertEquals(LaunchDestination.STATISTICS, readSettings().lastOpenedDestination)
    }

    /** 从统计页底部的「设置与数据」进入独立设置页。 */
    private fun openSettingsFromStatistics() {
        composeRule.onNodeWithTag("nav-statistics").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics-open-settings").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /** 底部导航的每一项都带 `nav-` 前缀的标识，因此计数就等于顶级入口数。 */
    private fun hasNavTagPrefix(): SemanticsMatcher = SemanticsMatcher("底部导航入口") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav-") == true
    }

    /** 设置不再是底部导航项，因此不存在带 settings 的 `nav-` 标识。 */
    private fun hasSettingsNavEntry(): SemanticsMatcher = SemanticsMatcher("设置导航项") { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        tag != null && tag.startsWith("nav-") && tag.contains("settings")
    }

    companion object {
        private const val TIMEOUT = 10_000L
        private const val TARGET_ID = "ui-settings-target"

        private fun repository(database: AttentionDatabase) = RoomAttentionStateRepository(
            business = RoomBusinessDataRepository(database),
            settings = ApplicationProvider.getApplicationContext<android.content.Context>().createSettingsStore(),
        )

        @JvmStatic
        @BeforeClass
        fun seedBeforeActivityStarts() {
            val state = AttentionState(
                targets = listOf(Target(TARGET_ID, title = "UI-设置目标")),
                settings = StoredSettings(),
            )
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    repository(database).replace(state)
                    context.createSettingsStore().setSnapshot(
                        SettingsSnapshot(onboardingCompleted = true),
                    )
                }
            } finally {
                database.close()
            }
        }

        /** 读一次真实持久化设置，用来断言设置页的修改真的落盘了。 */
        private fun readSettings() = runBlocking {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .createSettingsStore()
                .settings
                .first()
        }
    }
}
