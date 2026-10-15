package com.attention.workspace.state

import com.attention.domain.GoalCadence

/**
 * 统计页的 intent 接口。页面只发出 intent，实际落地（写 Room、启动 SAF 文件选择器）
 * 由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，也拿不到仓储或系统时钟。
 *
 * 启动入口与备份数据集中搬到了设置页：统计页底部只保留一个「设置与数据」入口，
 * 用户要改设置或动数据时从那里进独立设置页。
 */
interface StatisticsActions {
    fun selectPreviousPeriod()

    fun selectNextPeriod()

    fun selectCadence(cadence: GoalCadence)

    fun settlePeriodSnapshots()

    fun cancelMigration(migrationId: String)

    fun awardMilestones()

    /** 打开独立设置页。它只是从统计页进入设置，顶级页面仍然是统计。 */
    fun openSettings()
}