package com.attention.workspace.state

/**
 * 首次使用引导的一个说明页：它只解释一个入口的职责。
 *
 * 文案是投影给出的，页面只负责渲染，不在页面里散落说明文字。
 */
data class OnboardingPage(
    val title: String,
    val body: String,
)

/**
 * 首次使用引导专有的不可变状态。
 *
 * 引导是说明式的：它固定包含日程、目标树、统计三个说明页，顺序稳定，只讲各入口的职责，
 * 不要求创建计划、日程或设置。因此这个状态**不携带** [com.attention.domain.AttentionState]——
 * 引导一个业务数据都不写，也读不出目标、日程与时间记录的影子。
 */
data class OnboardingScreenState(
    val pages: List<OnboardingPage>,
) {
    /** 说明页总数，页面用它渲染「第 N 页，共 M 页」。 */
    val pageCount: Int get() = pages.size
}

/**
 * 从固定文案投影出引导状态。
 *
 * 它没有参数：这正是本票的核心约束——引导不依赖任何领域状态，因此完成或跳过它
 * 都不会写目标、日程、设置与时间记录。
 */
fun onboardingScreenState(): OnboardingScreenState = OnboardingScreenState(
    pages = listOf(
        OnboardingPage(
            title = "日程",
            body = "日程按规划日组织你今天要做的事。每条日程有标题、起止时间与预计分钟，" +
                "可以关联目标树里的目标；预计时长和完成标记只是安排，实际投入由计时或手动补记产生。",
        ),
        OnboardingPage(
            title = "目标树",
            body = "目标树把长期目标拆成层级：父目标与子目标各自有阶段，阶段按每日目标或周期累积实际投入，" +
                "并显示距离阶段目标还差多少。",
        ),
        OnboardingPage(
            title = "统计",
            body = "统计按规划日与周期汇总投入：每日容量、目标进度、等级经验与里程碑都在这里，" +
                "设置与数据的入口也在统计页底部。",
        ),
    ),
)
