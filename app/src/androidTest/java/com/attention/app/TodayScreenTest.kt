package com.attention.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.domain.AttentionState
import com.attention.domain.planningDate
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首次打开看到什么。
 *
 * 今天工作台在 #172 里与所有日期页合并成日程页，因此这里钉住的是合并后的首屏：
 * 默认落在当前规划日，并提供今天、明天与所有日期三个日期范围。
 * 工作台本身的旧断言由 #178 随旧代码一起删除。
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun first_open_shows_the_agenda_page_on_the_current_planning_day() {
        val planningDate = AttentionState().planningDate(Instant.now()).toString()

        composeRule.onNodeWithTag("nav-agenda").assert(hasText("日程"))
        composeRule.onNodeWithTag("agenda-filter").assert(hasText("规划日 $planningDate"))
        composeRule.onNodeWithTag("agenda-range-today").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-range-tomorrow").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda-range-all-dates").assertIsDisplayed()
    }
}
