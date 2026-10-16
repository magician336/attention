package com.attention.workspace.state

import com.attention.domain.StoredSettings

/** 首次设置中的一个可跳过步骤。 */
enum class OnboardingStepKey {
    PLANNING_DAY_BOUNDARY,
    WEEK_START_DAY,
    DAILY_CAPACITY,
    FIRST_TARGET,
    FIRST_SCHEDULE,
}

data class OnboardingPage(
    val key: OnboardingStepKey,
    val title: String,
    val body: String,
)

/** 工作台内的渐进首次设置状态。 */
data class OnboardingScreenState(
    val pages: List<OnboardingPage>,
    val planningDate: String = "",
    val initialSettings: StoredSettings = StoredSettings(),
) {
    val pageCount: Int get() = pages.size
}

fun onboardingScreenState(
    planningDate: String = "",
    settings: StoredSettings = StoredSettings(),
): OnboardingScreenState = OnboardingScreenState(
    pages = listOf(
        OnboardingPage(
            key = OnboardingStepKey.PLANNING_DAY_BOUNDARY,
            title = "规划日界线",
            body = "设置一天从几点开始计算，默认使用当前设置。可以跳过，之后在设置与数据中修改。",
        ),
        OnboardingPage(
            key = OnboardingStepKey.WEEK_START_DAY,
            title = "周起始日",
            body = "选择周统计从哪一天开始。可以跳过，之后在设置与数据中修改。",
        ),
        OnboardingPage(
            key = OnboardingStepKey.DAILY_CAPACITY,
            title = "每日容量",
            body = "设置每天计划投入的参考上限。容量只提供提醒，不会阻止保存日程。",
        ),
        OnboardingPage(
            key = OnboardingStepKey.FIRST_TARGET,
            title = "第一个计划",
            body = "输入一个最先要推进的计划；留空并跳过也可以直接开始工作。",
        ),
        OnboardingPage(
            key = OnboardingStepKey.FIRST_SCHEDULE,
            title = "第一条日程",
            body = "为当前规划日安排第一件事。只在确认保存后创建日程，预计投入不会自动算作实际投入。",
        ),
    ),
    planningDate = planningDate,
    initialSettings = settings,
)
