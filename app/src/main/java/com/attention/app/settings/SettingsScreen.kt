package com.attention.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.SettingsActions
import com.attention.workspace.state.SettingsScreenState

/**
 * 设置页。它只接收自己的 [SettingsScreenState] 与 [SettingsActions]：
 * 不读完整领域状态，不读 ViewModel，也不读系统时间——当前时间由组合根注入投影。
 * 页面本地状态（选中态、展开态）留在页面内，不上浮到组合根。
 */
@Composable
internal fun SettingsScreen(
    state: SettingsScreenState,
    actions: SettingsActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.spacing.large),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = tokens.typography.display)
        Text("启动入口", style = tokens.typography.title)
        Text("应用下次启动时打开的页面")
        state.options.forEach { option ->
            if (option.selected) {
                AttentionButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("launch-destination-${option.destination}"),
                ) { Text("${option.label}（当前）") }
            } else {
                AttentionOutlinedButton(
                    onClick = { actions.selectLaunchDestination(option.destination) },
                    modifier = Modifier.fillMaxWidth().testTag("launch-destination-${option.destination}"),
                ) { Text(option.label) }
            }
        }
    }
}
