package com.attention.app.ui.components

/**
 * 反馈类组件的稳定标识。
 *
 * 界面测试按语义定位这些标识，不依赖脆弱的文案或组件层级；页面接入时也不再各自发明定位方式。
 */
object AttentionFeedbackTags {
    /** 反馈宿主的标识。 */
    const val HOST: String = "attention.feedback.host"

    /** 短时提示本身的标识。 */
    const val SNACKBAR: String = "attention.feedback.snackbar"

    /** 短时提示文案的标识。 */
    const val SNACKBAR_MESSAGE: String = "attention.feedback.snackbar.message"

    /** 短时提示的撤销入口，没有撤销动作时不存在。 */
    const val SNACKBAR_UNDO: String = "attention.feedback.snackbar.undo"

    /** 内联错误的标识。 */
    const val INLINE_ERROR: String = "attention.feedback.inline_error"

    /** 内联错误文案的标识。 */
    const val INLINE_ERROR_MESSAGE: String = "attention.feedback.inline_error.message"

    /** 内联错误的就地重试入口，没有重试时不存在。 */
    const val INLINE_ERROR_RETRY: String = "attention.feedback.inline_error.retry"

    /** 确认弹窗的标识。 */
    const val CONFIRM_DIALOG: String = "attention.feedback.confirm_dialog"

    /** 确认弹窗的影响说明标识。 */
    const val CONFIRM_DIALOG_IMPACT: String = "attention.feedback.confirm_dialog.impact"

    /** 确认弹窗的确认按钮。 */
    const val CONFIRM_DIALOG_CONFIRM: String = "attention.feedback.confirm_dialog.confirm"

    /** 确认弹窗的取消按钮。 */
    const val CONFIRM_DIALOG_DISMISS: String = "attention.feedback.confirm_dialog.dismiss"

    /** 禁用态操作的标识。 */
    const val DISABLED_ACTION: String = "attention.feedback.disabled_action"

    /** 禁用原因说明的标识。 */
    const val DISABLED_ACTION_REASON: String = "attention.feedback.disabled_action.reason"

    /** 选中态的标识。 */
    const val SELECTED: String = "attention.feedback.selected"

    /** 选中标记的标识，未选中时不存在。 */
    const val SELECTED_MARK: String = "attention.feedback.selected.mark"

    /** 选中态文案的标识。 */
    const val SELECTED_LABEL: String = "attention.feedback.selected.label"
}
