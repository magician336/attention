/*
 * Gradle 依赖（Kotlin DSL）：
 *   implementation("com.squareup.okhttp3:okhttp:4.12.0")
 *   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 *   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
 * 并在 plugins {} 中启用 kotlin("plugin.serialization")。
 */

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.example.reasoner

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 一条对话消息。role 使用 OpenAI 约定的 "system" / "user" / "assistant"。
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun system(content: String): ChatMessage = ChatMessage("system", content)
        fun user(content: String): ChatMessage = ChatMessage("user", content)
        fun assistant(content: String): ChatMessage = ChatMessage("assistant", content)
    }
}

/**
 * 端点配置。
 *
 * @param baseUrl 兼容端点前缀，需包含版本段，例如 "https://api.openai.com/v1"、
 *   "https://api.deepseek.com/v1"；也允许直接传入完整的 ".../chat/completions"。
 * @param model 模型名，例如 "gpt-4o-mini"、"deepseek-reasoner"。
 * @param apiKey 密钥；本地服务（Ollama / LM Studio）可传任意非空字符串。
 * @param connectTimeoutMillis 建连超时，0 表示不限制。
 * @param readTimeoutMillis 单次读超时，0 表示不限制。
 * @param writeTimeoutMillis 单次写超时，0 表示不限制。
 * @param callTimeoutMillis 整次调用（DNS + 建连 + 写 + 读）的总超时，0 表示不限制。
 * @param maxTokens 可选的最大生成 token 数（请求体中的 max_tokens）。
 * @param temperature 可选的采样温度；为 null 时不发送该字段，由服务端决定。
 * @param extraHeaders 额外请求头，例如 OpenRouter 的 "HTTP-Referer"。
 */
data class OpenAiCompatConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val connectTimeoutMillis: Long = 10_000L,
    val readTimeoutMillis: Long = 120_000L,
    val writeTimeoutMillis: Long = 30_000L,
    val callTimeoutMillis: Long = 180_000L,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
)

/**
 * 调用结果。成功与失败都用普通值返回，调用方不需要捕获网络异常。
 */
sealed interface ReasonerResult {

    /**
     * @param content 助手回复正文；推理模型可能为空字符串。
     * @param reasoningContent 推理模型的思维链（DeepSeek 的 reasoning_content 等），可能为 null。
     * @param model 服务端实际使用的模型名。
     * @param finishReason 结束原因，例如 "stop" / "length" / "content_filter"。
     * @param usage token 用量。
     */
    data class Success(
        val content: String,
        val reasoningContent: String? = null,
        val model: String? = null,
        val finishReason: String? = null,
        val usage: TokenUsage? = null,
    ) : ReasonerResult

    data class Failed(val error: ReasonerError) : ReasonerResult {
        val message: String get() = error.message ?: "请求失败"
    }
}

/** token 用量。 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

/**
 * 失败原因。调用方可以用 when 做穷尽分支。
 */
sealed class ReasonerError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 该错误通常可以通过重试解决。 */
    open val retryable: Boolean = false

    /** 本地配置不合法，例如 model / baseUrl 为空或 baseUrl 无法解析。 */
    class InvalidConfig(message: String) : ReasonerError(message)

    /** 没有提供 apiKey。 */
    class MissingApiKey(
        message: String = "apiKey 为空：请设置 OpenAiCompatConfig.apiKey 或 OPENAI_API_KEY 环境变量",
    ) : ReasonerError(message)

    /** 本次请求参数不合法，例如消息列表为空。 */
    class InvalidRequest(message: String) : ReasonerError(message)

    /** 建连、读写或整体调用超时。 */
    class Timeout(message: String, cause: Throwable? = null) : ReasonerError(message, cause) {
        override val retryable: Boolean = true
    }

    /** DNS 失败、连接被拒、断网、TLS 握手失败等网络层错误。 */
    class Network(message: String, cause: Throwable? = null) : ReasonerError(message, cause) {
        override val retryable: Boolean = true
    }

    /**
     * 服务端返回非 2xx。
     *
     * @param statusCode HTTP 状态码。
     * @param apiErrorCode 服务端返回的错误码（若可解析）。
     * @param responseBody 截断后的原始响应体，便于排查。
     */
    class Http(
        val statusCode: Int,
        message: String,
        val apiErrorCode: String? = null,
        val responseBody: String? = null,
    ) : ReasonerError(message) {
        override val retryable: Boolean
            get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }

    /** 响应不是预期的 JSON 结构。 */
    class Decoding(message: String, cause: Throwable? = null) : ReasonerError(message, cause)

    /** 请求成功，但模型没有返回任何可用内容。 */
    class EmptyResponse(message: String) : ReasonerError(message)
}

