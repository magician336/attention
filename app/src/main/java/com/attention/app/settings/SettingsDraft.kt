package com.attention.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.workspace.state.SettingsScreenState

/** 一次规划设置的保存结果。界线不合法时沿用当前值，容量不合法时不给出结果。 */
internal sealed interface PlanningSettingsSave {
    data class Valid(val boundaryMinutes: Int, val dailyCapacityMinutes: Int?) : PlanningSettingsSave
    data object Invalid : PlanningSettingsSave
}

/**
 * 设置页自己的表单状态：规划日界线与每日容量两个输入框，以及上一次的报错。
 *
 * 它属于设置页 module 而不是组合根：ADR-0005 第 1 节要求表单草稿由页面 module 拥有，
 * 组合根既不声明这个类型，也不改写它的字段。
 */
internal class SettingsDraft {

    /** 规划日界线输入框，初值来自设置。 */
    var boundary: String by mutableStateOf("")
        private set

    /** 每日容量输入框；未设置时为空。 */
    var capacity: String by mutableStateOf("")
        private set

    var error: String? by mutableStateOf(null)
        private set

    // 「设置变了就重置对应输入框」这条语义留在这里：来源没变时不覆盖用户正在输入的内容。
    private var boundarySource: Int? = null
    private var capacitySource: Int? = null

    fun syncDefaults(state: SettingsScreenState) {
        if (boundarySource != state.planningDayBoundaryMinutes) {
            boundarySource = state.planningDayBoundaryMinutes
            boundary = state.planningDayBoundaryMinutes.toString()
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

    fun typeCapacity(value: String) {
        capacity = value.filter(Char::isDigit)
        error = null
    }

    /**
     * 校验两个输入框并给出一次保存意图的参数。
     *
     * 界线不合法时沿用当前界线（与迁移前的兜底一致），容量留空表示清除，
     * 容量不是正整数时给出报错并且不保存。
     */
    fun resolveSave(state: SettingsScreenState): PlanningSettingsSave {
        val capacity = when {
            capacity.isBlank() -> null
            (capacity.toIntOrNull() ?: 0) <= 0 -> {
                error = "每日容量必须是正整数，或留空清除"
                return PlanningSettingsSave.Invalid
            }
            else -> capacity.toInt()
        }
        val boundaryMinutes = boundary.toIntOrNull()
            ?.takeIf { it in 0 until MINUTES_PER_DAY }
            ?: state.planningDayBoundaryMinutes
        return PlanningSettingsSave.Valid(boundaryMinutes, capacity)
    }

    private companion object {
        /** 一天有多少分钟：与 `:domain` 的 PlannerSettings 同一个边界，页面不另立规则。 */
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** 设置页在组合根之外自己持有的表单草稿；组合根不需要参与它的生命周期。 */
@Composable
internal fun rememberSettingsDraft(): SettingsDraft = remember { SettingsDraft() }
