package com.attention.workspace.state

import com.attention.domain.LaunchDestination

/**
 * 设置页的 intent 接口。页面只发出 intent，实际落地（写 DataStore / Room）由组合根 adapter 完成；
 * 页面因此拿不到 `AttentionViewModel`，也拿不到仓储。
 */
interface SettingsActions {
    fun selectLaunchDestination(destination: LaunchDestination)
}
