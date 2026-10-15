package com.attention.workspace.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次使用引导的投影。
 *
 * 引导是说明式的：三个说明页固定为日程、目标树、统计，顺序稳定；它不读任何业务数据，
 * 因此投影不需要 [com.attention.domain.AttentionState]，也不会随目标、日程或时间记录变化。
 */
class OnboardingScreenStateTest {

    @Test
    fun the_guide_has_exactly_three_pages_in_a_stable_order() {
        val pages = onboardingScreenState().pages

        assertEquals(listOf("日程", "目标树", "统计"), pages.map { it.title })
    }

    @Test
    fun every_page_explains_its_entry_and_asks_for_no_data() {
        val pages = onboardingScreenState().pages

        pages.forEach { page ->
            assertTrue("${page.title} 缺少说明正文", page.body.isNotBlank())
            // 说明页只讲职责，不要求用户先创建计划、日程或设置，因此正文里不出现这些祈使要求
            listOf("请创建", "请先设置", "请填写").forEach { demand ->
                assertTrue(
                    "${page.title} 的正文不应要求用户先做别的事：$demand",
                    !page.body.contains(demand),
                )
            }
        }
    }

    @Test
    fun the_projection_is_pure_text_so_it_cannot_carry_business_data() {
        // 两次投影完全一致：说明页不依赖任何领域状态，因此引导前后不会读写目标、日程或时间记录。
        assertEquals(onboardingScreenState(), onboardingScreenState())
    }
}
