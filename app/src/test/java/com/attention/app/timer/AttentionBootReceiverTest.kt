package com.attention.app.timer

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionBootReceiverTest {
    @Test
    fun recovery_prompt_requires_an_active_timer() {
        assertFalse(AttentionBootReceiver.shouldPrompt(AttentionState()))
        assertTrue(
            AttentionBootReceiver.shouldPrompt(
                AttentionState(
                    activeTimer = ActiveTimer(
                        targetId = null,
                        startedAtEpochMillis = 10L,
                        paused = true,
                        lastPlanningDate = "2026-10-12",
                    ),
                ),
            ),
        )
    }

}
