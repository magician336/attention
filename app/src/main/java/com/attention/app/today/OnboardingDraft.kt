package com.attention.app.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.workspace.state.OnboardingScheduleDraft
import com.attention.workspace.state.TodayScreenState
import java.time.LocalDate

/**
 * 首次设置流程自己的表单状态：当前走到第几步、五个步骤各自的草稿与上一次的报错。
 *
 * 它属于今天工作台 module 而不是组合根：ADR-0005 第 1 节要求表单草稿由页面 module 拥有，
 * 组合根既不声明这个类型，也不改写它的字段。
 */
internal class OnboardingDraft(planningDate: String) {

    /** 当前步骤，0 到 4。 */
    var step: Int by mutableStateOf(0)
        private set

    /** 规划日界线输入框，默认取设置里的值。 */
    var boundary: String by mutableStateOf("")
        private set

    /** 每周起始日，默认取设置里的值。 */
    var weekStartDay: Int by mutableStateOf(1)
        private set

    /** 每日容量输入框，默认取设置里的值；未设置时为空。 */
    var capacity: String by mutableStateOf("")
        private set

    var planTitle: String by mutableStateOf("")
        private set

    var scheduleTitle: String by mutableStateOf("")
        private set

    /**
     * 日程的规划日初值。它只在这一刻取一次：用户已经打开这个表单之后，
     * 规划日就由他自己输入或保持原值，不跟着时钟或重算漂移。
     */
    var scheduleDate: String by mutableStateOf(planningDate)
        private set

    var scheduleEstimate: String by mutableStateOf("")
        private set

    var scheduleStart: String by mutableStateOf("")
        private set

    var scheduleEnd: String by mutableStateOf("")
        private set

    var scheduleNote: String by mutableStateOf("")
        private set

    var scheduleTargetId: String? by mutableStateOf(null)
        private set

    var error: String? by mutableStateOf(null)
        private set

    // 「设置变了就重置对应输入框」这条语义留在这里：迁移前它由 remember(key) 承担，
    // 现在由记住上一次同步值承担，效果一致——来源没变时不覆盖用户正在输入的内容。
    private var boundarySource: Int? = null
    private var weekStartSource: Int? = null
    private var capacitySource: Int? = null

    fun syncDefaults(state: TodayScreenState) {
        if (boundarySource != state.planningDayBoundaryMinutes) {
            boundarySource = state.planningDayBoundaryMinutes
            boundary = state.planningDayBoundaryMinutes.toString()
        }
        if (weekStartSource != state.weekDayOptions.single { it.selected }.day) {
            weekStartSource = state.weekDayOptions.single { it.selected }.day
            weekStartDay = weekStartSource!!
        }
        val capacityValue = state.dailyCapacityMinutes
        if (capacitySource != capacityValue) {
            capacitySource = capacityValue
            capacity = capacityValue?.toString() ?: ""
        }
    }

    fun typeBoundary(value: String) {
        boundary = value.filter(Char::isDigit)
        error = null
    }

    fun selectWeekStartDay(day: Int) {
        weekStartDay = day
    }

    fun typeCapacity(value: String) {
        capacity = value
        error = null
    }

    fun typePlanTitle(value: String) {
        planTitle = value
        error = null
    }

    fun typeScheduleTitle(value: String) {
        scheduleTitle = value
        error = null
    }

    fun typeScheduleDate(value: String) {
        scheduleDate = value
        error = null
    }

    fun typeScheduleEstimate(value: String) {
        scheduleEstimate = value.filter(Char::isDigit)
    }

    fun typeScheduleStart(value: String) {
        scheduleStart = value.filter(Char::isDigit)
    }

    fun typeScheduleEnd(value: String) {
        scheduleEnd = value.filter(Char::isDigit)
    }

    fun typeScheduleNote(value: String) {
        scheduleNote = value
    }

    fun selectScheduleTarget(targetId: String?) {
        scheduleTargetId = targetId
    }

    /** 是否已经走到最后一步；这一步之后首次设置就结束了。 */
    val isLastStep: Boolean get() = step == LAST_STEP

