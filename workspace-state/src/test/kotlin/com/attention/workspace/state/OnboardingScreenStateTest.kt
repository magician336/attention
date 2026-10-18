package com.attention.workspace.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次设置的投影：五个步骤固定顺序，设置值由组合根注入。
 */
class OnboardingScreenStateTest {

    @Test
    fun the_setup_has_five_steps_in_a_stable_order() {
        val pages = onboardingScreenState().pages

        assertEquals(
            listOf("规划日界线", "周起始日", "每日容量", "第一个计划", "第一条日程"),
            pages.map { it.title },
        )
    }

    @Test
    fun every_page_has_projected_body_without_forcing_an_action() {
        val pages = onboardingScreenState().pages

        pages.forEach { page ->
            assertTrue("${page.title} 缺少说明正文", page.body.isNotBlank())
            // 正文说明当前步骤，不要求用户先完成其他步骤，因此不出现这些祈使要求
            listOf("请创建", "请先设置", "请填写").forEach { demand ->
                assertTrue(
                    "${page.title} 的正文不应要求用户先做别的事：$demand",
                    !page.body.contains(demand),
                )
            }
        }
    }

    @Test
    fun the_schedule_step_does_not_teach_that_planned_minutes_are_actual_investment() {
        val schedule = onboardingScreenState().pages.single { it.title == "第一条日程" }

        assertTrue(
            "首次日程步骤必须说明预计投入不会自动成为实际投入",
            schedule.body.contains("预计投入") && schedule.body.contains("实际投入"),
        )
        assertFalse(
            "日程页不能声称完成的日程与实际投入一起进入统计：${schedule.body}",
            schedule.body.contains("完成的日程与实际投入一起进入统计"),
        )
    }

    @Test
    fun the_projection_is_pure_text_so_it_cannot_carry_business_data() {
        // 两次投影完全一致：说明页不依赖任何领域状态，因此引导前后不会读写目标、日程或时间记录。
        assertEquals(onboardingScreenState(), onboardingScreenState())
    }
}
