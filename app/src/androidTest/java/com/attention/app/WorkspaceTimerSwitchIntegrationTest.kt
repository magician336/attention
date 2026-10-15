package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.Target
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceTimerSwitchIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_running_timer_stays_visible_and_stoppable_on_the_merged_agenda_page() {
        // 今天工作台的计时卡下线之后，运行中的计时仍然必须在日程页看得见并停得掉
        composeRule.onNodeWithTag("nav-agenda", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("正在计时：计时目标", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-running-timer-stop", useUnmergedTree = true).performScrollTo().performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("agenda-running-timer").fetchSemanticsNodes().isEmpty()
        }
    }

    companion object {
        private const val TARGET_ID = "ui-switch-target"

        @JvmStatic
        @BeforeClass
        fun seedActiveTimerBeforeActivityStarts() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = AttentionDatabase.create(context)
            try {
                runBlocking {
                    RoomBusinessDataRepository(database).replace(
                        AttentionState(
                            targets = listOf(Target(TARGET_ID, title = "计时目标")),
                            activeTimer = ActiveTimer(
                                targetId = TARGET_ID,
                                startedAtEpochMillis = Instant.now().minusSeconds(60).toEpochMilli(),
                                lastPlanningDate = LocalDate.now().toString(),
                            ),
                        ),
                    )
                }
            } finally {
                database.close()
            }
        }
    }
}
