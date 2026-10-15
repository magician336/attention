package com.attention.app.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 日程页自己的次级区块折叠状态。
 *
 * 重复日程仍然可用，但它不再是首屏的主路径：创建规则与「停止未来生成」都收在
 * [recurrenceVisible] 后面，需要用户先展开一次。它默认折叠，因此刚进页面的人第一眼看到的
 * 是规划日与容量摘要、时间轴、计时状态与添加日程，而不是一张重复日程表单。
 *
 * 它属于日程页 module 而不是组合根（ADR-0005 第 1 节）：组合根只调用
 * [rememberAgendaDisclosureState] 持有一个实例，不声明这个类型、也不改写它的字段。
 * 这个状态刻意不写入任何持久化——重新启动应用时回到默认折叠，符合「次级入口不占主路径」。
 */
internal class AgendaDisclosureState {

    /** 重复日程区块当前是否展开。 */
    private var recurrenceExpanded by mutableStateOf(false)

    /** 重复日程的创建表单与规则列表是否可见。 */
    val recurrenceVisible: Boolean get() = recurrenceExpanded

    /** 展开或收起重复日程。反复点击在两种状态之间来回。 */
    fun toggleRecurrence() {
        recurrenceExpanded = !recurrenceExpanded
    }
}

/**
 * 在组合根或页面内创建日程页的次级区块折叠状态。状态类型与它的迁移一起留在页面 module 内。
 */
@Composable
internal fun rememberAgendaDisclosureState(): AgendaDisclosureState = remember { AgendaDisclosureState() }
