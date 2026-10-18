package com.attention.app.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.domain.ScheduleFrequency
import com.attention.workspace.state.AgendaRecurrenceDraft
import com.attention.workspace.state.AgendaBackfillDraft
import com.attention.workspace.state.AgendaScheduleDraft
import java.time.LocalDate

/**
 * 日程页的新建日程表单状态。它属于页面 module 而不是组合根（ADR-0005 第 1 节）：
 * 组合根既不声明这些字段，也不清空它们。
 */
internal class AgendaScheduleForm {
    var title by mutableStateOf("")
        private set
    var startTime by mutableStateOf("")
        private set
    var endTime by mutableStateOf("")
        private set
    var estimate by mutableStateOf("")
        private set
    var reminderTime by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var targetId by mutableStateOf<String?>(null)
        private set
    var targetMenuExpanded by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    fun changeTitle(value: String) {
        title = value
        error = null
    }

    fun changeStartTime(value: String) {
        startTime = value
        error = null
    }

    fun changeEndTime(value: String) {
        endTime = value
        error = null
    }

    fun changeEstimate(value: String) {
        estimate = value
        error = null
    }

    fun changeReminderTime(value: String) {
        reminderTime = value
        error = null
    }

    fun changeNote(value: String) {
        note = value
    }

    fun selectTarget(targetId: String?) {
        this.targetId = targetId
        targetMenuExpanded = false
    }

    fun toggleTargetMenu(expanded: Boolean) {
        targetMenuExpanded = expanded
    }

    fun showError(message: String) {
        error = message
    }

    /** 提交成功后清空表单，保持与迁移前一致的字段重置集合。 */
    fun reset() {
        title = ""
        startTime = ""
        endTime = ""
        estimate = ""
        reminderTime = ""
        note = ""
        targetId = null
        error = null
    }
}

/** 页面共用的事后补记时长编辑器；分钟部分只接受 5 分钟步进。 */
internal class AgendaBackfillForm {
    var hours by mutableStateOf("")
        private set
    var minutes by mutableStateOf("")
        private set
    var error: String? by mutableStateOf(null)
        private set
    private var initialized by mutableStateOf(false)

    fun initialize(defaultMinutes: Int) {
        if (initialized) return
        setTotal(defaultMinutes.coerceAtLeast(5))
        initialized = true
    }

    fun setPreset(totalMinutes: Int) {
        setTotal(totalMinutes)
        error = null
    }

    fun changeHours(value: String) {
        hours = value
        error = null
    }

    fun changeMinutes(value: String) {
        minutes = value
        error = null
    }

    fun totalMinutes(): Int? {
        val hourValue = hours.trim().toIntOrNull() ?: return null
        val minuteValue = minutes.trim().toIntOrNull() ?: return null
        return (hourValue * 60 + minuteValue).takeIf { it > 0 && minuteValue in 0..55 && minuteValue % 5 == 0 }
    }

    fun validate(): String? {
        val hourValue = hours.trim().toIntOrNull()
        val minuteValue = minutes.trim().toIntOrNull()
        return when {
            hourValue == null || hourValue < 0 -> "请输入有效的小时"
            minuteValue == null || minuteValue !in 0..55 || minuteValue % 5 != 0 -> "分钟必须是 0 到 55 之间的 5 分钟步进"
            hourValue * 60 + minuteValue < 5 -> "实际投入至少为 5 分钟"
            else -> null
        }
    }

    fun validateAndShowError(): Boolean {
        error = validate()
        return error == null
    }

    fun toDraft(targetId: String?, note: String = ""): AgendaBackfillDraft = AgendaBackfillDraft(
        durationMinutes = totalMinutes() ?: error("事后补记时长尚未校验"),
        targetId = targetId,
        note = note,
    )

    fun reset() {
        hours = ""
        minutes = ""
        error = null
        initialized = false
    }

    private fun setTotal(totalMinutes: Int) {
        hours = (totalMinutes / 60).toString()
        minutes = (totalMinutes % 60).toString()
    }
}

/**
 * 日程页的重复日程表单状态，同样属于页面 module。
 */
internal class AgendaRecurrenceForm {
    var title by mutableStateOf("")
        private set
    var frequency by mutableStateOf(ScheduleFrequency.DAILY)
        private set
    var untilDate by mutableStateOf("")
        private set
    var estimatedMinutes by mutableStateOf("")
        private set
    var reminderTime by mutableStateOf("")
        private set
    var weekdays by mutableStateOf(setOf<Int>())
        private set
    var dayOfMonth by mutableStateOf("")
        private set
    var targetId by mutableStateOf<String?>(null)
        private set
    var targetMenuExpanded by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    fun changeTitle(value: String) {
        title = value
        error = null
    }

    fun selectFrequency(value: ScheduleFrequency) {
        frequency = value
        error = null
    }