    /** 跳过当前步骤：清掉报错并前进一步；已经是最后一步就返回 true，请组合根收尾。 */
    fun skipStep(): Boolean {
        error = null
        if (isLastStep) return true
        step += 1
        return false
    }

    /** 前进一步；走到最后一步之后返回 true，表示首次设置到此结束。 */
    fun advance(): Boolean {
        error = null
        step += 1
        return isLastStep
    }

    /**
     * 校验当前这一步并给出下一步。
     *
     * 返回 [OnboardingSave.None] 表示输入不合法：这时 [error] 已经带上原因，
     * 页面只显示提示、不发出任何 intent。
     */
    fun resolveSave(state: TodayScreenState): OnboardingSave {
        error = null
        return when (step) {
            0 -> {
                val value = boundary.toIntOrNull()
                if (value == null || value !in 0 until 1440) {
                    error = "规划日界线必须是 0–1439 分钟"
                    OnboardingSave.None
                } else {
                    OnboardingSave.PlanningSettings(value, weekStartDay, state.dailyCapacityMinutes)
                }
            }
            1 -> OnboardingSave.PlanningSettings(state.planningDayBoundaryMinutes, weekStartDay, state.dailyCapacityMinutes)
            2 -> {
                val input = capacity.trim()
                val value = input.toIntOrNull()
                if (input.isNotEmpty() && (value == null || value <= 0)) {
                    error = "每日容量必须是正整数，或留空"
                    OnboardingSave.None
                } else {
                    OnboardingSave.PlanningSettings(state.planningDayBoundaryMinutes, weekStartDay, value)
                }
            }
            3 -> if (planTitle.isBlank()) {
                error = "计划标题不能为空"
                OnboardingSave.None
            } else {
                OnboardingSave.Target(planTitle.trim())
            }
            else -> {
                val date = runCatching { LocalDate.parse(scheduleDate.trim()) }.getOrNull()
                if (scheduleTitle.isBlank()) {
                    error = "日程标题不能为空"
                    OnboardingSave.None
                } else if (date == null) {
                    error = "请输入有效的规划日"
                    OnboardingSave.None
                } else {
                    val start = scheduleStart.toIntOrNull()
                    val end = scheduleEnd.toIntOrNull()
                    val estimate = scheduleEstimate.toIntOrNull()
                    val invalidRange = start != null && end != null && (start !in 0..1439 || end !in 1..1440 || end <= start)
                    when {
                        (start == null) != (end == null) || invalidRange -> {
                            error = "时间段无效"
                            OnboardingSave.None
                        }
                        estimate != null && estimate <= 0 -> {
                            error = "预计分钟必须为正整数"
                            OnboardingSave.None
                        }
                        else -> OnboardingSave.Schedule(
                            OnboardingScheduleDraft(
                                planningDate = date.toString(),
                                title = scheduleTitle.trim(),
                                startMinute = start,
                                endMinute = end,
                                estimatedMinutes = estimate,
                                note = scheduleNote,
                                targetId = scheduleTargetId,
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val LAST_STEP = 4
    }
}

/** 「保存并继续」校验之后的下一步。 */
internal sealed interface OnboardingSave {
    /** 输入不合法，[OnboardingDraft.error] 已经带上原因。 */
    data object None : OnboardingSave

    data class PlanningSettings(
        val boundaryMinutes: Int,
        val weekStartDay: Int,
        val dailyCapacityMinutes: Int?,
    ) : OnboardingSave

    data class Target(val title: String) : OnboardingSave

    data class Schedule(val draft: OnboardingScheduleDraft) : OnboardingSave
}

/**
 * 在页面里创建首次设置的表单状态。组合根因此不需要直接构造这个页面状态类型，
 * 它的类型与状态迁移一起留在页面 module 内（ADR-0005 第 1 节）。
 */
@Composable
internal fun rememberOnboardingDraft(planningDate: String): OnboardingDraft =
    remember { OnboardingDraft(planningDate) }
