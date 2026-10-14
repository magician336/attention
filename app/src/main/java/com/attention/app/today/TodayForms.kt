package com.attention.app.today

import java.time.LocalDate

/**
 * 今天工作台「今日安排」里编辑日程对话框的表单输入。
 *
 * 这个类型属于今天工作台 module：编辑草稿的取舍是页面自己的语义，
 * 组合根不认识它（ADR-0005 第 1 节）。
 */
internal data class TodayScheduleFormInput(
    val title: String,
    val date: LocalDate?,
    val startText: String,
    val endText: String,
    val estimateText: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val estimateMinutes: Int?,
    val reminderText: String = "",
    val reminderMinute: Int? = null,
)

/** 把 `HH:mm` 解析成一天内的分钟数；空输入表示不设。 */
internal fun parseTodayScheduleMinute(value: String, allowEndOfDay: Boolean = false): Int? {
    if (value.isBlank()) return null
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if ((hour in 0..23 && minute in 0..59) || (allowEndOfDay && hour == 24 && minute == 0)) hour * 60 + minute else null
}

/** 编辑日程表单的校验。文案与迁移前逐字一致，用户的提示不因 seam 改变。 */
internal fun validateTodayScheduleForm(input: TodayScheduleFormInput): String? = when {
    input.date == null -> "请输入有效的规划日"
    input.title.isBlank() -> "日程标题不能为空"
    input.startText.isNotBlank() && input.startMinute == null -> "请输入有效的开始时间 HH:mm"
    input.endText.isNotBlank() && input.endMinute == null -> "请输入有效的结束时间 HH:mm"
    (input.startMinute == null) != (input.endMinute == null) -> "时间段必须同时填写开始和结束时间"
    input.startMinute != null && input.endMinute != null && input.endMinute <= input.startMinute -> "结束时间必须晚于开始时间"
    input.estimateText.isNotBlank() && input.estimateMinutes == null -> "请输入有效的预计分钟"
    input.estimateMinutes != null && input.estimateMinutes <= 0 -> "预计时长必须为正数"
    input.reminderText.isNotBlank() && input.reminderMinute == null -> "请输入有效的提醒时间 HH:mm"
    else -> null
}
