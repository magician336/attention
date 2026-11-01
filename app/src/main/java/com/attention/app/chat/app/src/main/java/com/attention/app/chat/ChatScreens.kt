package com.attention.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FuguangAgentChatScreen(
    state: FuguangChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (FuguangChatSuggestion) -> Unit,
    onBack: () -> Unit = {},
    onMore: () -> Unit = {},
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: () -> Unit = {},
    onAdd: () -> Unit = {},
    onMic: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val dimensions = fuguangDimensions()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        FuguangChatTopBar(
            title = state.title,
            subtitle = state.subtitle,
            onBack = onBack,
            onMore = onMore,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-message-list"),
            contentPadding = PaddingValues(
                start = dimensions.screenHorizontal,
                end = dimensions.screenHorizontal,
                top = 12.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                when (entry) {
                    is FuguangChatEntry.Timestamp -> FuguangTimestamp(entry.label)
                    is FuguangChatEntry.UserText -> FuguangUserMessage(entry.text)
                    is FuguangChatEntry.AssistantText -> FuguangAssistantMessage(entry.text)
                    is FuguangChatEntry.AdjustmentCard -> FuguangAdjustmentCard(
                        title = entry.title,
                        rows = entry.rows,
                    )
                    is FuguangChatEntry.NotificationCard -> FuguangNotificationCard(
                        source = entry.source,
                        timestamp = entry.timestamp,
                        body = entry.body,
                        conflict = entry.conflict,
                        primaryAction = entry.primaryAction,
                        secondaryAction = entry.secondaryAction,
                        onPrimaryAction = onPrimaryAction,
                        onSecondaryAction = onSecondaryAction,
                    )
                }
            }
        }
        FuguangQuickSuggestions(
            suggestions = state.suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = Modifier.padding(horizontal = dimensions.screenHorizontal),
        )
        Spacer(Modifier.height(8.dp))
        FuguangChatInputBar(
            value = state.input,
            onValueChange = onInputChange,
            onSend = onSend,
            agentLabel = state.agentLabel,
            placeholder = state.inputPlaceholder,
            onAdd = onAdd,
            onMic = onMic,
            modifier = Modifier.padding(
                start = dimensions.screenHorizontal,
                end = dimensions.screenHorizontal,
                bottom = 10.dp,
            ),
        )
    }
}

