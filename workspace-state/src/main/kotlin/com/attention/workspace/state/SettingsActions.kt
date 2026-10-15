package com.attention.workspace.state

import com.attention.domain.LaunchDestination

/**
 * 设置页的 intent 接口。页面只发出 intent，实际落地（写 DataStore / Room、启动 SAF 文件选择器、
 * 申请通知权限）由组合根 adapter 完成；页面因此拿不到 `AttentionViewModel`，也拿不到仓储。
 *
 * 设置页是启动入口、规划日界线、周起始日、每日容量、通知与备份数据的唯一编辑入口，
 * 因此每一种可改设置在这里都有对应的动作。
 */
interface SettingsActions {
    fun selectLaunchDestination(destination: LaunchDestination)

    /**
     * 保存规划日界线（0–1439 分钟）与每日容量（分钟，null 表示不设上限）。
     *
     * 是否合法由 `:domain` 的 `PlannerSettings` 判定，页面在发出这条意图之前先问一次同一个判定，
     * 只为了把被拒绝的原因显示出来；真正落库的那一次仍由领域与 `SettingsStore` 兜底校验。
     */
    fun savePlanningDaySettings(boundaryMinutes: Int, dailyCapacityMinutes: Int?)

    /** 选择每周起始日，1 是周一，7 是周日。 */
    fun selectWeekStartDay(day: Int)

    /** 申请通知权限。是否已开启是平台事实，由组合根查好后注入页面。 */
    fun requestNotificationPermission()

    fun previewJsonBackup()

    fun importJson()

    fun exportTimeEntriesCsv()

    fun previewCsv()

    fun exportSchedulesCsv()

    fun exportCsvBundle()

    /** 恢复最近一次导入前的备份，需要用户显式确认；确认对话框由组合根打开。 */
    fun restoreLastImportBackup()

    /** 重新打开首次使用引导。引导是说明式的，重看它同样不写任何业务数据。 */
    fun openOnboarding()

    /** 离开设置页，回到统计页。设置页不是顶级工作区，因此它不写入「上次打开」。 */
    fun close()
}
