package com.attention.workspace.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 短时反馈文案的一处定义：组合根的反馈宿主展示它们，页面不自己编文案也不自己弹 Toast。
 *
 * 这里同时钉住「删除有撤销」这条既有通路的原文：撤销提示的文案与撤销按钮的字样
 * 属于用户已经看到过的行为，改动它就是改行为，必须先由用例说清楚。
 */
class AgendaFeedbackTest {

    @Test
    fun every_mutating_operation_has_its_own_short_confirmation() {
        val messages = AgendaOperationFeedback.entries.associate { it to agendaFeedbackMessage(it) }

        messages.forEach { (op, message) ->
            assertTrue("$op 必须有非空的短时确认文案", message.isNotBlank())
            assertTrue("$op 的确认文案应当是一句短提示，实际：$message", message.length <= 40)
        }
        assertEquals(
            "不同操作必须给出不同确认，否则用户分不清刚才是哪一步成功了",
            messages.values.toSet().size,
            messages.size,
        )
    }

    @Test
    fun the_deletion_feedback_keeps_its_undoable_wording() {
        // 删除实际投入的既有通路：确认已经发生，撤销入口仍然可用。
        // 组合根用同一条通路承载成功反馈，因此这条文案仍然是用户看到过的那一句。
        assertEquals(
            "已删除这条实际投入，它不再计入目标进度与统计。",
            agendaFeedbackMessage(AgendaOperationFeedback.TIME_DELETED),
        )
        assertEquals("撤销", AGENDA_UNDO_LABEL)
    }

    @Test
    fun the_read_failure_asks_for_a_retry_rather_than_a_dismiss() {
        // 读取失败必须给用户自己救回来的入口，而不是只有关闭。
        assertEquals("重试", AGENDA_RETRY_LABEL)
        assertTrue(agendaFeedbackMessage(AgendaOperationFeedback.LOAD_FAILED).contains("重试"))
    }
}
