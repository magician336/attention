package com.attention.app.chat

import com.attention.workspace.state.ChatMessage

/**
 * AI 对话服务的唯一契约。页面与状态只依赖它，mock 与未来的真实实现都从这里替换。
 *
 * [history] 是截至本次请求的全部消息，最后一条是刚发出的用户消息。
 */
interface AiChatService {
    suspend fun reply(history: List<ChatMessage>): String
}
