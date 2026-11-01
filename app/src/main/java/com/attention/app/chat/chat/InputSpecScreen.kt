package com.attention.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FuguangInputSpecScreen(
    state: FuguangChatUiState = FuguangChatPreviewData.mainChat,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.surfaceMuted).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("输入区 & 快捷建议", style = type.display, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Text("已定方案 A · 对话与小助手统一使用", style = type.body, color = colors.textTertiary)
        }
        item {
            InputOptionCard(
                badge = "已采用",
                badgeColor = colors.primary,
                title = "建议条 + 单行输入",
                highlighted = true,
            ) {
                FuguangQuickSuggestions(state.suggestions, actions::onSuggestionClick)
                Spacer(Modifier.height(10.dp))
                FuguangChatInputBar(
                    value = state.input,
                    onValueChange = actions::onChatInputChange,
                    onSend = actions::onSendChat,
                    agentLabel = "主 Agent",
                )
                Spacer(Modifier.height(10.dp))
                Text("最省高度。建议条跟着上下文变：有飞书通知就出现「飞书 2 条」，晚上就出现「调整今晚」——用户不必想「我能说什么」。", style = type.caption, color = colors.textTertiary)
            }
        }
        item {
            InputOptionCard(
                badge = "方案 B",
                badgeColor = colors.rose,
                title = "双行 · 输入 + 功能坞",
            ) {
                FuguangChatInputBar(
                    value = "",
                    onValueChange = {},
                    onSend = {},
                    agentLabel = "主 Agent",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("日程", "任务", "语音", "附件").forEach { label ->
                        Surface(
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceMuted,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = type.caption, color = colors.textSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("适合小助手详情页：功能坞把「记一笔进度」做成一次点击，不用打字。代价是多占 28px 高度。", style = type.caption, color = colors.textTertiary)
            }
        }
        item {
            InputOptionCard(
                badge = "方案 C",
                badgeColor = colors.rose,
                title = "语音优先 · 一句话汇报",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("今天做完了", "听力 78%", "明天提前").forEach { label ->
                        Surface(shape = CircleShape, color = colors.surfaceMuted, border = BorderStroke(1.dp, colors.border)) {
                            Text(label, style = type.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = colors.primarySecondary, modifier = Modifier.size(72.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = colors.white, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("手不方便或走路时最快，一次点击就完成一次记录。适合做「汇报进度」的入口，不适合排计划。", style = type.caption, color = colors.textTertiary)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("落地规则", style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    listOf("对话页与每个小助手统一用 A：一行 50px", "「记一笔进度」收进 + 号，不再加第二行功能坞", "语音保留在输入栏右侧麦克风，长按说话松手发").forEachIndexed { index, line ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        Text(line, style = type.caption, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputOptionCard(
    badge: String,
    badgeColor: Color,
    title: String,
    highlighted: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.primarySecondary else colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = badgeColor) {
                    Text(badge, style = type.caption, color = colors.white, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(title, style = type.bodyStrong, color = colors.text)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

