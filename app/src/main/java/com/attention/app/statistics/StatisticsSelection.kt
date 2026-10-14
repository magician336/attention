package com.attention.app.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.domain.GoalCadence
import com.attention.workspace.state.shiftStatisticsPeriod
import java.time.LocalDate

/**
 * 统计页自己的选择状态：当前选中的规划日与周期粒度。
 *
 * 它属于统计页 module 而不是组合根：ADR-0005 §24 要求选择状态留在页面一侧。
 * 组合根只调用 [rememberStatisticsSelection] 持有一个实例，用来喂投影；
 * 组合根不认识字段、也不解释「null 表示跟随当前规划日」这条语义——语义只在这里。
 */
internal class StatisticsSelection(
    selectedDate: LocalDate? = null,
    cadence: GoalCadence = GoalCadence.DAILY,
) {
    /** null 表示还没选过，投影会跟随组合根注入的当前规划日。 */
    var selectedDate: LocalDate? by mutableStateOf(selectedDate)
        private set

    var cadence: GoalCadence by mutableStateOf(cadence)
        private set

    fun selectCadence(cadence: GoalCadence) {
        this.cadence = cadence
    }

    /** 按当前粒度把 [currentDate]（投影已解析出的选中日）前后位移。 */
    fun shiftPeriod(amount: Long, currentDate: LocalDate) {
        selectedDate = shiftStatisticsPeriod(currentDate, cadence, amount)
    }
}

/**
 * 在组合根创建统计页的选择状态。组合根因此不需要直接构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberStatisticsSelection(): StatisticsSelection = remember { StatisticsSelection() }