@Composable
fun FuguangConversationHubScreen(
    state: FuguangConversationHubState,
    selectedDestination: FuguangBottomDestination = FuguangBottomDestination.CHAT,
    onOpenMainAgent: () -> Unit,
    onOpenAssistant: (String) -> Unit,
    onAddAssistant: () -> Unit = {},
    onManageAssistants: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onSync: () -> Unit = {},
    onBottomDestination: (FuguangBottomDestination) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dimensions = fuguangDimensions()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("conversation-hub-list"),
            contentPadding = PaddingValues(
                start = dimensions.screenHorizontal,
                end = dimensions.screenHorizontal,
                top = dimensions.screenTop,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.title,
                        style = type.display,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = state.readLabel, style = type.caption, color = colors.textTertiary)
                }
            }
            item {
                FuguangMainAgentCard(
                    state = state,
                    onOpen = onOpenMainAgent,
                )
            }
            item {
                FuguangSectionHeader(
                    title = "我的小助手",
                    action = "管理",
                    onAction = onManageAssistants,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.assistants.forEach { assistant ->
                        FuguangHubAssistantCard(
                            assistant = assistant,
                            onClick = { onOpenAssistant(assistant.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Surface(
                    onClick = onAddAssistant,
                    modifier = Modifier.fillMaxWidth().testTag("chat-add-assistant"),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceMuted,
                    border = BorderStroke(1.dp, colors.divider),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "添加小助手 · 新建长期计划",
                            style = type.label,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(2.dp))
                FuguangSectionHeader(
                    title = "最近动态",
                    action = state.syncLabel,
                    onAction = onSync,
                    actionColor = colors.gold,
                )
            }
            items(state.activities, key = { it.id }) { activity ->
                FuguangActivityRow(activity = activity, onClick = onOpenActivities)
            }
        }
        FuguangBottomNav(
            selected = selectedDestination,
            onSelect = onBottomDestination,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FuguangMainAgentCard(
    state: FuguangConversationHubState,
    onOpen: () -> Unit,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("chat-main-agent-card"),
        shape = RoundedCornerShape(24.dp),
        color = colors.primarySecondary,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FuguangAgentAvatar(size = 38.dp, emphasized = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = state.mainAgentTitle,
                    style = type.navigationTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.white,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = CircleShape, color = colors.goldSoft) {
                    Text(
                        text = state.mainAgentBadge,
                        style = type.caption,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(text = state.mainAgentSummary, style = type.body, color = colors.white.copy(alpha = 0.94f))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = state.pendingLabel, style = type.label, color = colors.white.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
                Text(text = "${state.openLabel}  →", style = type.label, color = colors.white)
            }
        }
    }
}

@Composable
private fun FuguangHubAssistantCard(
    assistant: FuguangHubAssistant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("chat-assistant-${assistant.id}"),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FuguangHubIconBadge(icon = assistant.icon, emphasized = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = assistant.title,
                    style = type.navigationTitle,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(text = assistant.targetLabel, style = type.caption, color = colors.textTertiary)
            Spacer(Modifier.height(8.dp))
            FuguangProgressBar(
                progress = assistant.progress,
                fillColor = when (assistant.icon) {
                    FuguangHubIcon.IELTS -> colors.primarySecondary
                    FuguangHubIcon.PLAN -> colors.rose
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(text = assistant.footer, style = type.micro, color = colors.textSecondary)
        }
    }
}

@Composable
private fun FuguangActivityRow(
    activity: FuguangHubActivity,
    onClick: () -> Unit,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("chat-activity-${activity.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (activity.highlighted) colors.surfaceMuted else colors.surface,
        border = BorderStroke(1.dp, if (activity.highlighted) colors.goldSoft else colors.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FuguangActivityIcon(activity = activity)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = activity.title, style = type.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(text = activity.detail, style = type.caption, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = activity.time, style = type.micro, color = colors.textTertiary)
        }
    }
}

@Composable
fun FuguangAssistantDetailScreen(
    state: FuguangAssistantDetailState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (FuguangChatSuggestion) -> Unit,
    onBack: () -> Unit = {},
    onMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val dimensions = fuguangDimensions()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        FuguangChatTopBar(
            title = state.title,
            subtitle = state.subtitle,
            onBack = onBack,
            onMore = onMore,
            trailingLabel = state.goalLabel,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("assistant-detail-list"),
            contentPadding = PaddingValues(start = dimensions.screenHorizontal, end = dimensions.screenHorizontal, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FuguangAssistantHeroCard(state)
            }
            item {
                FuguangSectionHeader(title = "今日任务 · ${state.tasks.count { it.completed }}/${state.tasks.size} 完成", action = "本周全部")
            }
            items(state.tasks, key = { it.id }) { task ->
                FuguangAssistantTaskRow(task)
            }
            item {
                FuguangPlanCard(state)
            }
            item {
                FuguangWeeklyChartCard(state)
            }
            item {
                FuguangSyncBanner(state.syncMessage)
            }
        }
        FuguangQuickSuggestions(
            suggestions = state.suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = Modifier.padding(horizontal = dimensions.screenHorizontal),
        )
        Spacer(Modifier.height(8.dp))
        FuguangChatInputBar(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
            agentLabel = state.title,
            placeholder = state.inputPlaceholder,
            modifier = Modifier.padding(horizontal = dimensions.screenHorizontal, vertical = 10.dp),
        )
    }
}

@Composable
private fun FuguangAssistantHeroCard(state: FuguangAssistantDetailState) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primarySecondary,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                val track = colors.white.copy(alpha = 0.18f)
                val indicator = colors.goldSoft
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 9.dp.toPx()
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = indicator,
                        startAngle = -90f,
                        sweepAngle = 248f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.white, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = state.heroTitle, style = type.title, color = colors.white)
                Spacer(Modifier.height(5.dp))
                Text(text = state.heroDetail, style = type.body, color = colors.white.copy(alpha = 0.86f))
                Spacer(Modifier.height(5.dp))
                Text(text = state.heroFootnote, style = type.caption, color = colors.white.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun FuguangAssistantTaskRow(task: FuguangAssistantTask) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (task.completed) colors.primarySecondary else colors.surface)
                    .then(if (!task.completed) Modifier.background(colors.surface) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (task.completed) {
                    Icon(Icons.Filled.Check, contentDescription = "已完成", tint = colors.white, modifier = Modifier.size(15.dp))
                } else {
                    Box(Modifier.size(19.dp).clip(CircleShape).background(colors.border))
                    Box(Modifier.size(15.dp).clip(CircleShape).background(colors.surface))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = task.title,
                style = type.bodyStrong,
                color = if (task.completed) colors.textTertiary else colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(text = task.duration, style = type.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun FuguangPlanCard(state: FuguangAssistantDetailState) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = state.planTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                Text(text = state.planFootnote, style = type.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    val filled = state.planProgress >= (index + 1) / 3f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(if (filled) colors.primarySecondary else colors.divider),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("基础 1-4 周", "强化 5-8 周", "冲刺 9-12 周").forEach { label ->
                    Text(text = label, style = type.micro, color = colors.textTertiary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FuguangWeeklyChartCard(state: FuguangAssistantDetailState) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = state.chartTitle, style = type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
                Text(text = state.chartDetail, style = type.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(94.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.chartBars.forEachIndexed { index, value ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((24 + 70 * value.coerceIn(0f, 1f)).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (index == state.chartBars.lastIndex) colors.primarySecondary else colors.primaryTint),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("四", "五", "六", "日", "一", "二", "今").forEach { label ->
                    Text(text = label, style = type.micro, color = colors.textTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun FuguangSyncBanner(message: String) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.primarySoft,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = message, style = type.caption, color = colors.primaryDeep)
        }
    }
}
