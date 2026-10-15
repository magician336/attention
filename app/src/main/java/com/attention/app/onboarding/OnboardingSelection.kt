package com.attention.app.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 引导页自己的页面本地状态：当前看到第几个说明页。
 *
 * 它属于引导页 module 而不是组合根（ADR-0005 第 1 节：页面内部状态留在页面 module）。
 * 组合根只调用 [rememberOnboardingSelection] 持有一个实例，不认识字段，也不解释「第几页」这条语义。
 *
 * 这条状态刻意不写入任何持久化：引导在每次打开时都从第一个说明页开始，
 * 翻到哪里因此不会跨进程残留。
 */
internal class OnboardingSelection {
    /** 当前说明页的下标。 */
    var pageIndex by mutableStateOf(0)
        private set

    /**
     * 「跳过当前页」的前进语义：还有下一页就翻到下一页并返回 false；
     * 已经是最后一页就返回 true——此时没有下一页可翻，这一页就是引导的终点。
     *
     * [pageCount] 由投影给出，页面因此不需要自己知道说明页有几页。
     */
    fun skipCurrentPage(pageCount: Int): Boolean =
        if (pageIndex >= pageCount - 1) true else {
            pageIndex += 1
            false
        }
}

/**
 * 在组合根创建引导页的本地状态。组合根因此不需要构造页面状态类型，
 * 页面的状态类型与它的状态迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberOnboardingSelection(): OnboardingSelection = remember { OnboardingSelection() }
