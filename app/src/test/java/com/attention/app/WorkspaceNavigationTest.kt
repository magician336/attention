package com.attention.app

import com.attention.domain.LaunchDestination
import com.attention.domain.asAgendaEntry
import com.attention.domain.resolveInitialLaunchDestination
import com.attention.domain.resolveLaunchDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceNavigationTest {
    @Test
    fun fixed_launch_destinations_are_opened_directly() {
        assertEquals(
            LaunchDestination.TARGETS,
            resolveLaunchDestination(LaunchDestination.TARGETS, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.ALL_DATES,
            resolveLaunchDestination(LaunchDestination.ALL_DATES, LaunchDestination.TODAY),
        )
    }

    @Test
    fun last_opened_restores_only_an_actual_page_and_self_reference_falls_back() {
        assertEquals(
            LaunchDestination.STATISTICS,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.STATISTICS),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            resolveLaunchDestination(LaunchDestination.LAST_OPENED, LaunchDestination.LAST_OPENED),
        )
    }

    @Test
    fun the_agenda_entry_lands_on_the_merged_agenda_page() {
        // 底部导航收敛为三项之后，「日程」就是它的真实入口：旧入口值仍然等价于日程
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(false, LaunchDestination.AGENDA, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            LaunchDestination.TODAY.asAgendaEntry(),
        )
        assertEquals(
            LaunchDestination.AGENDA,
            LaunchDestination.ALL_DATES.asAgendaEntry(),
        )
    }

    @Test
    fun external_schedule_request_takes_precedence_over_saved_launch_page() {
        // 外部日程请求直接打开日程并定位到指定规划日与条目，不被保存的启动入口拦截
        assertEquals(
            LaunchDestination.AGENDA,
            resolveInitialLaunchDestination(true, LaunchDestination.STATISTICS, LaunchDestination.TARGETS),
        )
        assertEquals(
            LaunchDestination.STATISTICS,
            resolveInitialLaunchDestination(false, LaunchDestination.STATISTICS, LaunchDestination.TARGETS),
        )
    }

    @Test
    fun last_opened_is_not_written_until_persisted_settings_are_loaded() {
        assertEquals(false, writeBack(settingsLoaded = false))
        assertEquals(false, writeBack(settingsOpen = true))
        assertEquals(true, writeBack())
    }

    @Test
    fun last_opened_is_not_written_while_the_domain_state_is_still_the_empty_default() {
        // 领域状态加载之前，[state] 是一份空默认值：据此解析出来的启动落点只是暂定值，
        // 写回去会覆盖用户上次真正停留的页面，因此必须等状态真的读上来。
        assertEquals(false, writeBack(stateLoaded = false))
        // 设置页不是顶级工作区：这条限制与上一条各自都能单独挡住写回
        assertEquals(false, writeBack(settingsLoaded = false))
        assertEquals(false, writeBack(settingsOpen = true))
    }

    @Test
    fun last_opened_is_not_written_while_the_launch_destination_is_still_provisional() {
        // 状态已加载并不等于落点已纠正：启动落点的初值同样是由那份空默认值解析出来的暂定值，
        // 状态纠正与写回发生在同一次重组里，写回那条 effect 捕获到的正是纠正之前的值。
        // 因此「启动入口=上次打开、TARGETS 历史」的用户会在启动时被永久改成日程（#179 实跑失败）。
        assertEquals(false, writeBack(destinationSettled = false))
        assertEquals(false, writeBack(settingsLoaded = false, destinationSettled = false))
        assertEquals(false, writeBack(settingsOpen = true, destinationSettled = false))
        // 落点确定之后才写回
        assertEquals(true, writeBack())
    }

    @Test
    fun an_external_schedule_request_never_rewrites_last_opened() {
        // 外部请求只负责把用户送到指定规划日与条目，不代表用户上次停留的页面就是日程。
        // 若把它当成「落点已确定」，启动入口为「上次打开」的用户会在一次提醒之后，
        // 此后每次启动都被拉到日程页——「上次打开」被一次外部请求劫持（#185）。
        assertEquals(false, writeBack(fromExternalRequest = true))
        // 外部送达独自一条就够：即使落点已经「确定」，也不写回
        assertEquals(false, writeBack(fromExternalRequest = true, destinationSettled = true))
        assertEquals(false, writeBack(fromExternalRequest = true, settingsLoaded = false))
        // 外部请求打开之后用户亲手切换了主工作区：那次切换是用户自己的导航，照常记录。
        assertEquals(true, writeBack())
    }

    /** 事实全为真（可以写回）的基线，按需逐条翻转其中一条。 */
    private fun writeBack(
        settingsLoaded: Boolean = true,
        settingsOpen: Boolean = false,
        stateLoaded: Boolean = true,
        destinationSettled: Boolean = true,
        fromExternalRequest: Boolean = false,
    ): Boolean = shouldPersistLastOpened(
        LastOpenedWriteBackFacts(
            settingsLoaded = settingsLoaded,
            settingsOpen = settingsOpen,
            stateLoaded = stateLoaded,
            destinationSettled = destinationSettled,
            fromExternalRequest = fromExternalRequest,
        ),
    )
}
