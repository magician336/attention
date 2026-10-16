package com.attention.workspace.state

/** 首次设置页面的 intent seam；跳过步骤不调用这些写入动作。 */
interface OnboardingActions {
    fun savePlanningDaySettings(boundaryMinutes: Int, weekStartDay: Int, dailyCapacityMinutes: Int?)

    fun selectWeekStartDay(day: Int)

    fun addFirstTarget(title: String)

    fun addFirstSchedule(draft: AgendaScheduleDraft)

    fun completeOnboarding()
}
