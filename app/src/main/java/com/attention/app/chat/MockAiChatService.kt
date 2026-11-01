package com.attention.app.chat

import com.attention.workspace.state.ChatMessage
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 本地 mock：延迟 800ms 模拟网络，然后返回一句带当前时间的假回复。
 *
 * 固定 10% 概率抛异常，用来验证页面的失败与重试路径；接真实实现时整个类替换掉即可，
 * 页面与 [AiChatService] 的契约都不变。构造参数保留给测试固定失败率与时间。
 */
class MockAiChatService(
    private val failureRate: Double = 0.1,
    private val random: Random = Random.Default,
    private val now: () -> LocalTime = { LocalTime.now() },
) : AiChatService {
    override suspend fun reply(history: List<ChatMessage>): String {
        delay(800)
        if (random.nextDouble() < failureRate) {
            throw IllegalStateException("模拟网络失败")
        }
        val time: String = now().format(REPLY_TIME_FORMATTER)
        return "收到你的消息了，现在是 $time。这是模拟回复，接入真实 AI 后会返回模型生成的答案。"
    }

    private companion object {
        val REPLY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
