package com.attention.app.ui.format

import com.attention.domain.ScheduleEntry
import com.attention.domain.TimeEntrySource

/**
 * 时间记录来源的中文表达。名字取自 `CONTEXT.md` 已有词汇，不新造领域词。
 *
 * “按计划记录”只属于日程确认来源：它是规格明确要求的标记，用来把承诺与精确计时区分开。
 */
object TimeEntrySourceFormat {

    const val SCHEDULE_CONFIRMED_LABEL = "按计划记录"

    fun labelOf(source: TimeEntrySource): String = when (source) {
        TimeEntrySource.SCHEDULE_CONFIRMED -> SCHEDULE_CONFIRMED_LABEL
        TimeEntrySource.TIMER -> "计时"
        TimeEntrySource.MANUAL -> "事后补记"
        TimeEntrySource.IMPORT -> "导入"
    }

    /**
     * 日程条目给出的计划时间表达。
     *
     * 有起止时间就显示区间；只有预计时长就显示时长；两者都没有就不编造时间。
     */
    fun plannedTimeOf(schedule: ScheduleEntry?): String {
        if (schedule == null) return NO_SCHEDULE
        val start = schedule.startMinute
        val end = schedule.endMinute
        if (start != null && end != null) return "${MinuteOfDay.format(start)} – ${MinuteOfDay.format(end)}"
        val estimated = schedule.estimatedMinutes
        if (estimated != null) {
            return "预计 ${DurationFormat.ofMinutes(estimated)}"
        }
        return NO_SCHEDULE
    }

    const val NO_SCHEDULE = "未关联日程"
}

/**
 * 一天之内第几分钟的定点表达。
 *
 * 这里只做定点格式化，不参与规划日判定——规划日界线由领域层负责。
 */
object MinuteOfDay {

    private const val MINUTES_PER_HOUR = 60

    fun format(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_HOUR * 24 - 1)
        return "%02d:%02d".format(clamped / MINUTES_PER_HOUR, clamped % MINUTES_PER_HOUR)
    }
}