    fun changeUntilDate(value: String) {
        untilDate = value
        error = null
    }

    fun changeEstimatedMinutes(value: String) {
        estimatedMinutes = value
        error = null
    }

    fun changeReminderTime(value: String) {
        reminderTime = value
        error = null
    }

    fun toggleWeekday(weekday: Int, checked: Boolean) {
        weekdays = if (checked) weekdays + weekday else weekdays - weekday
        error = null
    }

    fun changeDayOfMonth(value: String) {
        dayOfMonth = value
        error = null
    }

    fun selectTarget(targetId: String?) {
        this.targetId = targetId
        targetMenuExpanded = false
    }

    fun toggleTargetMenu(expanded: Boolean) {
        targetMenuExpanded = expanded
    }

    fun showError(message: String) {
        error = message
    }

    /** 提交成功后清空表单；频率选择保持迁移前不变。 */
    fun reset() {
        title = ""
        untilDate = ""
        estimatedMinutes = ""
        reminderTime = ""
        weekdays = emptySet()
        dayOfMonth = ""
        targetId = null
        error = null
    }
}

@Composable
internal fun rememberAgendaScheduleForm(): AgendaScheduleForm = remember { AgendaScheduleForm() }

@Composable
internal fun rememberAgendaBackfillForm(): AgendaBackfillForm = remember { AgendaBackfillForm() }

@Composable
internal fun rememberAgendaRecurrenceForm(): AgendaRecurrenceForm = remember { AgendaRecurrenceForm() }

/** 新建日程的表单输入。校验与迁移前逐条一致，页面在提交前先给出同样的提示。 */
internal data class ScheduleFormInput(
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

internal fun parseScheduleMinute(value: String, allowEndOfDay: Boolean = false): Int? {
    if (value.isBlank()) return null
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if ((hour in 0..23 && minute in 0..59) || (allowEndOfDay && hour == 24 && minute == 0)) hour * 60 + minute else null
}

internal fun validateScheduleForm(input: ScheduleFormInput): String? = when {
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

internal fun validateRecurrenceForm(
    title: String,
    startDate: LocalDate?,
    untilDateText: String,
    untilDate: LocalDate?,
    frequency: ScheduleFrequency,
    weekdays: Set<Int>,
    dayOfMonth: Int?,
    estimatedMinutesText: String,
    estimatedMinutes: Int?,
    reminderText: String,
    reminderMinute: Int?,
): String? = when {
    startDate == null -> "请输入有效的开始规划日"
    title.isBlank() -> "重复日程标题不能为空"
    untilDateText.isNotBlank() && untilDate == null -> "请输入有效的结束规划日"
    untilDate != null && untilDate.isBefore(startDate) -> "结束规划日不能早于开始规划日"
    estimatedMinutesText.isNotBlank() && estimatedMinutes == null -> "请输入有效的预计分钟"
    estimatedMinutes != null && estimatedMinutes <= 0 -> "预计时长必须为正数"
    reminderText.isNotBlank() && reminderMinute == null -> "请输入有效的提醒时间 HH:mm"
    frequency == ScheduleFrequency.WEEKLY && weekdays.isEmpty() -> "每周规则至少选择一个星期"
    frequency == ScheduleFrequency.MONTHLY && dayOfMonth !in 1..31 -> "月内日期必须在 1 到 31 之间"
    frequency != ScheduleFrequency.MONTHLY && dayOfMonth != null -> "只有每月规则可以设置月内日期"
    else -> null
}

/** 把新建日程表单解析成草稿；解析与校验都已在提交前完成。 */
internal fun AgendaScheduleForm.toDraft(planningDate: LocalDate): AgendaScheduleDraft = AgendaScheduleDraft(
    planningDate = planningDate.toString(),
    title = title,
    startMinute = parseScheduleMinute(startTime),
    endMinute = parseScheduleMinute(endTime, allowEndOfDay = true),
    estimatedMinutes = estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull(),
    note = note,
    targetId = targetId,
    reminderMinuteOfDay = parseScheduleMinute(reminderTime),
)

/** 把重复日程表单解析成草稿。 */
internal fun AgendaRecurrenceForm.toDraft(startDate: LocalDate): AgendaRecurrenceDraft = AgendaRecurrenceDraft(
    title = title,
    startDate = startDate.toString(),
    frequency = frequency,
    weekdays = weekdays.toList().sorted(),
    dayOfMonth = dayOfMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull(),
    untilDate = untilDate.trim().takeIf(String::isNotEmpty)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }?.toString(),
    estimatedMinutes = estimatedMinutes.trim().takeIf(String::isNotEmpty)?.toIntOrNull(),
    targetId = targetId,
    reminderMinuteOfDay = parseScheduleMinute(reminderTime),
)
