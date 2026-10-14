package com.attention.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 累计投入里程碑的 kind 与 instanceKey 格式是 :domain 的规则（ADR-0004）。
 * 页面投影只读取这里的结果，不再自己过滤 kind、排序或解析 key。
 */
class CumulativeMilestoneTest {
    @Test
    fun cumulative_milestones_are_ordered_and_deduplicated_by_instance_key() {
        val state = AttentionState(
            milestones = listOf(
                Milestone("b", "cumulative", "cumulative:200", 2_000L, 20L),
                Milestone("a", "cumulative", "cumulative:100", 1_000L, 10L),
                Milestone("c", "cumulative", "cumulative:100", 1_000L, 30L),
                Milestone("d", "streak", "streak:t1:7", 500L, 5L),
                Milestone("e", "goal_period", "goal_period:s1:2026-03-01", 500L, 8L),
            ),
        )

        assertEquals(listOf("a", "b"), state.cumulativeMilestones().map { it.id })
    }

    @Test
    fun cumulative_hours_are_read_from_the_domain_instance_key() {
        assertEquals(100L, Milestone("a", "cumulative", "cumulative:100", 1_000L, 1L).cumulativeHours())
        assertNull(Milestone("a", "streak", "streak:t1:7", 500L, 1L).cumulativeHours())
        assertNull(Milestone("a", "cumulative", "cumulative:not-a-number", 1_000L, 1L).cumulativeHours())
    }

    @Test
    fun awarded_cumulative_milestones_are_visible_through_the_domain_reader() {
        val state = AttentionState(
            milestones = listOf(Milestone("a", "cumulative", "cumulative:100", 1_000L, 1L)),
        ).cumulativeMilestones()

        assertEquals(1, state.size)
        assertEquals(100L, state.single().cumulativeHours())
    }
}