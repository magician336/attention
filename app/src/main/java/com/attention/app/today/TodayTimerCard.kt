package com.attention.app.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionPermissionState
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.TodayScreenState

/**
 * 今天工作台的计时卡片。
 *
 * 页面这一侧不碰任何 Android API，也不读系统时钟：正在计时的目标、已用时与结束范围预览
 * 都由投影按组合根注入的时刻算好（ADR-0005 §3、§45-47）。开始、暂停、恢复、切换与结束
 * 只发出 intent；启动前台计时服务、撤掉恢复提示与通知不可用时的提示由组合根 adapter 完成。
 *
 * 标签、文案与排列与迁移前一致。
 */
@Composable
internal fun TodayTimerCard(state: TodayScreenState, actions: TodayActions) {
    val tokens = attentionTokens()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("计时器", style = tokens.typography.title)
            val timer = state.activeTimer
            if (timer == null) {
                Text("计时结束时会按规划日界线自动切分时间记录。")
                AttentionButton(onClick = {
                    actions.startTimer(null)
                }) { Text("开始未归属计时") }
                state.targetOptions.forEach { target ->
                    AttentionOutlinedButton(onClick = {
                        actions.startTimer(target.targetId)
                    }) { Text("计时：${target.title}") }
                }
            } else {
                Text(if (timer.paused) "计时已暂停" else "正在计时")
                Text("当前目标：${timer.targetLabel}")
                if (timer.completedSegmentCount > 0) {
                    Text("已完成片段：${timer.completedSegmentCount}")
                }
                val preview = timer.settlementPreview
                val settlementScope = preview.segments.joinToString("；") {
                    "${it.planningDate} ${it.durationMinutes} 分钟"
                }
                Text(
                    if (preview.hasCompleteMinutes) {
                        "结束范围预览：$settlementScope；共 ${preview.totalMinutes} 分钟"
                    } else {
                        "结束范围预览：当前片段不足 1 分钟，不会生成时间记录"
                    },
                    modifier = Modifier.testTag("timer-settlement-preview"),
                )
                if (!state.notificationsEnabled) {
                    AttentionPermissionState(
                        label = "权限未开启",
                        description = "通知权限未开启，计时数据仍会保存；通知栏暂停、继续和结束控制不可用。",
                        required = true,
                        modifier = Modifier.testTag("timer-notification-permission-warning"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (timer.paused) AttentionButton(onClick = actions::resumeTimer) { Text("继续") }
                    else AttentionButton(onClick = actions::pauseTimer) { Text("暂停") }
                    AttentionOutlinedButton(onClick = actions::recoverTimer) { Text("从此刻继续") }
                    AttentionOutlinedButton(onClick = actions::stopTimer) { Text("结束并保存") }
                }
                if (timer.targetId != null) {
                    AttentionOutlinedButton(
                        modifier = Modifier.testTag("switch-timer-unowned"),
                        onClick = { actions.switchTimer(null) },
                    ) { Text("切换到未归属活动") }
                }
                state.targetOptions.filter { it.targetId != timer.targetId }.forEach { target ->
                    AttentionOutlinedButton(onClick = { actions.switchTimer(target.targetId) }) {
                        Text("切换到 ${target.title}")
                    }
                }
            }
        }
    }
}
