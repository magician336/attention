package com.attention.app.fuguang.week.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun FuguangAgentHeader(
    title: String = "浮光主 Agent",
    subtitle: String = "已接入飞书 · 3 个群 · 2 位小助手",
    goalLabel: String = "目标 7.5",
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = type.title, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = type.micro, color = colors.textMuted)
        }
        FuguangWeekTag(text = "●  $goalLabel", accent = FuguangWeekAccent.GOLD)
        if (onMore != null) {
            Spacer(Modifier.width(8.dp))
            FuguangWeekIconBubble(
                icon = Icons.Filled.MoreHoriz,
                contentDescription = "更多",
                background = colors.elevated,
                tint = colors.textSecondary,
                onClick = onMore,
            )
        }
    }
}

@Composable
fun FuguangChatTimestamp(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Text(
        text = text,
        style = type.micro,
        color = colors.textMuted,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun FuguangChatMessageBubble(
    message: FuguangChatMessageUi,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val bubbleShape = RoundedCornerShape(18.dp)
    val bubbleText = messageText(message)
    val isUser = message.author == FuguangMessageAuthor.USER

    if (isUser) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f).testTag("chat-message-${message.id}"),
                shape = bubbleShape,
                color = colors.primarySoft,
            ) {
                Text(
                    text = bubbleText,
                    style = type.body,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            FuguangWeekIconBubble(
                icon = Icons.Filled.LightMode,
                contentDescription = "浮光主 Agent",
                background = colors.goldSoft,
                tint = colors.goldDeep,
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.weight(1f).widthIn(max = 360.dp).testTag("chat-message-${message.id}"),
                shape = bubbleShape,
                color = colors.elevated,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = bubbleText,
                    style = type.body,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                )
            }
        }
    }
}

private fun messageText(message: FuguangChatMessageUi): AnnotatedString = buildAnnotatedString {
    append(message.text)
    message.emphasizedPhrases.forEach { phrase ->
        val start = message.text.indexOf(phrase)
        if (start >= 0) {
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.SemiBold),
                start = start,
                end = start + phrase.length,
            )
        }
    }
}

@Composable
fun FuguangSystemStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: FuguangWeekAccent = FuguangWeekAccent.GREEN,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = fuguangAccentSoftColor(accent).copy(alpha = 0.74f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = fuguangAccentColor(accent),
                modifier = Modifier.size(13.dp),
            )
            Text(text, style = type.micro, color = colors.textSecondary)
        }
    }
}

@Composable
fun FuguangInlineActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = colors.elevated,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.62f)),
    ) {
        Text(
            text = "$text  →",
            style = type.label,
            color = colors.primaryDark,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun FuguangChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit = {},
    placeholder: String = "说点什么…",
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = colors.elevated,
            border = BorderStroke(1.dp, colors.borderStrong),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 46.dp).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = type.body, color = colors.textMuted)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().testTag("week-chat-input"),
                        textStyle = type.body.copy(color = colors.text),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    )
                }
                FuguangWeekIconBubble(
                    icon = Icons.Filled.Mic,
                    contentDescription = "语音输入",
                    background = colors.elevated,
                    tint = colors.textMuted,
                    onClick = onMic,
                )
            }
        }
        Surface(
            onClick = onSend,
            modifier = Modifier.size(48.dp).testTag("week-chat-send"),
            shape = CircleShape,
            color = if (value.isBlank()) colors.primary.copy(alpha = 0.56f) else colors.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "发送",
                    tint = colors.white,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class WeekNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun FuguangChatBottomNav(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangWeekColors()
    val type = fuguangWeekType()
    val items = listOf(
        WeekNavItem("日程", Icons.Filled.CalendarMonth),
        WeekNavItem("目标树", Icons.Filled.AccountTree),
        WeekNavItem("统计", Icons.AutoMirrored.Filled.ShowChart),
        WeekNavItem("对话", Icons.Filled.ChatBubble),
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.elevated.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEachIndexed { index, item ->
                val active = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (active) colors.primary else colors.elevated,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (active) colors.white else colors.textMuted,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            style = type.micro,
                            color = if (active) colors.white else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}