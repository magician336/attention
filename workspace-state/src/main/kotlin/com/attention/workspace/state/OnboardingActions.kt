package com.attention.workspace.state

/**
 * 首次使用引导的 intent 接口。
 *
 * 引导只有一种落地：更新引导状态。完成与跳过走同一条意图，因为它们对持久化的影响完全相同，
 * 而且都**不写**目标、日程、设置与时间记录。
 *
 * 「现在是第几页」「还能不能继续翻页」是页面本地状态（ADR-0005 第 1 节：页面内部状态留在页面 module），
 * 所以这里不会出现页码参数：页面决定是继续翻页还是离开组合根。
 *
 * 实际落地（写 `SettingsSnapshot.onboardingCompleted`）由组合根 adapter 完成，
 * 页面因此拿不到 ViewModel，也拿不到仓储。
 */
interface OnboardingActions {
    /** 完成或跳过引导：只更新引导状态，之后不再自动出现。 */
    fun completeOnboarding()
}
