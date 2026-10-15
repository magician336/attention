package com.attention.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attention.app.data.settings.createSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * #177 之后，未完成引导的第一次启动看到的是独立全屏引导而不是工作区。
 *
 * 这些宿主级用例要测的是引导之后的工作区，因此各自在启动 Activity 之前把引导状态置为已完成；
 * 引导本身的行为由 [WorkspaceOnboardingFlowIntegrationTest] 覆盖。
 *
 * 这里读回当前快照再改引导状态一个字段，因此用例自己铺的规划设置、启动入口等都不会被冲掉。
 */
internal object WorkspaceGuideSeed {
    fun markCompleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val store = context.createSettingsStore()
            store.setSnapshot(store.settings.first().copy(onboardingCompleted = true))
        }
    }
}
