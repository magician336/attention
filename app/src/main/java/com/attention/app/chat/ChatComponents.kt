package com.attention.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FuguangChatTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp).testTag("chat-back")) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.text,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.navigationTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = type.micro,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingLabel != null) {
            Surface(
                shape = CircleShape,
                color = colors.primarySoft,
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Text(
                    text = trailingLabel,
                    style = type.label,
                    color = colors.primaryDeep,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        } else {
            IconButton(onClick = onMore, modifier = Modifier.size(44.dp).testTag("chat-more")) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "更多",
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun FuguangAgentAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    emphasized: Boolean = false,
) {
    val colors = fuguangPalette()
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (emphasized) colors.primaryTint else colors.primarySoft,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = if (emphasized) colors.primaryDeep else colors.primary,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

@Composable
fun FuguangTimestamp(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = fuguangTypography().micro,
        color = fuguangPalette().textTertiary,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun FuguangUserMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp).semantics { contentDescription = "用户消息" },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = colors.primarySecondary,
        ) {
            Text(
                text = text,
                style = type.body,
                color = colors.white,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun FuguangAssistantMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "浮光主 Agent 消息" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FuguangAgentAvatar()
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Text(
                text = text,
                style = type.body,
                color = colors.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun FuguangAdjustmentCard(
    title: String,
    rows: List<AdjustmentRow>,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(fuguangDimensions().cardRadius),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(fuguangDimensions().cardPadding)) {
            Text(text = title, style = type.bodyStrong, color = colors.text)
            Spacer(Modifier.height(8.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Column {
                    Text(text = row.title, style = type.body, color = colors.textSecondary)
                    Text(text = row.detail, style = type.label, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
fun FuguangNotificationCard(
    source: String,
    timestamp: String,
    body: String,
    conflict: String,
    primaryAction: String,
    secondaryAction: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(fuguangDimensions().cardRadius),
        color = colors.surfaceMuted,
        border = BorderStroke(1.dp, colors.goldSoft),
    ) {
        Column(modifier = Modifier.padding(fuguangDimensions().cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(colors.goldSoft.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = colors.gold,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(text = source, style = type.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Text(text = timestamp, style = type.micro, color = colors.textTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Text(text = body, style = type.body, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text(text = conflict, style = type.label, color = colors.gold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuguangPillButton(
                    text = primaryAction,
                    onClick = onPrimaryAction,
                    modifier = Modifier.weight(1f).testTag("notification-primary"),
                    filled = true,
                )
                FuguangPillButton(
                    text = secondaryAction,
                    onClick = onSecondaryAction,
                    modifier = Modifier.weight(1f).testTag("notification-secondary"),
                    filled = false,
                )
            }
        }
    }
}

@Composable
fun FuguangQuickSuggestions(
    suggestions: List<FuguangChatSuggestion>,
    onSuggestionClick: (FuguangChatSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            val (background, foreground, border) = when (suggestion.tone) {
                FuguangSuggestionTone.GREEN -> Triple(colors.primarySoft, colors.primaryDeep, colors.primaryTint)
                FuguangSuggestionTone.GOLD -> Triple(colors.surface, colors.gold, colors.goldSoft)
                FuguangSuggestionTone.NEUTRAL -> Triple(colors.surface, colors.textSecondary, colors.border)
            }
            Surface(
                onClick = { onSuggestionClick(suggestion) },
                shape = CircleShape,
                color = background,
                contentColor = foreground,
                border = BorderStroke(1.dp, border),
                modifier = Modifier.heightIn(min = 44.dp).testTag("chat-suggestion-${suggestion.id}"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (suggestion.tone == FuguangSuggestionTone.GREEN) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(text = suggestion.label, style = type.label, color = foreground)
                }
            }
        }
    }
}

@Composable
fun FuguangChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    agentLabel: String,
    modifier: Modifier = Modifier,
    placeholder: String = "说点什么…",
    onAdd: () -> Unit = {},
    onMic: () -> Unit = {},
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val canSend = value.isNotBlank()

    Surface(
        modifier = modifier.fillMaxWidth().testTag("chat-input-shell"),
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted)
                    .clickable(onClick = onAdd)
                    .testTag("chat-add"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = colors.textSecondary)
            }
            Surface(shape = CircleShape, color = colors.primarySoft) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colors.primary))
                    Text(text = agentLabel, style = type.caption, color = colors.primaryDeep, maxLines = 1)
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).testTag("chat-input"),
                textStyle = type.body.copy(color = colors.text),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = type.body,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(onClick = onMic, modifier = Modifier.size(40.dp).testTag("chat-mic")) {
                Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = colors.textTertiary)
            }
            Surface(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(42.dp).testTag("chat-send"),
                shape = CircleShape,
                color = if (canSend) colors.primary else colors.primaryTint,
                contentColor = colors.white,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "发送",
                        tint = if (canSend) colors.white else colors.primaryDeep.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
fun FuguangPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean,
    enabled: Boolean = true,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = if (filled) colors.primary else colors.surface,
        contentColor = if (filled) colors.white else colors.textSecondary,
        border = if (filled) null else BorderStroke(1.dp, colors.border),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FuguangSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = type.navigationTitle, color = colors.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                text = action,
                style = type.caption,
                color = actionColor ?: colors.textTertiary,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier,
            )
        }
    }
}

@Composable
fun FuguangProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = fuguangPalette().divider,
    fillColor: Color = fuguangPalette().primarySecondary,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(trackColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(fillColor),
        )
    }
}

@Composable
fun FuguangBottomNav(
    selected: FuguangBottomDestination,
    onSelect: (FuguangBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val type = fuguangTypography()
    val items = listOf(
        Triple(FuguangBottomDestination.AGENDA, "日程", Icons.Outlined.Event),
        Triple(FuguangBottomDestination.CHAT, "对话", Icons.Outlined.ChatBubbleOutline),
        Triple(FuguangBottomDestination.STUDY, "学习", Icons.AutoMirrored.Outlined.MenuBook),
        Triple(FuguangBottomDestination.PROFILE, "我的", Icons.Outlined.PersonOutline),
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (destination, label, icon) ->
                val isSelected = destination == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSelected) colors.primarySecondary else Color.Transparent)
                        .clickable { onSelect(destination) }
                        .semantics {
                            this.selected = isSelected
                            role = Role.Tab
                        }
                        .testTag("chat-nav-${destination.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) colors.white else colors.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            style = type.caption,
                            color = if (isSelected) colors.white else colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FuguangHubIconBadge(
    icon: FuguangHubIcon,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    val background = when (icon) {
        FuguangHubIcon.IELTS -> if (emphasized) colors.primaryTint else colors.primarySoft
        FuguangHubIcon.PLAN -> colors.roseSoft
    }
    val tint = when (icon) {
        FuguangHubIcon.IELTS -> colors.primaryDeep
        FuguangHubIcon.PLAN -> colors.rose
    }
    Box(
        modifier = modifier.size(38.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (icon) {
                FuguangHubIcon.IELTS -> Icons.AutoMirrored.Outlined.MenuBook
                FuguangHubIcon.PLAN -> Icons.Outlined.EditNote
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun FuguangActivityIcon(
    activity: FuguangHubActivity,
    modifier: Modifier = Modifier,
) {
    val colors = fuguangPalette()
    if (activity.highlighted) {
        Box(
            modifier = modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.goldSoft.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = colors.gold, modifier = Modifier.size(19.dp))
        }
    } else {
        Box(
            modifier = modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(20.dp))
        }
    }
}


