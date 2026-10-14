package com.attention.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.attention.app.ui.theme.attentionTokens

/**
 * 内联错误：把校验错误显示在出错的位置附近，而不是只在页面顶部报错一次。
 *
 * 错误同时带图标与文案，色弱用户也能看懂哪里出了问题。
 */
@Composable
fun AttentionInlineError(
    message: String,
    retryLabel: String = "重试",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    Column(
        modifier = modifier
            .testTag(AttentionFeedbackTags.INLINE_ERROR)
            .padding(tokens.spacing.small),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = message,
                tint = tokens.brand.error.accent,
                modifier = Modifier.size(tokens.spacing.medium),
            )
            Text(
                text = message,
                style = tokens.typography.caption,
                color = tokens.brand.error.onContainer,
                modifier = Modifier.testTag(AttentionFeedbackTags.INLINE_ERROR_MESSAGE),
            )
        }
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(AttentionFeedbackTags.INLINE_ERROR_RETRY),
            ) {
                Text(
                    text = retryLabel,
                    style = tokens.typography.caption,
                    color = tokens.brand.error.accent,
                )
            }
        }
    }
}

/**
 * 确认弹窗：危险操作前说明影响，让用户不会误删数据。
 *
 * 影响说明是必填的——没有说明的确认弹窗不允许构造。
 */
@Composable
fun AttentionConfirmDialog(
    visible: Boolean,
    title: String,
    impact: String,
    confirmLabel: String = "确认",
    dismissLabel: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val tokens = attentionTokens()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "注意",
                tint = tokens.brand.error.accent,
            )
        },
        title = {
            Text(
                text = title,
                style = tokens.typography.title,
                color = tokens.colorScheme.onSurface,
                modifier = Modifier.testTag(AttentionFeedbackTags.CONFIRM_DIALOG),
            )
        },
        text = {
            Text(
                text = impact,
                style = tokens.typography.body,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(AttentionFeedbackTags.CONFIRM_DIALOG_IMPACT),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(AttentionFeedbackTags.CONFIRM_DIALOG_CONFIRM),
            ) {
                Text(
                    text = confirmLabel,
                    style = tokens.typography.body,
                    color = tokens.brand.error.accent,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(AttentionFeedbackTags.CONFIRM_DIALOG_DISMISS),
            ) {
                Text(
                    text = dismissLabel,
                    style = tokens.typography.body,
                    color = tokens.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(tokens.radius.medium),
        containerColor = tokens.colorScheme.surfaceContainerHigh,
    )
}

/**
 * 禁用态操作：不可用的操作显示为禁用而不是隐藏。
 *
 * 用户因此知道它存在但现在不能用；[reason] 说明为什么不能用。
 */
@Composable
fun AttentionDisabledAction(
    label: String,
    reason: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    Column(
        modifier = modifier.padding(tokens.spacing.small),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Button(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            shape = RoundedCornerShape(tokens.radius.small),
            modifier = Modifier.testTag(AttentionFeedbackTags.DISABLED_ACTION),
        ) {
            Text(text = label, style = tokens.typography.body)
        }
        if (reason != null) {
            Text(
                text = reason,
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(AttentionFeedbackTags.DISABLED_ACTION_REASON),
            )
        }
    }
}

/**
 * 选中态：颜色之外同时用图标与文字表达选中，用户始终知道当前选中了什么。
 */
@Composable
fun AttentionSelectedIndicator(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    val container = if (selected) {
        tokens.brand.completed.container
    } else {
        tokens.colorScheme.surfaceContainerHigh
    }
    val onContainer = if (selected) {
        tokens.brand.completed.onContainer
    } else {
        tokens.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(tokens.radius.small),
        color = container,
        contentColor = onContainer,
        modifier = modifier
            .testTag(AttentionFeedbackTags.SELECTED)
            .padding(tokens.spacing.small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = tokens.spacing.medium, vertical = tokens.spacing.small),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = onContainer,
                    modifier = Modifier
                        .testTag(AttentionFeedbackTags.SELECTED_MARK)
                        .size(tokens.spacing.medium),
                )
            }
            Text(
                text = if (selected) "已选中：$label" else label,
                style = tokens.typography.body,
                color = onContainer,
                modifier = Modifier.testTag(AttentionFeedbackTags.SELECTED_LABEL),
            )
        }
    }
}
