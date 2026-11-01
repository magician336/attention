package com.attention.workspace.state

/**
 * AI 对话页的 intent 接口。页面只发出 intent，真正的挂起调用、取消与时间戳生成
 * 由 app 模块里的实现完成；workspace-state 因此不依赖协程，也不触达 Android API。
 */
interface ChatActions {
    /** 更新输入框内容。 */
    fun updateInput(value: String)

    /** 发送当前输入；空白或发送中时必须被忽略。 */
    fun send()

    /** 重发最后一条用户消息并重新等待回复。 */
    fun retryLast()

    /** 清空整段对话，并取消还在等待的回复。 */
    fun clear()
}
