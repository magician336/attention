package com.attention.workspace.state

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 对话消息的角色：用户或 AI。 */
enum class ChatRole { USER, ASSISTANT }

/**
 * 对话页的一条消息。
 *
 * [timestampEpochMillis] 是消息产生的时刻；页面按注入的时区把它格式化成 HH:mm，
 * 因此同一条消息在不同时区显示的是同一时刻的本地时间。
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val timestampEpochMillis: Long,
)

/**
 * AI 对话页的不可变状态。
 *
 * 它只带页面需要的信息：消息列表、输入框内容、是否发送中与失败文案。
 * 输入是否可发送由 [canSend] 判定，页面不自己重复一份「空白 + 发送中」的规则。
 */
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
) {
    /** 空白输入或发送中都不能再发一条。 */
    val canSend: Boolean get() = input.isNotBlank() && !sending
}

/** 发送失败的统一文案：整句可点，点一下就是重试。 */
const val CHAT_SEND_FAILED_MESSAGE: String = "发送失败，点这里重试"

private val CHAT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 把消息时间戳格式化成 HH:mm。时区由调用方注入，默认跟随设备时区。 */
fun chatTimeLabel(timestampEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(timestampEpochMillis).atZone(zone).format(CHAT_TIME_FORMATTER)

/**
 * 重试时要重发的历史：截到最后一条用户消息为止。
 *
 * 失败时最后一条一定是用户消息；不能把某条 AI 回复之后的半截历史当成新的提问，
 * 也不重复追加一条用户消息。没有用户消息时返回 null，页面据此不提供可重试的目标。
 */
fun chatRetryHistory(state: ChatScreenState): List<ChatMessage>? {
    val lastUserIndex: Int = state.messages.indexOfLast { message -> message.role == ChatRole.USER }
    if (lastUserIndex < 0) return null
    return state.messages.take(lastUserIndex + 1)
}
