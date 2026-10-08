package com.attention.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun first_open_shows_today_workbench_and_default_settings() {
        composeRule.onNodeWithText("今天工作台").assertIsDisplayed()
        composeRule.onNodeWithText("规划日界线 03:00 · 每周从周一开始").assertIsDisplayed()
    }
}
