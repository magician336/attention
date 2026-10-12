package com.attention.app.timer

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.Target
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTimerNotificationTest {
    @Test
    fun running_timer_notification_contains_target_and_running_status() {
        val content = AttentionTimerService.notificationContent(
            AttentionState(
                targets = listOf(Target(id = "target-1", title = "写设计")),
                activeTimer = ActiveTimer(
                    targetId = "target-1",
                    startedAtEpochMillis = 10L,
                    lastPlanningDate = "2026-10-12",
                ),
            ),
            now = Instant.ofEpochMilli(70_010L),
        )

        assertEquals("Attention 正在计时", content.title)
        assertEquals("目标 写设计 · 正在计时 · 时长 00:01", content.text)
        assertTrue(content.active)
        assertFalse(content.paused)
    }

    @Test
    fun paused_unowned_timer_notification_exposes_resume_state() {
        val content = AttentionTimerService.notificationContent(
            AttentionState(
                activeTimer = ActiveTimer(
                    targetId = null,
                    startedAtEpochMillis = 10L,
                    paused = true,
                    lastPlanningDate = "2026-10-12",
                ),
            ),
            now = Instant.ofEpochMilli(70_010L),
        )

        assertEquals("Attention 已暂停", content.title)
        assertEquals("目标 未归属活动 · 已暂停 · 时长 00:00", content.text)
        assertTrue(content.active)
        assertTrue(content.paused)
    }

    @Test
    fun missing_timer_is_not_ongoing_and_has_no_controls() {
        val content = AttentionTimerService.notificationContent(null)

        assertFalse(content.active)
        assertFalse(content.paused)
        assertEquals("等待计时器状态", content.text)
    }
}
