package com.attention.workspace.state

import com.attention.domain.RecurrenceRule
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import java.time.LocalDate

/**
 * 所有日期页新建或编辑日程条目时提交的一份表单内容。
 * 页面只填自己表单里的字符串解析结果，提醒时间的绝对时刻由组合根 adapter 按领域函数换算。
 */
data class AgendaScheduleDraft(
    val planningDate: String,
    val title: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val estimatedMinutes: Int?,
    val note: String,
    val targetId: String?,
    val reminderMinuteOfDay: Int?,
)

/**
 * 所有日期页创建重复日程规则时提交的一份表单内容。规则展开仍由 `:domain` 完成。
 */
data class AgendaRecurrenceDraft(
    val title: String,
    val startDate: String,
    val frequency: ScheduleFrequency,
    val weekdays: List<Int>,
    val dayOfMonth: Int?,
    val untilDate: String?,
    val estimatedMinutes: Int?,
    val targetId: String?,
    val reminderMinuteOfDay: Int?,
)

/**
 * 所有日期页的 intent 接口。页面只发出 intent，实际落地（写 Room、启动前台计时服务、
 * 申请通知权限）由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，也拿不到系统时钟。
 */
interface AgendaActions {
    /** 选中一个规划日；null 表示所有日期特殊视图。 */
    fun selectPlanningDate(date: LocalDate?)

    fun selectAllDates()

    fun shiftPlanningDay(days: Long)

    fun addSchedule(draft: AgendaScheduleDraft)

    /**
 * 所有日期页保存一条被编辑的日程条目。[reminderMinuteOfDay] 是提醒在规划日当天的分钟数；
 * 它的绝对时刻由组合根 adapter 用领域规则与注入的时区换算，页面因此不需要读系统时区。
 */
fun updateSchedule(entry: ScheduleEntry, reminderMinuteOfDay: Int?)

    fun confirmScheduleCompleted(entryId: String)

    fun toggleScheduleCompleted(entryId: String)

    /** 从日程条目起计时。[targetId] 与条目一起带出，产出的时间记录保留同一来源上下文。 */
    fun startTimerFromEntry(entry: ScheduleEntry)

    /**
     * 结束并保存运行中的计时。计时卡下线之后，这是日程页上唯一能停掉一次计时的入口，
     * 因此它必须留在同一页；结算、经验与里程碑仍由 `:domain` 计算，前台服务由组合根停掉。
     */
    fun stopRunningTimer()

    /**
     * 编辑一条实际投入，只调整时长、目标归属与备注。
     * 记录的规划日与来源由领域层保持不变，因此这里不接收它们。
     */
    fun editTime(entryId: String, minutes: Int, targetId: String?, note: String)

    /** 删除一条实际投入，并给出一次撤销机会；撤销提示由组合根展示。 */
    fun deleteTime(entryId: String)

    fun addRecurrenceAndMaterialize(draft: AgendaRecurrenceDraft)

    fun stopRecurrence(ruleId: String)

    /** 页面请求申请提醒权限；权限检查与申请由组合根 adapter 完成。 */
    fun requestReminderPermission()
}

/** 把重复日程草稿落成领域规则；物化仍然由 `:domain` 按规则展开。 */
fun AgendaRecurrenceDraft.toRecurrenceRule(): RecurrenceRule = RecurrenceRule(
    title = title,
    startDate = startDate,
    frequency = frequency,
    weekdays = weekdays,
    dayOfMonth = dayOfMonth,
    untilDate = untilDate,
    estimatedMinutes = estimatedMinutes,
    targetId = targetId,
    reminderMinuteOfDay = reminderMinuteOfDay,
)