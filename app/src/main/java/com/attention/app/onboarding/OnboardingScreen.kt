package com.attention.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.OnboardingActions
import com.attention.workspace.state.OnboardingScreenState

/**
 * 首次使用引导：独立、全屏、可跳过的说明式 UI。
 *
 * 固定三个说明页：日程、目标树、统计，顺序稳定。它只解释各入口的职责，
 * **不要求创建计划、日程或设置**，因此页面上没有任何输入框，也没有任何会写业务数据的动作。
 *
 * 它只接收自己的 [OnboardingScreenState] 与 [OnboardingActions]：不读完整领域状态，不读 ViewModel，
 * 也不读系统时间。当前看到第几页是页面本地状态（[OnboardingSelection]），不上浮到组合根。
 *
 * 三种离开方式：跳过当前页、跳过剩余步骤、开始使用。后两者与最后一页的「跳过当前页」都落到
 * [OnboardingActions.completeOnboarding]，它只更新引导状态。
 */
@Composable
internal fun OnboardingScreen(
    state: OnboardingScreenState,
    actions: OnboardingActions,
) {
    val tokens = attentionTokens()
    val selection = rememberOnboardingSelection()
    // 说明页数量由投影给出；内容恒为三个入口，因此这里按下标取当前页即可。
    val index = selection.pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    val page = state.pages.getOrNull(index)
    Surface(Modifier.fillMaxSize().testTag("onboarding-screen")) {
        if (page == null) {
            Text("引导内容缺失", modifier = Modifier.padding(tokens.spacing.large))
            return@Surface
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("首次使用引导", style = tokens.typography.display, modifier = Modifier.testTag("onboarding-title"))
            Text(
                "第 ${index + 1} 页，共 ${state.pageCount} 页",
                modifier = Modifier.testTag("onboarding-indicator"),
            )
            Text(page.title, style = tokens.typography.title, modifier = Modifier.testTag("onboarding-page-title"))
            Text(page.body, modifier = Modifier.testTag("onboarding-page-body"))
            Text(
                "这三个入口随时可以跳过：看完再创建数据也来得及。",
                style = tokens.typography.body,
            )
            AttentionOutlinedButton(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-skip-page"),
                onClick = { if (selection.skipCurrentPage(state.pageCount)) actions.completeOnboarding() },
            ) { Text("跳过当前页") }
            AttentionTextButton(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-skip-remaining"),
                onClick = actions::completeOnboarding,
            ) { Text("跳过剩余步骤") }
            AttentionButton(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-start"),
                onClick = actions::completeOnboarding,
            ) { Text("开始使用") }
        }
    }
}
