package com.attention.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.theme.attentionTokens
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.OnboardingActions
import com.attention.workspace.state.OnboardingScreenState
import com.attention.workspace.state.OnboardingStepKey

/**
 * 首次设置以内嵌卡片的形式出现在工作台上，因此底部导航、添加日程和自由计时始终可达。
 * 每一步允许跳过；页面只在用户确认输入后发出对应 intent。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingScreen(
    state: OnboardingScreenState,
    actions: OnboardingActions,
) {
    val tokens = attentionTokens()
    val selection = rememberOnboardingSelection()
    val index = selection.pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    val page = state.pages.getOrNull(index)
    var boundary by remember(state.initialSettings) { mutableStateOf(state.initialSettings.planningDayBoundaryMinutes.toString()) }
    var weekStart by remember(state.initialSettings) { mutableStateOf(state.initialSettings.weekStartDay) }
    var capacity by remember(state.initialSettings) { mutableStateOf(state.initialSettings.dailyCapacityMinutes?.toString().orEmpty()) }
    var targetTitle by remember { mutableStateOf("") }
    var scheduleTitle by remember { mutableStateOf("") }
    var scheduleEstimate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun advance() {
        error = null
        if (selection.skipCurrentPage(state.pageCount)) actions.completeOnboarding()
    }

    Surface(Modifier.fillMaxWidth().testTag("onboarding-screen")) {
        if (page == null) {
            Text("首次设置内容缺失", modifier = Modifier.padding(tokens.spacing.large))
            return@Surface
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("首次设置", style = tokens.typography.title, modifier = Modifier.testTag("onboarding-title"))
            Text("第 ${index + 1} 步，共 ${state.pageCount} 步", modifier = Modifier.testTag("onboarding-indicator"))
            Text(page.title, style = tokens.typography.title, modifier = Modifier.testTag("onboarding-page-title"))
            Text(page.body, modifier = Modifier.testTag("onboarding-page-body"))
            when (page.key) {
                OnboardingStepKey.PLANNING_DAY_BOUNDARY -> {
                    OutlinedTextField(
                        value = boundary,
                        onValueChange = { boundary = it; error = null },
                        label = { Text("规划日界线（分钟，0-1439）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-boundary-input"),
                        singleLine = true,
                    )
                }
                OnboardingStepKey.WEEK_START_DAY -> {
                    Text("选择一周从哪天开始")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日").forEach { (day, label) ->
                            if (weekStart == day) AttentionButton(onClick = {}, modifier = Modifier.testTag("onboarding-week-$day")) { Text(label) }
                            else AttentionOutlinedButton(onClick = { weekStart = day }, modifier = Modifier.testTag("onboarding-week-$day")) { Text(label) }
                        }
                    }
                }
                OnboardingStepKey.DAILY_CAPACITY -> {
                    OutlinedTextField(
                        value = capacity,
                        onValueChange = { capacity = it; error = null },
                        label = { Text("每日容量（分钟，可选）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-capacity-input"),
                        singleLine = true,
                    )
                }
                OnboardingStepKey.FIRST_TARGET -> {
                    OutlinedTextField(
                        value = targetTitle,
                        onValueChange = { targetTitle = it; error = null },
                        label = { Text("计划名称") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-plan-title"),
                        singleLine = true,
                    )
                }
                OnboardingStepKey.FIRST_SCHEDULE -> {
                    OutlinedTextField(
                        value = scheduleTitle,
                        onValueChange = { scheduleTitle = it; error = null },
                        label = { Text("日程标题") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-title"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = scheduleEstimate,
                        onValueChange = { scheduleEstimate = it; error = null },
                        label = { Text("预计分钟（可选）") },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding-schedule-estimate"),
                        singleLine = true,
                    )
                }
            }
            error?.let { AttentionInlineError(message = it, messageTag = "onboarding-error") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AttentionOutlinedButton(
                    modifier = Modifier.weight(1f).testTag("onboarding-skip-page"),
                    onClick = ::advance,
                ) { Text("跳过这一步") }
                AttentionTextButton(
                    modifier = Modifier.testTag("onboarding-skip-remaining"),
                    onClick = actions::completeOnboarding,
                ) { Text("跳过剩余") }
            }
            AttentionButton(
                modifier = Modifier.fillMaxWidth().testTag("onboarding-save-step"),
                onClick = {
                    when (page.key) {
                        OnboardingStepKey.PLANNING_DAY_BOUNDARY -> {
                            val value = boundary.trim().toIntOrNull()
                            val capacityValue = capacity.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                            when {
                                value == null || value !in 0 until 1440 -> error = "规划日界线必须是 0 到 1439 分钟"
                                capacity.isNotBlank() && (capacityValue == null || capacityValue <= 0) -> error = "每日容量必须是正整数"
                                else -> {
                                    actions.savePlanningDaySettings(value, weekStart, capacityValue)
                                    advance()
                                }
                            }
                        }
                        OnboardingStepKey.WEEK_START_DAY -> {
                            actions.selectWeekStartDay(weekStart)
                            advance()
                        }
                        OnboardingStepKey.DAILY_CAPACITY -> {
                            val value = capacity.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                            if (capacity.isNotBlank() && (value == null || value <= 0)) error = "每日容量必须是正整数"
                            else {
                                val boundaryValue = boundary.trim().toIntOrNull() ?: state.initialSettings.planningDayBoundaryMinutes
                                actions.savePlanningDaySettings(boundaryValue, weekStart, value)
                                advance()
                            }
                        }
                        OnboardingStepKey.FIRST_TARGET -> {
                            if (targetTitle.isBlank()) error = "计划名称不能为空；也可以跳过这一步"
                            else {
                                actions.addFirstTarget(targetTitle.trim())
                                advance()
                            }
                        }
                        OnboardingStepKey.FIRST_SCHEDULE -> {
                            val estimate = scheduleEstimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                            when {
                                scheduleTitle.isBlank() -> error = "日程标题不能为空；也可以跳过这一步"
                                scheduleEstimate.isNotBlank() && (estimate == null || estimate <= 0) -> error = "预计分钟必须是正整数"
                                else -> {
                                    actions.addFirstSchedule(
                                        AgendaScheduleDraft(
                                            planningDate = state.planningDate,
                                            title = scheduleTitle.trim(),
                                            startMinute = null,
                                            endMinute = null,
                                            estimatedMinutes = estimate,
                                            note = "",
                                            targetId = null,
                                            reminderMinuteOfDay = null,
                                        ),
                                    )
                                    advance()
                                }
                            }
                        }
                    }
                },
            ) { Text("保存并继续") }
        }
    }
}
