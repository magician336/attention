package com.attention.app

import com.attention.workspace.state.AGENDA_UNDO_LABEL
import com.attention.workspace.state.AgendaOperationFeedback
import com.attention.workspace.state.agendaFeedbackMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合根反馈宿主的 JVM seam（#195）：成功 Snackbar 与删除撤销都从这里出发。
 *
 * 这一层不需要设备：[agendaLoadFacts] 是纯判定，反馈文案来自 `workspace-state` 的纯函数，
 * 因此「哪些操作有确认」「删除撤销的原文与撤销字样」都能在没有 Activity 的情况下钉住。
 * 真正把提示画出来的那部分由 `AttentionFeedbackComponentsTest` 在设备上验证。
 */
class WorkspaceAgendaFeedbackTest {

    @Test
    fun the_agenda_page_is_loading_only_until_the_local_state_has_been_read() {
        // 状态还没读上来：页面给轻量时间轴骨架，而不是一个空白工作台。
        val loading = agendaLoadFacts(stateLoaded = false, loadError = null)

        assertTrue("还没读到时必须是加载中", loading.loading)
        assertNull("加载中不是读取失败", loading.loadError)
    }

    @Test
    fun a_read_failure_wins_over_the_loading_skeleton_so_retry_is_reachable() {
        // 读取失败时如果仍然显示骨架，用户永远看不到那个「重试」。
        val failed = agendaLoadFacts(stateLoaded = false, loadError = "读取失败")

        assertTrue("读取失败时不能再停在加载骨架上", !failed.loading)
        assertEquals("读取失败", failed.loadError)
    }

    @Test
    fun a_read_workbench_is_neither_loading_nor_failed() {
        val loaded = agendaLoadFacts(stateLoaded = true, loadError = null)

        assertTrue("读上来之后不再是加载中", !loaded.loading)
        assertNull("读上来之后没有读取失败", loaded.loadError)
    }

    @Test
    fun every_operation_the_ticket_lists_has_a_short_confirmation_of_its_own() {
        // 添加日程、记录实际投入、开始/停止计时、修改或完成日程之后都有短时 Snackbar；
        // 成功提示走的是删除撤销同一条宿主通路，不是页面里的 Toast。
        val listed = listOf(
            AgendaOperationFeedback.SCHEDULE_ADDED,
            AgendaOperationFeedback.SCHEDULE_UPDATED,
            AgendaOperationFeedback.SCHEDULE_COMPLETED,
            AgendaOperationFeedback.SCHEDULE_COMPLETION_TOGGLED,
            AgendaOperationFeedback.TIME_RECORDED,
            AgendaOperationFeedback.TIMER_STARTED,
            AgendaOperationFeedback.TIMER_STOPPED,
        )

        val messages = listed.map(::agendaFeedbackMessage)
        messages.forEach { assertTrue("成功确认必须是非空的短提示：$it", it.isNotBlank()) }
        assertEquals("每个操作都要有自己的确认文案", listed.size, messages.toSet().size)
    }

    @Test
    fun the_deletion_undo_path_keeps_its_wording_and_its_undo_entry() {
        // 删除撤销是既有行为：文案原文与「撤销」字样都不因本票改动，
        // 否则用户会以为那不是同一个入口。
        assertEquals(
            "已删除这条实际投入，它不再计入目标进度与统计。",
            agendaFeedbackMessage(AgendaOperationFeedback.TIME_DELETED),
        )
        assertEquals("撤销", AGENDA_UNDO_LABEL)
    }

    @Test
    fun retrying_reads_again_instead_of_rendering_the_same_failed_data() {
        // 重试给的是「正在重新读取」，不是又一次成功——用户据此知道还没恢复。
        val retrying = agendaFeedbackMessage(AgendaOperationFeedback.LOAD_RETRYING)

        assertTrue("重试应当说明正在重新读取：$retrying", retrying.contains("重新读取"))
    }
}
