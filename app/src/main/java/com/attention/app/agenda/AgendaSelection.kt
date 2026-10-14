package com.attention.app.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * 所有日期页自己的选择状态：当前查看的规划日。null 表示「所有日期」这个特殊视图，
 * 它不是日程条目可以使用的日期值。
 *
 * 它属于所有日期页 module 而不是组合根：ADR-0005 第 1 节要求选择状态留在页面一侧。
 * 组合根只调用 [rememberAgendaSelection] 持有一个实例来喂投影，不认识字段、
 * 也不解释「null 表示所有日期」这条语义——语义只在这里。
 */
internal class AgendaSelection(
    selectedDate: LocalDate? = null,
) {
    /** null 表示所有日期特殊视图；投影会按规划日分组展示全部条目。 */
    var selectedDate: LocalDate? by mutableStateOf(selectedDate)
        private set

    fun selectDate(date: LocalDate?) {
        selectedDate = date
    }

    /** 以投影已解析出的当前选中日为基准前后位移规划日。 */
    fun shiftDays(amount: Long, currentDate: LocalDate) {
        selectedDate = currentDate.plusDays(amount)
    }
}

/**
 * 在组合根创建所有日期页的选择状态。组合根因此不需要直接构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberAgendaSelection(initialDate: LocalDate? = null): AgendaSelection =
    remember { AgendaSelection(initialDate) }