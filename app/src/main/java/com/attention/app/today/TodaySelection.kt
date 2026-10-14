package com.attention.app.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.workspace.state.TodayScreenState
import com.attention.workspace.state.TodayTimeRecord

/**
 * 今天工作台自己的选择与表单状态：快速记录的分钟数与目标归属、两个规划日设置输入框、
 * 时间记录的编辑草稿，以及删除确认的目标。
 *
 * 它属于今天工作台 module 而不是组合根：ADR-0005 第 1 节要求选择状态留在页面一侧。
 * 组合根不认识这些字段，也不解释「谁正在被编辑」这条语义——语义只在这里。
 */
internal class TodaySelection {
    /** 快速记录表单的分钟数，默认 15，与迁移前的默认值一致。 */
    var minutes: String by mutableStateOf("15")
        private set

    /** 快速记录的目标归属；null 表示未归属活动。 */
    var manualTargetId: String? by mutableStateOf(null)
        private set

    var boundaryMinutes: String by mutableStateOf("")
        private set

    var dailyCapacityMinutes: String by mutableStateOf("")
        private set

    var dailyCapacityError: String? by mutableStateOf(null)
        private set

    /** 正在编辑的时间记录 id；null 表示没有打开编辑表单。 */
    var editingEntryId: String? by mutableStateOf(null)
        private set

    var editMinutes: String by mutableStateOf("")
        private set

    var editNote: String by mutableStateOf("")
        private set

    var editTargetId: String? by mutableStateOf(null)
        private set

    /** 待确认删除的时间记录 id；null 表示没有打开删除确认。 */
    var pendingDeleteEntryId: String? by mutableStateOf(null)
        private set

    // 记住上一次同步过的设置值，于是「设置变了就重置输入框、用户正在输入时不覆盖」
    // 这条语义留在这里，而不是留在组合根或页面里重复判断。
    private var boundarySource: Int? = null
    private var capacitySource: Int? = null

    /**
     * 把两个设置输入框的默认值同步成投影给出的值。
     * 只有来源值真的变了才重写，因此用户正在输入的内容不会被一次无关重算抹掉。
     */
    fun syncDefaults(state: TodayScreenState) {
        if (boundarySource != state.planningDayBoundaryMinutes) {
            boundarySource = state.planningDayBoundaryMinutes
            boundaryMinutes = state.planningDayBoundaryMinutes.toString()
        }
        val capacity = state.dailyCapacityMinutes
        if (capacitySource != capacity) {
            capacitySource = capacity
            dailyCapacityMinutes = capacity?.toString() ?: ""
        }
    }

    fun typeMinutes(value: String) {
        minutes = value.filter(Char::isDigit)
    }

    fun selectManualTarget(targetId: String?) {
        manualTargetId = targetId
    }

    fun typeBoundaryMinutes(value: String) {
        boundaryMinutes = value.filter(Char::isDigit)
    }

    /**
     * 改容量输入时清掉上一次的报错，避免旧提示停留在已经改过的输入上。
     *
     * 这里刻意不过滤字符：负数与字母要原样留在输入框里，保存时才给出
     * 「必须是正整数」的提示。把它们悄悄滤掉会让用户以为自己填对了。
     */
    fun typeDailyCapacityMinutes(value: String) {
        dailyCapacityMinutes = value
        dailyCapacityError = null
    }

    /** 打开一条记录的编辑表单，草稿取自这条记录本身。 */
    fun startEditing(record: TodayTimeRecord) {
        editingEntryId = record.entryId
        editMinutes = record.durationMinutes.toString()
        editNote = record.note
        editTargetId = record.targetId
    }

    fun stopEditing() {
        editingEntryId = null
    }

    fun typeEditMinutes(value: String) {
        editMinutes = value.filter(Char::isDigit)
    }

    fun typeEditNote(value: String) {
        editNote = value
    }

    fun selectEditTarget(targetId: String?) {
        editTargetId = targetId
    }

    fun requestDelete(entryId: String) {
        pendingDeleteEntryId = entryId
    }

    fun dismissDelete() {
        pendingDeleteEntryId = null
    }

    /**
     * 校验容量输入并给出下一步。
     * 容量必须是正整数，或者留空清除；其它输入只提示错误、不发出保存。
     */
    fun resolveCapacitySave(): DailyPlanningSave {
        val boundary = boundaryMinutes.toIntOrNull()?.takeIf { it in 0 until 1440 }
        val input = dailyCapacityMinutes.trim()
        val capacity = input.toIntOrNull()?.takeIf { it > 0 }
        return if (input.isNotEmpty() && capacity == null) {
            dailyCapacityError = "每日容量必须是正整数，或留空清除"
            DailyPlanningSave.Invalid
        } else {
            dailyCapacityError = null
            DailyPlanningSave.Valid(boundary, capacity)
        }
    }
}

/** 保存规划日设置的校验结果。 */
internal sealed interface DailyPlanningSave {
    /** 输入不合法，[TodaySelection.dailyCapacityError] 已经带上原因。 */
    data object Invalid : DailyPlanningSave

    /** 输入合法，可以发出保存 intent。[boundary] 为 null 表示沿用当前界线。 */
    data class Valid(val boundary: Int?, val capacity: Int?) : DailyPlanningSave
}

/**
 * 在页面里创建今天工作台的选择状态。组合根因此不需要直接构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberTodaySelection(): TodaySelection = remember { TodaySelection() }
