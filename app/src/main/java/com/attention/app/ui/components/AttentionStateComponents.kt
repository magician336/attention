package com.attention.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.attention.app.ui.theme.attentionTokens

/**
 * 加载中：页面正在取数，用户看到统一的加载表达而不是空白。
 */
@Composable
fun AttentionLoadingState(
    label: String = AttentionStateKind.LOADING.label,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.LOADING,
        label = label,
        description = description,
        actionLabel = null,
        onAction = null,
        modifier = modifier,
    )
}

/**
 * 成功：操作已经达成，仍然带图标与文案，不靠颜色宣布结果。
 */
@Composable
fun AttentionSuccessState(
    label: String = AttentionStateKind.SUCCESS.label,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.SUCCESS,
        label = label,
        description = description,
        actionLabel = null,
        onAction = null,
        modifier = modifier,
    )
}

/**
 * 错误：读取失败时的统一表达，并给用户自己恢复的入口。
 *
 * [onRetry] 为空时不渲染重试按钮——但状态本身仍然可见，用户不会卡在空白上。
 */
@Composable
fun AttentionErrorState(
    label: String = AttentionStateKind.ERROR.label,
    description: String? = "没能读到数据，你可以重试一次。",
    retryLabel: String = "重试",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.ERROR,
        label = label,
        description = description,
        actionLabel = retryLabel.takeIf { onRetry != null },
        onAction = onRetry,
        modifier = modifier,
    )
}

/**
 * 空数据：没有内容时给出明确的下一步，而不是让用户自己猜。
 *
 * [onNextStep] 为空时仍会说明下一步是什么，只是不提供可点击入口。
 */
@Composable
fun AttentionEmptyState(
    label: String = AttentionStateKind.EMPTY.label,
    description: String? = "现在还没有内容。",
    nextStepLabel: String = "添加第一条",
    onNextStep: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.EMPTY,
        label = label,
        description = description,
        actionLabel = nextStepLabel.takeIf { onNextStep != null },
        onAction = onNextStep,
        modifier = modifier,
    )
}

/**
 * 权限未开启：说明权限影响什么，以及是否必须开启。
 *
 * [required] 为真时说明必须开启，为假时说明可以先跳过。
 */
@Composable
fun AttentionPermissionState(
    label: String = AttentionStateKind.PERMISSION_NOT_GRANTED.label,
    description: String? = null,
    required: Boolean = true,
    actionLabel: String = if (required) "去开启" else "知道了",
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.PERMISSION_NOT_GRANTED,
        label = label,
        description = description
            ?: if (required) {
                "没有通知权限，计时结束与应用提醒不会送达。开启后才能收到提醒。"
            } else {
                "没有通知权限不会影响计时与记录，只是收不到提醒。你可以先继续使用。"
            },
        actionLabel = actionLabel.takeIf { onAction != null },
        onAction = onAction,
        modifier = modifier,
    )
}

/**
 * 本地数据说明：让用户理解数据去向，避免误以为已经上云。
 *
 * 这是常驻说明而不是一次性状态，因此没有动作入口。
 */
@Composable
fun AttentionLocalDataNotice(
    label: String = AttentionStateKind.LOCAL_DATA_NOTICE.label,
    description: String = "你的日程条目、实际投入与时间记录都只保存在这台设备上，" +
        "我们不会上传，也不会同步到其他设备。卸载应用会一并删除这些数据。",
    modifier: Modifier = Modifier,
) {
    AttentionStateBody(
        kind = AttentionStateKind.LOCAL_DATA_NOTICE,
        label = label,
        description = description,
        actionLabel = null,
        onAction = null,
        modifier = modifier,
    )
}

/**
 * 六种状态共用的容器。
 *
 * 六个组件全部委托到这里，因此它们天然共用同一套标识、同一套排版与同一套取色方式，
 * 同一个状态在任何页面渲染都得到同一个标识。
 */
@Composable
private fun AttentionStateBody(
    kind: AttentionStateKind,
    label: String,
    description: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    val accent = when (kind.role) {
        AttentionStateRole.NEUTRAL -> tokens.colorScheme.onSurfaceVariant
        AttentionStateRole.POSITIVE -> tokens.brand.completed.accent
        AttentionStateRole.NEGATIVE -> tokens.brand.error.accent
        AttentionStateRole.INFORMATIONAL -> tokens.brand.scheduled.accent
    }

    Column(
        modifier = modifier
            .testTag(kind.rootTag)
            .padding(tokens.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Icon(
            imageVector = kind.icon,
            // 图标同时朗读语义标签：颜色不可用时，状态依然能被听懂。
            contentDescription = label,
            tint = accent,
            modifier = Modifier
                .testTag(kind.iconTag)
                .size(tokens.spacing.large),
        )
        Text(
            text = label,
            style = tokens.typography.title,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(kind.labelTag),
        )
        if (description != null) {
            Text(
                text = description,
                style = tokens.typography.body,
                color = tokens.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(kind.descriptionTag),
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag(kind.actionTag),
            ) {
                Text(text = actionLabel, style = tokens.typography.body, color = accent)
            }
        }
    }
}
