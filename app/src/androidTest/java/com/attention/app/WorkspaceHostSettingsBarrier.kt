package com.attention.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attention.app.data.settings.SettingsSnapshot
import com.attention.app.data.settings.createSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    /**
     * 写入 [snapshot] 并等它真的成为持久化设置；被并发写回时重播播种。
     * 返回最终确认生效的那份快照。
     */
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

    private val context: Context get() = ApplicationProvider.getApplicationContext()
}
