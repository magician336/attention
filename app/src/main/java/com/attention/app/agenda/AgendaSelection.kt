package com.attention.app.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.attention.workspace.state.AgendaRangeScope
import java.time.LocalDate

/**
 * 日程页自己的选择状态：当前查看的日期范围。null 表示「所有日期」这个特殊视图，
 * 它不是日程条目可以使用的日期值。
 *
 * 它属于日程页 module 而不是组合根：ADR-0005 第 1 节要求选择状态留在页面一侧。
 * 组合根只调用 [rememberAgendaSelection] 持有一个实例来喂投影，不认识字段、
 * 也不解释「null 表示所有日期」这条语义——语义只在这里。
 *
 * ## 会话内保留，重启重置
 *
 * 选择状态区分两件事：
 *
 * - 用户**显式选过**的范围（含「所有日期」）：在应用会话内保留。切到目标树或统计再回来，
 *   组合根持有的仍是同一个实例，因此日程页仍停在这个范围上。
 * - 用户**还没显式选过**：跟随组合根每次注入的当前规划日，见 [selectedDate]。
 *
 * 这条状态刻意不写入任何持久化：Room 与 DataStore 里都没有它的位置，
 * 重新启动应用时组合根新建一个实例，用户显式选过的范围因此不会跨重启残留，
 * 日程页重新以当前规划日为默认。这样「跟着当前规划日」也不会被首次组合时
 * 尚未加载完成的设置冻住——持久化设置到达后，默认跟着正确的规划日走。
 *
 * ## 范围高亮跨规划日界线
 *
 * [chosen] 存的是用户在点击那一刻的规划日，而当前规划日每次重组都会重读。两者一旦分家
 * （应用跨过规划日界线），只拿它们比较就会得出「三个范围都没选中」——用户明明点了「今天」，
 * 页面却停在一个没有高亮的日子上。因此这里额外记住用户当时点的是哪个范围
 * （[chosenScope]）：范围是用户的选择，规划日只是时钟，两者漂移时以选择为准。
 */
internal class AgendaSelection(
    requestedDate: LocalDate? = null,
) {
    /**
     * 用户显式选中的规划日。null 表示「所有日期」这个特殊视图；
     * [explicitlyChosen] 为 false 时这个字段整体不参与解释——那时页面跟随当前规划日。
     */
    private var chosen: LocalDate? by mutableStateOf(requestedDate)
        private set

    /**
     * 用户当时点中的日期范围。null 表示那是一个范围之外的**具体日期**（下拉框选的任意一天），
     * 它没有对应的顶部 chip，因此保持按当前规划日重新判定，与既有行为一致。
     */
    private var chosenScope: AgendaRangeScope? by mutableStateOf(null)
        private set

    /** 用户是否已经在这个会话里选过范围；false 时 [selectedDate] 跟随当前规划日。 */
    private var explicitlyChosen: Boolean by mutableStateOf(requestedDate != null)
        private set

    /**
     * 投影与页面要用的选中范围：null 表示「所有日期」。
     *
     * 用户显式选过就用选中的范围，否则用 [planningDay]——也就是组合根注入的当前规划日。
     * 规划日是什么由领域规则给出，页面不自己算。
     */
    fun selectedDate(planningDay: LocalDate): LocalDate? =
        if (explicitlyChosen) chosen else planningDay

    /**
     * 顶部三个范围里当前被选中的那一个，null 表示没有一个被选中。
     *
     * 还没显式选过时跟随当前规划日，也就是落在「今天」上；显式选过「今天」或「明天」时，
     * 这个范围一直保持被选中，跨过规划日界线也不回退。
     */
    fun selectedRangeScope(planningDay: LocalDate): AgendaRangeScope? = when {
        !explicitlyChosen -> AgendaRangeScope.TODAY
        chosenScope != null -> chosenScope
        else -> rangeScopeOf(chosen, planningDay)
    }

    /** 选中一个规划日；null 表示所有日期特殊视图。[planningDay] 是点击那一刻的当前规划日。 */
    fun selectDate(date: LocalDate?, planningDay: LocalDate) {
        chosen = date
        chosenScope = rangeScopeOf(date, planningDay)
        explicitlyChosen = true
    }

    /** 选中「所有日期」这个特殊视图。它与时钟无关，因此不需要当前规划日。 */
    fun selectAllDates() {
        chosen = null
        chosenScope = AgendaRangeScope.ALL_DATES
        explicitlyChosen = true
    }

    /**
     * 丢掉本次会话里的显式选择，重新跟随组合根注入的当前规划日。
     *
     * 「跟随当前规划日」不是「选中某一天」，也不是「所有日期」，所以它需要自己一个入口；
     * 引导结束时用它把用户送回当前规划日的日程。
     */
    fun followPlanningDay() {
        chosen = null
        chosenScope = null
        explicitlyChosen = false
    }

    /** 以投影已解析出的当前选中日为基准前后位移规划日。 */
    fun shiftDays(amount: Long, currentDate: LocalDate, planningDay: LocalDate) {
        selectDate(currentDate.plusDays(amount), planningDay)
    }
}

/** 点击那一刻的日期落在哪个范围；今天与明天之外的具体日期没有对应的范围 chip。 */
private fun rangeScopeOf(date: LocalDate?, planningDay: LocalDate): AgendaRangeScope? = when (date) {
    null -> AgendaRangeScope.ALL_DATES
    planningDay -> AgendaRangeScope.TODAY
    else -> if (date == planningDay.plusDays(1)) AgendaRangeScope.TOMORROW else null
}

/**
 * 在组合根创建日程页的选择状态。组合根因此不需要直接构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 *
 * [remember] 让这一个实例挂在组合根上、活过页面切换：会话内保留由此实现。
 * 它不跨进程重启，因此重新启动就是一次新会话，重新以当前规划日为默认。
 *
 * [requestedDate] 是外部日程请求带来的规划日（提醒深链）：它算一次显式选择，
 * 优先于「当前规划日」这个默认值。深链的整体优先级由 #174 决定。
 */
@Composable
internal fun rememberAgendaSelection(requestedDate: LocalDate? = null): AgendaSelection =
    remember { AgendaSelection(requestedDate) }
