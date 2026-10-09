package com.attention.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceRoomIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun target_tree_screen_is_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("目标树") and hasClickAction()).performClick()
        composeRule.onNodeWithText("父目标显示整个子树的实际投入；归档目标会从默认列表隐藏。").assertExists()
    }

    @Test
    fun agenda_screen_is_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("所有日期") and hasClickAction()).performClick()
        composeRule.onNodeWithText("日程条目可带预计时长；它不会自动变成实际投入。").assertExists()
    }

    @Test
    fun timer_controls_are_available_from_room_backed_workspace() {
        composeRule.onNode(hasText("今天") and hasClickAction()).performClick()
        composeRule.onNodeWithText("计时器").assertExists()
        composeRule.onNodeWithText("开始未归属计时").assertExists()
    }
}
