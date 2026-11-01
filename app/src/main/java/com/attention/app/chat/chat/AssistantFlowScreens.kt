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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FuguangAssistantCreationScreen(
    state: FuguangAssistantCreationState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()
    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        FuguangChatTopBar(
            title = state.title,
            subtitle = state.subtitle,
            onBack = actions::onBack,
            onMore = {},
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("assistant-creation-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { FuguangTimestamp("今天 09:20") }
            item { FuguangUserMessage(state.userMessage) }
            item { FuguangAssistantMessage(state.agentMessage) }
            item {
                FuguangSurfaceCard {
                    Text(state.formTitle, style = type.title, color = colors.text)
                    Text(state.formSubtitle, style = type.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(14.dp))
                    Text("目标与期限", style = type.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    Text(state.goalLabel, style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(14.dp))
                    Text("每周能投入", style = type.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.hoursOptions.forEach { hours ->
                            FuguangPillButton(
                                text = hours.toString() + " 小时",
                                onClick = { actions.onSelectWeeklyHours(hours) },
                                modifier = Modifier.weight(1f),
                                filled = hours == state.selectedHours,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("底线", style = type.caption, color = colors.textTertiary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.constraintLabel, style = type.body, color = colors.text, modifier = Modifier.weight(1f))
                        Text("改", style = type.label, color = colors.textTertiary, modifier = Modifier.clickable { actions.onEditConstraints() }.padding(8.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        onClick = actions::onCreateAssistant,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("assistant-create"),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.primarySecondary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(state.createLabel, style = type.title, color = colors.white)
                        }
                    }
                }
            }
            item {
                Text(state.note, style = type.label, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                FuguangSurfaceCard {
                    Text("接下来", style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(6.dp))
                    state.nextSteps.forEachIndexed { index, step ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text((index + 1).toString(), style = type.bodyStrong, color = colors.primaryDeep, modifier = Modifier.width(22.dp))
                            Text(step, style = type.body, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        FuguangQuickSuggestions(state.suggestions, actions::onSuggestionClick, Modifier.padding(horizontal = dims.screenHorizontal))
        Spacer(Modifier.height(8.dp))
        FuguangChatInputBar(
            value = state.input,
            onValueChange = actions::onChatInputChange,
            onSend = actions::onSendChat,
            agentLabel = "主 Agent",
            placeholder = state.inputPlaceholder,
            modifier = Modifier.padding(horizontal = dims.screenHorizontal, vertical = 10.dp),
        )
    }
}

@Composable
fun FuguangAssistantPlanScreen(
    state: FuguangAssistantPlanState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()
    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        FuguangChatTopBar(
            title = state.title,
            subtitle = state.subtitle,
            onBack = actions::onBack,
            onMore = {},
            trailingLabel = state.goalChip,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("assistant-plan-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.primarySecondary) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.heroTitle, style = type.title, color = colors.white)
                            Spacer(Modifier.height(5.dp))
                            Text(state.heroDetail, style = type.body, color = colors.white.copy(alpha = 0.86f))
                            Spacer(Modifier.height(4.dp))
                            Text(state.heroPath, style = type.caption, color = colors.white.copy(alpha = 0.68f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(state.weeklyQuota, style = type.display, color = colors.white)
                            Text("每周配额", style = type.caption, color = colors.white.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.availabilityTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Surface(shape = CircleShape, color = colors.primarySoft) {
                            Text(state.availabilityChip, style = type.caption, color = colors.primaryDeep, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    state.availabilityRows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(row.first, style = type.body, color = colors.textTertiary, modifier = Modifier.width(76.dp))
                            Text(row.second, style = type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(state.availabilityNote, style = type.label, color = colors.gold)
                }
            }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.allocationTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Text(state.allocationAction, style = type.caption, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        state.allocationSegments.forEach { segment ->
                            Box(Modifier.weight(segment.value.coerceAtLeast(0.01f)).height(10.dp).clip(CircleShape).background(segment.color))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(state.allocationDetail, style = type.caption, color = colors.textTertiary)
                }
            }
            item {
                FuguangSurfaceCard {
                    Text(state.anchorsTitle, style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    val dotColors = listOf(colors.primary, colors.primaryTint, colors.gold)
                    state.anchors.forEachIndexed { index, anchor ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColors[index % dotColors.size]))
                            Spacer(Modifier.width(8.dp))
                            Text(anchor, style = type.body, color = colors.textSecondary)
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = colors.primarySoft) {
                    Text(state.note, style = type.body, color = colors.primaryDeep, modifier = Modifier.padding(16.dp))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                    Spacer(Modifier.width(8.dp))
                    Text(state.status, style = type.caption, color = colors.textTertiary)
                }
            }
        }
        FuguangQuickSuggestions(state.suggestions, actions::onSuggestionClick, Modifier.padding(horizontal = dims.screenHorizontal))
        Spacer(Modifier.height(8.dp))
        FuguangChatInputBar(
            value = state.input,
            onValueChange = actions::onChatInputChange,
            onSend = actions::onSendChat,
            agentLabel = "雅思助手",
            placeholder = state.inputPlaceholder,
            modifier = Modifier.padding(horizontal = dims.screenHorizontal, vertical = 10.dp),
        )
    }
}

@Composable
fun FuguangAgentConfirmationScreen(
    state: FuguangAgentConfirmationState,
    actions: FuguangAppActions = FuguangNoOpActions,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dims = fuguangDimensions()
    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        FuguangChatTopBar(
            title = state.title,
            subtitle = state.subtitle,
            onBack = actions::onBack,
            onMore = {},
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("agent-confirmation-list"),
            contentPadding = PaddingValues(start = dims.screenHorizontal, end = dims.screenHorizontal, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { FuguangTimestamp("今天 09:26") }
            item { FuguangAssistantMessage(state.agentMessage) }
            item {
                FuguangSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.planTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                        Surface(shape = CircleShape, color = colors.goldSoft.copy(alpha = 0.55f)) {
                            Text(state.shortfall, style = type.caption, color = colors.gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    state.planRows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(row.label, style = type.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Text(row.duration, style = type.caption, color = colors.textTertiary)
                        }
                    }
                }
            }
            item {
                FuguangSurfaceCard {
                    Text(state.decisionTitle, style = type.bodyStrong, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    state.choices.forEachIndexed { index, choice ->
                        FuguangChoiceRow(
                            index = index,
                            text = choice,
                            selected = index == state.selectedChoice,
                            onClick = { actions.onSelectPlanChoice(index) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FuguangPillButton(state.confirmLabel, actions::onConfirmPlan, Modifier.weight(1.6f), filled = true)
                        FuguangPillButton(state.cancelLabel, actions::onCancelPlan, Modifier.weight(1f), filled = false)
                    }
                }
            }
            item { Text(state.note, style = type.label, color = colors.textTertiary) }
        }
        FuguangQuickSuggestions(state.suggestions, actions::onSuggestionClick, Modifier.padding(horizontal = dims.screenHorizontal))
        Spacer(Modifier.height(8.dp))
        FuguangChatInputBar(
            value = state.input,
            onValueChange = actions::onChatInputChange,
            onSend = actions::onSendChat,
            agentLabel = "主 Agent",
            placeholder = state.inputPlaceholder,
            modifier = Modifier.padding(horizontal = dims.screenHorizontal, vertical = 10.dp),
        )
    }
}

@Composable
private fun FuguangChoiceRow(index: Int, text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primarySoft else colors.surfaceMuted,
        border = BorderStroke(1.dp, if (selected) colors.primaryTint else colors.divider),
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(if (selected) colors.primarySecondary else colors.border),
                contentAlignment = Alignment.Center,
            ) {
                Text((index + 1).toString(), style = type.micro, color = if (selected) colors.white else colors.textTertiary)
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = type.body, color = if (selected) colors.primaryDeep else colors.textSecondary)
        }
    }
}

