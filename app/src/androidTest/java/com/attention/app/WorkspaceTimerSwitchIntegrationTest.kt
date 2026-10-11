package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun active_timer_can_switch_to_unowned_activity_from_workbench() {
        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.onNodeWithText("当前目标：计时目标").assertExists()
        composeRule.onNodeWithTag("switch-timer-unowned").assertExists()
        composeRule.onNodeWithText("切换到未归属活动").assertExists()
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
