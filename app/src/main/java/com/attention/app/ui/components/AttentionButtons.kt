package com.attention.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.attention.app.ui.theme.attentionTokens

/**
 * 触控目标的最小边长：Material 与 Android 无障碍基线都是 48dp。
 */
val AttentionTouchTargetMinSize: Dp = 48.dp

/**
 * 把控件撑到 48dp 最小触控尺寸。
 *
 * 之所以要显式声明：Material 3 的 Button / OutlinedButton / TextButton 都把内容高度固定在
 * 40dp，并且**不**响应 `LocalMinimumInteractiveComponentEnforcement`。本仓库实测过，
 * 在主题里打开强制约束后按钮仍然是 40dp，因此这一层只能由页面自己声明。
 */
fun Modifier.attentionTouchTarget(): Modifier = heightIn(min = AttentionTouchTargetMinSize)

/**
 * 主操作按钮：Material 3 Button 的品牌接入版，强制 48dp 最小触控高度。
 *
 * 页面统一用它而不是直接用 [Button]，这样触控尺寸由构造方式保证，新增按钮不会漏掉。
 */
@Composable
fun AttentionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = attentionTokens()
    Button(
        onClick = onClick,
        modifier = modifier.attentionTouchTarget(),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.brand.timingInProgress.accent,
            contentColor = tokens.colorScheme.onPrimary,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * 次要操作按钮：Material 3 OutlinedButton 的品牌接入版，强制 48dp 最小触控高度。
 */
@Composable
fun AttentionOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = attentionTokens()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.attentionTouchTarget(),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = tokens.brand.timingInProgress.accent,
        ),
        border = BorderStroke(1.dp, tokens.colorScheme.outlineVariant),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * 轻量操作入口：Material 3 TextButton 的品牌接入版，同样强制 48dp 最小触控高度。
 */
@Composable
fun AttentionTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = attentionTokens()
    TextButton(
        onClick = onClick,
        modifier = modifier.attentionTouchTarget(),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = tokens.brand.timingInProgress.accent,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}
