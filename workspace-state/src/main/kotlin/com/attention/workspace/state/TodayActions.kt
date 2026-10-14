package com.attention.workspace.state

/**
 * 今天工作台的 intent 接口。页面只发出 intent，实际落地（写 Room / DataStore、
 * 展示可撤销提示）由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，
 * 也拿不到仓储。
 *
 * 计时相关的 intent 不在这里：计时器、前台服务与通知权限属于票 147 的迁移范围，
 * 今天工作台目前仍通过组合根侧的旧路径触发计时。
 */
interface TodayActions {
    /**
     * 记一笔实际投入。[targetId] 为 null 表示记为未归属活动。
     * [planningDate] 是页面正在显示的那一个规划日，记录因此落在用户看到的规划日上。
     */
    fun recordTime(planningDate: String, minutes: Int, targetId: String?)

    /**
     * 编辑一条实际投入，只调整时长、目标归属与备注。
     * 记录的规划日与来源由领域层保持不变，因此这里不接收它们。
     */
    fun editTime(entryId: String, minutes: Int, targetId: String?, note: String)

    /** 删除一条实际投入，并给出一次撤销机会。撤销的提示由组合根展示。 */
    fun deleteTime(entryId: String)

    /** 保存规划日界线与每日容量。[dailyCapacityMinutes] 为 null 表示清除容量上限。 */
    fun saveDailyPlanningSettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?)

    /** 选择周起始日。 */
    fun selectWeekStartDay(day: Int)
}
