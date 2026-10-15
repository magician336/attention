package com.attention.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 返回键处理的决策规则：可关闭的层优先，没有可关闭的层时把返回键交回系统。
 *
 * 决策点是一个纯函数，因此每种情形都能在这里逐条钉住，不依赖设备与窗口顺序。
 */
class WorkspaceBackNavigationTest {
    @Test
    fun back_closes_the_open_settings_page_before_the_system_sees_it() {
        // 设置页挂在主内容里而不是对话框窗口里，因此只有组合根能把它接住：
        // 迁移前没有 BackHandler，返回键会直接把整个应用退掉。
        assertEquals(
            WorkspaceBackTarget.CLOSE_SETTINGS_PAGE,
            resolveBackTarget(settingsOpen = true),
        )
    }

    @Test
    fun back_is_left_to_the_system_when_no_layer_is_open() {
        // 没有可关闭的层时组合根不接管返回键，退出行为与 #175 之前完全一致。
        assertEquals(
            WorkspaceBackTarget.SYSTEM,
            resolveBackTarget(settingsOpen = false),
        )
    }
}
