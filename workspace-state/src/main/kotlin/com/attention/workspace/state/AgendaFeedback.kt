package com.attention.workspace.state

/**
 * 日程页上的一次「用户刚做过的操作」。
 *
 * 这些操作的成功反馈由**组合根的反馈宿主**展示（短时 Snackbar），页面只发出 intent，
 * 因此这里描述的是「刚刚发生了什么」，而不是「页面要画什么」。
 * 页面与组合根都需要知道「刚才是哪一步成功了」，文案因此放在两侧都能读的纯 Kotlin module 里，
 * 而不是散落在组合根的 lambda 里。
 *
 * [TIME_DELETED] 是特例：它带一次撤销机会，因此由组合根用带撤销的提示展示（见
 * [AgendaFeedbackTest]）——删除撤销走的是同一条反馈宿主通路，不是另一套实现。
 */
enum class AgendaOperationFeedback {
    /** 添加了一条日程条目。 */
    SCHEDULE_ADDED,

    /** 修改了一条日程条目（标题、时间、预计时长或提醒）。 */
    SCHEDULE_UPDATED,

    /** 把一条日程标记为完成。 */
    SCHEDULE_COMPLETED,

    /** 切换了一条日程的完成状态（完成 ↔ 取消完成）。 */
    SCHEDULE_COMPLETION_TOGGLED,

    /** 记录或修改了一条实际投入。 */
    TIME_RECORDED,

    /** 从日程条目起了一次计时。 */
    TIMER_STARTED,

    /** 结束并保存了一次计时，它已经计入实际投入。 */
    TIMER_STOPPED,

    /** 删除了一条实际投入；这条带撤销。 */
    TIME_DELETED,

    /** 本地数据读取失败。 */
    LOAD_FAILED,

    /** 用户点了「重试」，正在重新读取本地数据。 */
    LOAD_RETRYING,

    /** 创建了重复日程规则并物化了未来 30 天。 */
    RECURRENCE_ADDED,

    /** 停止了一条重复日程的未来生成。 */
    RECURRENCE_STOPPED,
}

/** 撤销按钮的字样。它是用户已经习惯的动作名，因此不随文案一起改。 */
const val AGENDA_UNDO_LABEL: String = "撤销"

/** 重试按钮的字样。读取失败必须给「重试」，不能只给「关闭」（#195 验收标准）。 */
const val AGENDA_RETRY_LABEL: String = "重试"

/**
 * 一次操作之后给用户的短时确认文案。
 *
 * 文案全部是「已经发生了什么」而不是「你该做什么」：流程不被打断，用户不必为了确认
 * 一次保存而停下来。读取失败是唯一的例外——它必须说清下一步是重试。
 */
fun agendaFeedbackMessage(operation: AgendaOperationFeedback): String = when (operation) {
    AgendaOperationFeedback.SCHEDULE_ADDED -> "已添加这条日程"
    AgendaOperationFeedback.SCHEDULE_UPDATED -> "已保存这条日程的修改"
    AgendaOperationFeedback.SCHEDULE_COMPLETED -> "已标记为完成"
    AgendaOperationFeedback.SCHEDULE_COMPLETION_TOGGLED -> "已更新这条日程的完成状态"
    AgendaOperationFeedback.TIME_RECORDED -> "已记录这条实际投入"
    AgendaOperationFeedback.TIMER_STARTED -> "已开始计时"
    AgendaOperationFeedback.TIMER_STOPPED -> "已结束计时并计入实际投入"
    AgendaOperationFeedback.TIME_DELETED -> "已删除这条实际投入，它不再计入目标进度与统计。"
    AgendaOperationFeedback.LOAD_FAILED -> "没能读到本地数据，你可以重试一次。"
    AgendaOperationFeedback.LOAD_RETRYING -> "正在重新读取本地数据"
    AgendaOperationFeedback.RECURRENCE_ADDED -> "已创建重复日程并生成未来 30 天"
    AgendaOperationFeedback.RECURRENCE_STOPPED -> "已停止这条重复日程的未来生成"
}
