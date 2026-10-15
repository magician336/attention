package com.attention.app.agenda

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日程页次级区块的折叠语义：重复日程默认收起来，不占首屏主路径，但展开后创建规则与
 * 「停止未来生成」仍然可达。状态属于日程页 module，因此这里直接测它的迁移。
 */
class AgendaDisclosureTest {

    @Test
    fun the_recurring_schedule_block_starts_collapsed() {
        // 首屏第一眼是规划日与容量摘要、时间轴、计时与添加日程，而不是重复日程表单
        assertFalse(AgendaDisclosureState().recurrenceVisible)
    }

    @Test
    fun expanding_and_collapsing_reveals_the_same_block_again() {
        val disclosure = AgendaDisclosureState()

        disclosure.toggleRecurrence()
        assertTrue("展开后创建与停止未来生成都可达", disclosure.recurrenceVisible)

        disclosure.toggleRecurrence()
        assertFalse("再点一次收回次级入口", disclosure.recurrenceVisible)
    }

    @Test
    fun two_instances_keep_their_own_state() {
        // 两个页面实例互不干扰：折叠不是全局开关
        val first = AgendaDisclosureState()
        val second = AgendaDisclosureState()

        first.toggleRecurrence()

        assertTrue(first.recurrenceVisible)
        assertFalse(second.recurrenceVisible)
    }
}
