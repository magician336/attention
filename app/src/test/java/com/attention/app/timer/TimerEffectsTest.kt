package com.attention.app.timer

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEffectsTest {
    @Test
    fun starting_a_timer_clears_recovery_warns_when_notifications_are_disabled_and_starts_once() {
        val platform = RecordingTimerPlatform(notificationsEnabled = false)
        val messages = mutableListOf<String>()
        val effects = AndroidTimerEffects(platform, messages::add)

        effects.started()
        effects.started()

        assertEquals(listOf("clearRecoveryPrompt", "startForegroundService"), platform.events)
        assertEquals(listOf("通知权限未开启，计时仍会保存；通知栏控制不可用。"), messages)
    }

    @Test
    fun paused_and_resumed_timer_keep_the_foreground_service_alive() {
        val platform = RecordingTimerPlatform()
        val effects = AndroidTimerEffects(platform, {})

        effects.started()
        effects.paused()
        effects.resumed()

        assertEquals(
            listOf("clearRecoveryPrompt", "startForegroundService"),
            platform.events,
        )
    }

    @Test
    fun stopping_clears_recovery_and_stops_service_once() {
        val platform = RecordingTimerPlatform()
        val effects = AndroidTimerEffects(platform, {})

        effects.started()
        effects.stopped()
        effects.stopped()

        assertEquals(
            listOf("clearRecoveryPrompt", "startForegroundService", "clearRecoveryPrompt", "stopForegroundService"),
            platform.events,
        )
    }

    @Test
    fun synchronization_starts_active_timer_and_removes_stale_service_when_timer_is_gone() {
        val platform = RecordingTimerPlatform()
        val effects = AndroidTimerEffects(platform, {})
        val active = AttentionState(
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = 10L,
                lastPlanningDate = "2026-10-19",
            ),
        )

        effects.synchronize(active)
        effects.synchronize(active)
        effects.synchronize(AttentionState())
        effects.synchronize(AttentionState())

        assertEquals(
            listOf("startForegroundService", "stopForegroundService"),
            platform.events,
        )
    }

    @Test
    fun platform_failure_is_reported_without_changing_domain_state() {
        val platform = RecordingTimerPlatform(startFailure = IllegalStateException("blocked"))
        val messages = mutableListOf<String>()
        val effects = AndroidTimerEffects(platform, messages::add)

        effects.started()

        assertTrue(messages.single().contains("前台计时服务启动失败"))
        assertEquals(listOf("clearRecoveryPrompt", "startForegroundService"), platform.events)
    }
}

private class RecordingTimerPlatform(
    private val notificationsEnabled: Boolean = true,
    private val startFailure: Throwable? = null,
) : TimerPlatform {
    val events = mutableListOf<String>()

    override fun clearRecoveryPrompt() {
        events += "clearRecoveryPrompt"
    }

    override fun startForegroundService() {
        events += "startForegroundService"
        startFailure?.let { throw it }
    }

    override fun stopForegroundService() {
        events += "stopForegroundService"
    }

    override fun notificationsEnabled(): Boolean = notificationsEnabled
}
