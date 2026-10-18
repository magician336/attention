package com.attention.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标识的字典序必须与创建顺序一致。
 *
 * 这不是洁癖：读路径用 `ORDER BY ... , id` 收尾，而同一规划日里没有精确发生时刻的
 * 时间记录（事后补记、日程完成确认）在排序里会并列，先后因此落到 id 上。
 * id 随机的话，同一份数据每次读出来的顺序都可能不同，重启之后还会换位置。
 */
class MonotonicIdTest {
    @Test
    fun ids_created_in_order_sort_in_that_order() {
        val ids = (1..200).map { newId(nowMillis = 1_700_000_000_000L + it) }

        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun ids_created_in_the_same_millisecond_still_increase() {
        val ids = (1..200).map { newId(nowMillis = 1_700_000_000_000L) }

        assertEquals(ids.distinct().size, ids.size)
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun a_clock_that_jumps_backwards_does_not_repeat_or_reorder() {
        val first = newId(nowMillis = 1_700_000_000_000L)
        val second = newId(nowMillis = 1_700_000_000_500L)
        // 时钟回拨：标识仍然必须严格递增，不能退回已经用过的区间。
        val afterRollback = newId(nowMillis = 1_600_000_000_000L)

        assertTrue(first < second)
        assertTrue(second < afterRollback)
    }

    @Test
    fun ids_keep_a_fixed_width_prefix_so_sorting_is_by_prefix_first() {
        val id = newId(nowMillis = 1_700_000_000_000L)

        // 定长前缀是排序成立的前提：变长的话 "0x" 之类会排到 "00..." 后面。
        // 9 位 base36 时钟前缀 + 10 位随机尾巴。宽度在生产代码里是私有的，
        // 这里只钉住「定长」这个性质，不为了测试放宽可见性。
        assertEquals(19, id.length)
        assertTrue(id.all { it.isLetterOrDigit() })
    }
}
