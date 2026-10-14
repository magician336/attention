package com.attention.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.attention.app.ui.theme.attentionTokens

/**
 * 反馈宿主状态：页面用它发出短时提示，而不是用打断式弹窗。
 *
 * [showUndoableMessage] 把撤销和它要恢复的事情绑在一起：用户点撤销，[onUndo] 就真的执行，
 * 页面不需要自己再记一份“待恢复”的状态。
 */
@Stable
class AttentionFeedbackHostState internal constructor(internal val host: SnackbarHostState) {

    /** 当前提示的文案，由发起提示的一方写入，宿主只负责渲染。 */
    internal var message: String? by mutableStateOf(null)
        private set

    /** 当前提示的撤销入口文案，没有撤销入口时为 null。 */
    internal var undoLabel: String? by mutableStateOf(null)
        private set

    /** 用户是否点了撤销。提示结束后据此决定要不要执行恢复。 */
    private var undoRequested: Boolean by mutableStateOf(false)

    /** 由撤销按钮调用：记录用户确实想撤销。 */
    internal fun requestUndo() {
        undoRequested = true
    }

    /**
     * 发出短时提示。
     *
     * 操作结果走这里而不是弹窗，流程不被打断。
     */
    suspend fun showMessage(message: String): SnackbarResult {
        this.message = message
        undoLabel = null
        val result = host.showSnackbar(message = message)
        this.message = null
        return result
    }

    /**
     * 发出带撤销的短时提示。
     *
     * 用户点撤销时执行 [onUndo]，因此误删的时间记录可以当场找回。
     */
    suspend fun showUndoableMessage(
        message: String,
        undoLabel: String = "撤销",
        onUndo: suspend () -> Unit,
    ): SnackbarResult {
        this.message = message
        this.undoLabel = undoLabel
        undoRequested = false

        val result = host.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            // 撤销需要留出阅读与反应的时间，短提示的时长不够。
            duration = SnackbarDuration.Long,
        )

        // 撤销按钮自己记录了用户意图；宿主返回的结果作为兜底，两者一致才执行恢复。
        val performed = undoRequested || result == SnackbarResult.ActionPerformed
        this.message = null
        this.undoLabel = null
        if (performed) {
            onUndo()
        }
        return result
    }
}

/** 取得反馈宿主状态。页面在顶层创建一次并向下传递。 */
@Composable
fun rememberAttentionFeedbackHostState(): AttentionFeedbackHostState =
    remember { AttentionFeedbackHostState(SnackbarHostState()) }

/**
 * 反馈宿主：把页面内容与短时提示挂在一起。
 *
 * 页面接入时用它包住内容，提示就统一从底部出现，用户不需要学习两套反馈方式。
 */
@Composable
fun AttentionFeedbackHost(
    state: AttentionFeedbackHostState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = attentionTokens()
    Box(
        modifier = modifier
            .testTag(AttentionFeedbackTags.HOST)
            .padding(tokens.spacing.medium),
    ) {
        content()
        AttentionSnackbarHost(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 短时提示本体，样式取自品牌令牌，撤销入口始终带文字，不只靠颜色。 */
@Composable
fun AttentionSnackbarHost(
    state: AttentionFeedbackHostState,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    SnackbarHost(
        hostState = state.host,
        modifier = modifier,
        snackbar = { data ->
            val undoLabel = state.undoLabel
            Snackbar(
                modifier = Modifier.testTag(AttentionFeedbackTags.SNACKBAR),
                shape = RoundedCornerShape(tokens.radius.medium),
                containerColor = tokens.colorScheme.inverseSurface,
                contentColor = tokens.colorScheme.inverseOnSurface,
                actionContentColor = tokens.colorScheme.inversePrimary,
                action = {
                    if (undoLabel != null) {
                        TextButton(
                            onClick = {
                                state.requestUndo()
                                data.dismiss()
                            },
                            modifier = Modifier.testTag(AttentionFeedbackTags.SNACKBAR_UNDO),
                        ) {
                            Text(
                                text = undoLabel,
                                style = tokens.typography.body,
                                color = tokens.colorScheme.inversePrimary,
                            )
                        }
                    }
                },
            ) {
                Text(
                    text = state.message.orEmpty(),
                    style = tokens.typography.body,
                    color = tokens.colorScheme.inverseOnSurface,
                    modifier = Modifier.testTag(AttentionFeedbackTags.SNACKBAR_MESSAGE),
                )
            }
        },
    )
}
