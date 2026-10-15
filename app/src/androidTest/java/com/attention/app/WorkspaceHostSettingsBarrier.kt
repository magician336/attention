package com.attention.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 宿主级设置屏障：同一安装下的设备套件共享同一个 DataStore 与同一份 Room 数据。
 *
 * 别的测试类如果用 `createAndroidComposeRule<MainActivity>` 或 `ActivityScenario`，
 * 它们的 Activity 实例在收尾时会把「上次打开」写回共享 DataStore（本轮导航重组之后，
 * 底部只有三项，因此写回的一定是日程或它当时停留的那一项）。于是本类刚播种的
 * `TARGETS` 可能在启动落点被读取之前就被覆盖掉，表现为间歇性超时，而不是稳定失败。
 *
 * 断言本身没有问题，产品侧的竞态（状态未加载就写回）已由 `AttentionViewModel.stateLoaded`
 * 修掉；这里补的是宿主级的重新播种屏障：写完之后必须**读回确认**播种真的生效，
 * 若被别的 Activity 写回就再写一次。屏障只影响测试之间的隔离，不放宽任何断言。
 */
internal object WorkspaceHostSettingsBarrier {
    private const val ATTEMPTS = 5
    private const val SETTLE_MILLIS = 200L
    private const val QUIESCE_ROUNDS = 6

    /**
     * 等到共享 DataStore 停止变化再返回：连续两次读到同样的值，中间间隔一个结算窗口。
     *
     * 这是「关闭 Activity 之后」的收尾屏障。`ActivityScenario.close()` 之后，被关闭的
     * Activity 组合根里那条「上次打开」写回的协程仍可能在收尾途中落盘；下一次用例如果
     * 在它落盘之前播种，播种值就会被覆盖。因此每个用例收尾时要先等写入停稳，
     * 下一次播种才不会被抢走（#179 设备实跑仍间歇失败）。
     */
    fun quiesce(): SettingsSnapshot {
        val store = context.createSettingsStore()
        var previous = runBlocking { store.settings.first() }
        repeat(QUIESCE_ROUNDS) {
            Thread.sleep(SETTLE_MILLIS)
            val current = runBlocking { store.settings.first() }
            if (current == previous) return current
            previous = current
        }
        return previous
    }

    /** 写入 [snapshot] 并等它真的成为持久化设置；被并发写回时重播播种。 */
    fun quiesceAndSeed(snapshot: SettingsSnapshot): SettingsSnapshot {
        val store = context.createSettingsStore()
        var pending = snapshot
        repeat(ATTEMPTS) {
            val observed = runBlocking {
                store.setSnapshot(pending)
                store.settings.first()
            }
            // 读回确认：读到别的值说明有别的 Activity 刚写回，重播播种。
            if (observed == pending) return pending
            Thread.sleep(SETTLE_MILLIS)
        }
        return runBlocking { store.settings.first() }
    }

    /** 等到持久化设置真的变成期望的样子（宿主写回是异步的）。 */
    fun awaitSettings(predicate: (SettingsSnapshot) -> Boolean): SettingsSnapshot {
        var current: SettingsSnapshot = read()
        repeat(ATTEMPTS) {
            if (predicate(current)) return current
            Thread.sleep(SETTLE_MILLIS)
            current = read()
        }
        return current
    }

    /** 宿主级用例读设置的统一入口，避免每个类各写一份 runBlocking。 */
    fun read(): SettingsSnapshot = runBlocking { context.createSettingsStore().settings.first() }

    /**
     * 宿主 Activity 的收尾规则：等共享 DataStore 的写入停稳再放行下一条用例。
     *
     * `createAndroidComposeRule<MainActivity>` 与 `ActivityScenario` 都会在用例结束时销毁
     * Activity，但被销毁的那个组合根里「上次打开」的写回协程仍可能在收尾途中落盘。
     * 下一条用例若在它落盘之前播种，播种值会被静默覆盖，于是「启动入口=上次打开」的用例
     * 会间歇性停在默认页面。规则只做隔离，不放宽任何断言。
     */
    val quiesceAfterActivity: TestRule = object : TestWatcher() {
        override fun finished(description: Description) {
            quiesce()
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
}
