package com.attention.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FuguangAuthorizationScreen(
    state: FuguangAuthorizationState = FuguangAuthorizationState(),
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(state.title, style = type.display, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Text(state.subtitle, style = type.body, color = colors.textTertiary)
        }
        item {
            FuguangRuleHeading("①", "静默", state.silentCondition, colors.primary)
            Spacer(Modifier.height(8.dp))
            FuguangSurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.width(54.dp)) {
                        Text("08:00", style = type.caption, color = colors.textTertiary)
                        Text("08:45", style = type.micro, color = colors.textTertiary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.silentExampleTitle, style = type.bodyStrong, color = colors.text)
                        Text(state.silentExampleDetail, style = type.caption, color = colors.textTertiary)
                    }
                    Text("● 已调整", style = type.micro, color = colors.primaryDeep)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("不打断。只在「最近动态」留一行痕，点开可看谁改的、改了什么。", style = type.label, color = colors.textTertiary)
        }
        item {
            FuguangRuleHeading("②", "事后通知", state.notifyCondition, colors.gold)
            Spacer(Modifier.height(8.dp))
            FuguangSurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.width(54.dp)) {
                        Text("19:30", style = type.caption, color = colors.textTertiary)
                        Text("20:30", style = type.micro, color = colors.textTertiary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("雅思写作 Task 2 精练", style = type.bodyStrong, color = colors.text)
                        Text("60 分钟 · 由 21:00 移至", style = type.caption, color = colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceMuted) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("主 Agent 已于 09:12 调整", style = type.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text("撤回", style = type.label, color = colors.gold, modifier = Modifier.padding(8.dp).clickable { actions.onUndoChange(state.undoChangeId) })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("照改，但给一条 24 小时有效的撤回条。撤回要连「为什么改」一起撤销，不做二次协商。", style = type.label, color = colors.textTertiary)
        }
        item {
            FuguangRuleHeading("③", "事前确认", state.confirmCondition, colors.rose)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceMuted, border = BorderStroke(1.dp, colors.goldSoft)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("飞书 · 管理会计课程群", style = type.caption, color = colors.gold)
                    Spacer(Modifier.height(5.dp))
                    Text(state.confirmMessage, style = type.body, color = colors.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FuguangPillButton("顺延到周六 10:00", actions::onConfirmPlan, Modifier.weight(1f), filled = true)
                        FuguangPillButton("保持原样", actions::onCancelPlan, Modifier.weight(1f), filled = false)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("停下来等点头。只给两个动作，不给「忽略」——沉默即不改。", style = type.label, color = colors.textTertiary)
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = colors.primarySoft) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.ironRule, style = type.body, color = colors.primaryDeep)
                }
            }
        }
    }
}

@Composable
fun FuguangSchedulingRulesScreen(
    state: FuguangSchedulingRulesState,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(state.title, style = type.display, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Text(state.subtitle, style = type.body, color = colors.textTertiary)
        }
        items(state.rules.size) { index ->
            val rule = state.rules[index]
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(rule.accent))
                        Spacer(Modifier.width(8.dp))
                        Text("${rule.index} ${rule.title}", style = type.bodyStrong, color = colors.text)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(rule.body, style = type.body, color = colors.textSecondary)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = colors.primarySoft) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.footer, style = type.body, color = colors.primaryDeep)
                }
            }
        }
    }
}

@Composable
fun FuguangResponsibilityScreen(
    state: FuguangResponsibilityState,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(state.title, style = type.display, color = colors.text)
            Spacer(Modifier.height(4.dp))
            Text(state.subtitle, style = type.body, color = colors.textTertiary)
        }
        items(state.roles.size) { index ->
            val (heading, body) = state.roles[index]
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(listOf(colors.rose, colors.primary, colors.gold)[index % 3]))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(heading, style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = type.body, color = colors.textSecondary)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = colors.primarySoft) {
                Text(state.ironRule, style = type.body, color = colors.primaryDeep, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            FuguangSurfaceCard {
                Text(state.explanationTitle, style = type.bodyStrong, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(state.explanation, style = type.body, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FuguangRuleHeading(index: String, title: String, condition: String, color: Color) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(index, style = type.bodyStrong, color = colors.text)
        Spacer(Modifier.width(4.dp))
        Text(title, style = type.bodyStrong, color = colors.text)
        Spacer(Modifier.width(8.dp))
        Text(condition, style = type.caption, color = colors.textTertiary)
    }
}