/**
 * 基于 OkHttp + kotlinx.serialization 的 OpenAI 兼容 Chat Completions 客户端（非流式）。
 *
 * 所有可预期的失败（配置错误、网络异常、超时、非 2xx、解析失败、空响应）都会以
 * [ReasonerResult.Failed] 返回；协程被取消时则正常抛出 CancellationException。
 *
 * 用法：
 * ```
 * val reasoner = OpenAiCompatReasoner.fromEnvironment()
 * when (val result = reasoner.reason("用一句话解释熵增", systemPrompt = "你是一位物理老师")) {
 *     is ReasonerResult.Success -> println(result.content)
 *     is ReasonerResult.Failed -> println("失败：${result.message}（可重试=${result.error.retryable}）")
 * }
 * ```
 *
 * @param config 端点、模型、密钥与超时配置。
 * @param httpClient 可复用外部 OkHttpClient；为 null 时按 [config] 的超时新建。
 * @param json 自定义 Json；默认忽略未知字段且不输出 null 字段。
 */
class OpenAiCompatReasoner(
    private val config: OpenAiCompatConfig,
    httpClient: OkHttpClient? = null,
    private val json: Json = DEFAULT_JSON,
) {
    private val client: OkHttpClient = httpClient ?: defaultClient(config)

    /**
     * 便捷入口：按 system / history / user 组装消息后发起请求。
     *
     * @param prompt 本轮用户输入；为空时只发送 history。
     * @param systemPrompt 可选的系统提示词。
     * @param history 之前的对话消息。
     * @param maxTokens 覆盖 [OpenAiCompatConfig.maxTokens]。
     * @param temperature 覆盖 [OpenAiCompatConfig.temperature]。
     */
    suspend fun reason(
        prompt: String,
        systemPrompt: String? = null,
        history: List<ChatMessage> = emptyList(),
        maxTokens: Int? = config.maxTokens,
        temperature: Double? = config.temperature,
    ): ReasonerResult {
        currentCoroutineContext().ensureActive()

        val messages = buildList {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { add(ChatMessage.system(it)) }
            addAll(history)
            if (prompt.isNotBlank()) add(ChatMessage.user(prompt))
        }

        if (messages.isEmpty()) {
            return ReasonerResult.Failed(
                ReasonerError.InvalidRequest("prompt、systemPrompt、history 不能同时为空"),
            )
        }

        return complete(messages, maxTokens, temperature)
    }

    /**
     * 直接发送完整的消息列表。
     *
     * @param messages 至少包含一条消息。
     * @param maxTokens 覆盖 [OpenAiCompatConfig.maxTokens]。
     * @param temperature 覆盖 [OpenAiCompatConfig.temperature]。
     */
    suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int? = config.maxTokens,
        temperature: Double? = config.temperature,
    ): ReasonerResult {
        currentCoroutineContext().ensureActive()

        if (messages.isEmpty()) {
            return ReasonerResult.Failed(ReasonerError.InvalidRequest("messages 不能为空"))
        }

        validateConfig()?.let { return ReasonerResult.Failed(it) }

        val endpoint = chatCompletionsUrl()
            ?: return ReasonerResult.Failed(
                ReasonerError.InvalidConfig("baseUrl 不是合法的 HTTP(S) 地址：\"${config.baseUrl}\""),
            )

        val payload = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
        )

        val requestJson = try {
            json.encodeToString(ChatCompletionRequest.serializer(), payload)
        } catch (e: IllegalArgumentException) {
            return ReasonerResult.Failed(
                ReasonerError.Decoding("构造请求 JSON 失败：${e.message}", e),
            )
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .apply {
                config.extraHeaders.forEach { (name, value) -> header(name, value) }
            }
            .build()

        // 网络层：超时与普通 IO 失败分开归类。
        val response = try {
            execute(client.newCall(request))
        } catch (e: SocketTimeoutException) {
            return ReasonerResult.Failed(
                ReasonerError.Timeout("请求超时（连接或读取）：${e.message ?: "timeout"}", e),
            )
        } catch (e: InterruptedIOException) {
            return ReasonerResult.Failed(
                ReasonerError.Timeout("请求被整体超时中断：${e.message ?: "timeout"}", e),
            )
        } catch (e: IOException) {
            return ReasonerResult.Failed(
                ReasonerError.Network("网络请求失败：${e.message ?: e.javaClass.simpleName}", e),
            )
        }

        return response.use { resp ->
            val bodyText = try {
                resp.body?.string().orEmpty()
            } catch (e: SocketTimeoutException) {
                return@use ReasonerResult.Failed(ReasonerError.Timeout("读取响应超时", e))
            } catch (e: IOException) {
                return@use ReasonerResult.Failed(
                    ReasonerError.Network("读取响应失败：${e.message ?: "unknown"}", e),
                )
            }

            if (resp.isSuccessful) {
                parseSuccess(bodyText)
            } else {
                ReasonerResult.Failed(parseHttpError(resp.code, resp.message, bodyText))
            }
        }
    }

    /** 把配置问题映射成 Failed，而不是在构造阶段抛异常。 */
    private fun validateConfig(): ReasonerError? {
        if (config.apiKey.isBlank()) return ReasonerError.MissingApiKey()
        if (config.model.isBlank()) return ReasonerError.InvalidConfig("model 不能为空")
        if (config.baseUrl.isBlank()) return ReasonerError.InvalidConfig("baseUrl 不能为空")
        return null
    }

    /** 兼容 baseUrl 带不带尾部斜杠、以及是否已包含 /chat/completions。 */
    private fun chatCompletionsUrl(): HttpUrl? {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        return url.toHttpUrlOrNull()
    }

    private fun parseHttpError(
        statusCode: Int,
        statusMessage: String,
        bodyText: String,
    ): ReasonerError.Http {
        val envelope = runCatching {
            json.decodeFromString(ApiErrorEnvelope.serializer(), bodyText)
        }.getOrNull()

        val apiMessage = (envelope?.error?.message ?: envelope?.message)
            ?.trim()
            .orEmpty()

        val message = if (apiMessage.isNotEmpty()) {
            "HTTP $statusCode: $apiMessage"
        } else {
            "HTTP $statusCode ${statusMessage.ifBlank { "请求失败" }}".trim()
        }

        return ReasonerError.Http(
            statusCode = statusCode,
            message = message,
            apiErrorCode = envelope?.error?.code?.takeIf { it.isNotBlank() },
            responseBody = bodyText.take(MAX_ERROR_BODY_CHARS).ifBlank { null },
        )
    }

    private fun parseSuccess(bodyText: String): ReasonerResult {
        val decoded = try {
            json.decodeFromString(ChatCompletionResponse.serializer(), bodyText)
        } catch (e: SerializationException) {
            return ReasonerResult.Failed(
                ReasonerError.Decoding("无法解析响应 JSON：${e.message}", e),
            )
        } catch (e: IllegalArgumentException) {
            return ReasonerResult.Failed(
                ReasonerError.Decoding("响应格式不合法：${e.message}", e),
            )
        }

        val choice = decoded.choices.firstOrNull()
            ?: return ReasonerResult.Failed(
                ReasonerError.EmptyResponse("响应中没有 choices"),
            )

        val message = choice.message
            ?: return ReasonerResult.Failed(
                ReasonerError.EmptyResponse(
                    "响应缺少 message（finish_reason=${choice.finishReason ?: "unknown"}）",
                ),
            )

        val content = message.content.orEmpty()
        val reasoning = message.reasoningContent?.takeIf { it.isNotBlank() }
            ?: message.reasoning?.takeIf { it.isNotBlank() }

        // 推理模型在 max_tokens 耗尽时可能只有 reasoning_content，此时仍算成功。
        if (content.isBlank() && reasoning == null) {
            return ReasonerResult.Failed(
                ReasonerError.EmptyResponse(
                    "模型返回了空内容（finish_reason=${choice.finishReason ?: "unknown"}）",
                ),
            )
        }

        return ReasonerResult.Success(
            content = content,
            reasoningContent = reasoning,
            model = decoded.model ?: config.model,
            finishReason = choice.finishReason,
            usage = decoded.usage?.let {
                TokenUsage(
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens,
                )
            },
        )
    }

    /**
     * 用 suspendCancellableCoroutine 包装 OkHttp 的异步调用：
     * 协程取消时会同步取消底层 Call，避免请求悬挂。
     */
    private suspend fun execute(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val token = continuation.tryResumeWithException(e) ?: return
                    continuation.completeResume(token)
                }

                override fun onResponse(call: Call, response: Response) {
                    val token = continuation.tryResume(response)
                    if (token == null) {
                        response.close()
                    } else {
                        continuation.completeResume(token)
                    }
                }
            })
        }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.openai.com/v1"
        const val ENV_BASE_URL: String = "OPENAI_BASE_URL"
        const val ENV_MODEL: String = "OPENAI_MODEL"
        const val ENV_API_KEY: String = "OPENAI_API_KEY"

        /**
         * 从环境变量读取配置并创建实例：
         * OPENAI_BASE_URL（缺省为 [DEFAULT_BASE_URL]）、OPENAI_MODEL、OPENAI_API_KEY。
         * 缺失 model / apiKey 不会在这里抛异常，而是在调用时返回 Failed。
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OpenAiCompatReasoner {
            val config = OpenAiCompatConfig(
                baseUrl = env[ENV_BASE_URL]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL,
                model = env[ENV_MODEL]?.trim().orEmpty(),
                apiKey = env[ENV_API_KEY]?.trim().orEmpty(),
            )
            return OpenAiCompatReasoner(config)
        }

        /** 按配置中的超时构建 OkHttpClient；需要共享连接池时可自行构建后传入构造器。 */
        fun defaultClient(config: OpenAiCompatConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .callTimeout(config.callTimeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}

private const val MAX_ERROR_BODY_CHARS: Int = 4_096

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val DEFAULT_JSON: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

@Serializable
private data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    @Serializable
    data class Choice(
        val index: Int = 0,
        val message: ResponseMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class ResponseMessage(
        val role: String? = null,
        val content: String? = null,
        @SerialName("reasoning_content") val reasoningContent: String? = null,
        val reasoning: String? = null,
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0,
        @SerialName("total_tokens") val totalTokens: Int = 0,
    )
}

/**
 * 兼容两种错误体：{"error":{"message":...}} 与 {"message":...}。
 */
@Serializable
private data class ApiErrorEnvelope(
    val error: ApiError? = null,
    val message: String? = null,
) {
    @Serializable
    data class ApiError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null,
    )
}