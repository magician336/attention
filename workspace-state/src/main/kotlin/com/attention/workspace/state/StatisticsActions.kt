package com.attention.workspace.state

import com.attention.domain.GoalCadence
import com.attention.domain.LaunchDestination

/**
 * 统计页的 intent 接口。页面只发出 intent，实际落地（写 Room、启动 SAF 文件选择器）
 * 由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，也拿不到仓储或系统时钟。
 */
interface StatisticsActions {
    fun selectPreviousPeriod()

    fun selectNextPeriod()

    fun selectCadence(cadence: GoalCadence)

    fun settlePeriodSnapshots()

    fun cancelMigration(migrationId: String)

    fun awardMilestones()

    fun selectLaunchDestination(destination: LaunchDestination)

    fun restoreLastImportBackup()

    fun previewJsonBackup()

    fun importJson()

    fun exportTimeEntriesCsv()

    fun previewCsv()

    fun exportSchedulesCsv()

    fun exportCsvBundle()
}