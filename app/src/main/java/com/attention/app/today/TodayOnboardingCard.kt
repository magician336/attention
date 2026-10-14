package com.attention.app.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.TodayActions
import com.attention.workspace.state.TodayScreenState

/**
 * 首次设置流程。
 *
 * 页面这一侧只有表单与五步的顺序；它只发 intent，因此走的是和离线一样的公开设置命令，
 * Activity 重建后由 DataStore 恢复（ADR-0005 §24：表单状态属于页面 module）。
 *
 * 日程的规划日初值来自投影的规划日，不再读系统时钟。
 *
 * 标签、文案与排列与迁移前一致。
 */
@Composable
internal fun TodayOnboardingCard(state: TodayScreenState, actions: TodayActions) {
    val tokens = attentionTokens()
    val draft = rememberOnboardingDraft(state.planningDate.toString())
    draft.syncDefaults(state)

    fun nextOrFinish() {
        if (draft.advance()) actions.finishOnboarding()
    }

    Card(Modifier.fillMaxWidth().testTag("onboarding-card")) {
        Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("首次设置", style = tokens.typography.title)
            Text("第 ${draft.step + 1} 步，共 5 步")
            when (draft.step) {
                0 -> {
                    Text("设置规划日界线")
                    Text("默认 03:00；请输入 0–1439 分钟。")
                    OutlinedTextField(
                        value = draft.boundary,
                        onValueChange = draft::typeBoundary,
                        label = { Text("规划日界线（分钟）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-boundary-input"),
                        singleLine = true,
                        isError = draft.error != null,
                    )
                }
                1 -> {
                    Text("设置每周起始日")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.weekDayOptions.forEach { option ->
                            if (option.day == draft.weekStartDay) {
                                AttentionButton(onClick = {}, modifier = Modifier.testTag("onboarding-week-${option.day}")) {
                                    Text(option.label)
                                }
                            } else {
                                AttentionOutlinedButton(
                                    onClick = { draft.selectWeekStartDay(option.day) },
                                    modifier = Modifier.testTag("onboarding-week-${option.day}"),
                                ) { Text(option.label) }
                            }
                        }
                    }
                }
                2 -> {
                    Text("设置每日容量")
                    Text("可留空；填写时必须是正整数分钟。")
                    OutlinedTextField(
                        value = draft.capacity,
                        onValueChange = draft::typeCapacity,
                        label = { Text("每日容量（分钟，可选）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-capacity-input"),
                        singleLine = true,
                        isError = draft.error != null,
                    )
                }
                3 -> {
                    Text("创建首个计划")
                    Text("可跳过；标题不能为空。")
                    OutlinedTextField(
                        draft.planTitle,
                        draft::typePlanTitle,
                        label = { Text("计划标题") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-plan-title"),
                        singleLine = true,
                        isError = draft.error != null,
                    )
                }
                else -> {
                    Text("创建第一条日程")
                    Text("可跳过；标题和规划日必填。")
                    OutlinedTextField(
                        draft.scheduleTitle,
                        draft::typeScheduleTitle,
                        label = { Text("日程标题") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-title"),
                        singleLine = true,
                        isError = draft.error != null,
                    )
                    OutlinedTextField(
                        draft.scheduleDate,
                        draft::typeScheduleDate,
                        label = { Text("规划日（YYYY-MM-DD）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-date"),
                        singleLine = true,
                        isError = draft.error != null,
                    )
                    OutlinedTextField(
                        draft.scheduleEstimate,
                        draft::typeScheduleEstimate,
                        label = { Text("预计分钟（可选）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-estimate"),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            draft.scheduleStart,
                            draft::typeScheduleStart,
                            label = { Text("开始分钟") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            draft.scheduleEnd,
                            draft::typeScheduleEnd,
                            label = { Text("结束分钟") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        draft.scheduleNote,
                        draft::typeScheduleNote,
                        label = { Text("备注（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    state.targetOptions.forEach { target ->
                        if (target.targetId == draft.scheduleTargetId) {
                            AttentionButton(onClick = {}, modifier = Modifier.testTag("onboarding-schedule-target-${target.targetId}")) {
                                Text("关联：${target.title}")
                            }
                        } else {
                            AttentionOutlinedButton(
                                onClick = { draft.selectScheduleTarget(target.targetId) },
                                modifier = Modifier.testTag("onboarding-schedule-target-${target.targetId}"),
                            ) { Text("关联：${target.title}") }
                        }
                    }
                    AttentionTextButton(onClick = { draft.selectScheduleTarget(null) }) { Text("不关联计划") }
                }
            }
            draft.error?.let { AttentionInlineError(message = it, messageTag = "onboarding-error") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttentionButton(
                    modifier = Modifier.testTag("onboarding-save-continue"),
                    onClick = {
                        when (val save = draft.resolveSave(state)) {
                            is OnboardingSave.None -> Unit
                            is OnboardingSave.PlanningSettings -> {
                                actions.saveOnboardingPlanningSettings(
                                    save.boundaryMinutes,
                                    save.weekStartDay,
                                    save.dailyCapacityMinutes,
                                )
                                nextOrFinish()
                            }
                            is OnboardingSave.Target -> actions.createOnboardingTarget(save.title) { nextOrFinish() }
                            is OnboardingSave.Schedule -> {
                                actions.createOnboardingSchedule(save.draft)
                                nextOrFinish()
                            }
                        }
                    },
                ) { Text("保存并继续") }
                AttentionOutlinedButton(
                    modifier = Modifier.testTag("onboarding-skip-step"),
                    onClick = { if (draft.skipStep()) actions.finishOnboarding() },
                ) { Text("跳过当前步骤") }
            }
            AttentionTextButton(
                modifier = Modifier.testTag("onboarding-skip-all"),
                onClick = actions::finishOnboarding,
            ) { Text("跳过剩余步骤") }
        }
    }
}
