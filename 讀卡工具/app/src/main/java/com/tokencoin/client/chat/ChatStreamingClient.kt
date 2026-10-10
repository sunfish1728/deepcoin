package com.tokencoin.client.chat

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.Reader
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class HistoryMessage(val role: String, val content: String)
internal data class StreamDelta(
    val content: String = "",
    val reasoning: String = "",
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
)

/** 排除失敗與空白訊息，並合併相鄰同角色訊息（Gemini 要求 user / model 交替）。 */
internal fun buildHistory(messages: List<ChatMessage>): List<HistoryMessage> = messages
    .filter { it.status != MessageStatus.ERROR && it.content.isNotBlank() }
    .map { HistoryMessage(if (it.role == Role.USER) "user" else "assistant", it.content) }
    .fold(mutableListOf<HistoryMessage>()) { acc, m ->
        val last = acc.lastOrNull()
        if (last != null && last.role == m.role) {
            acc[acc.lastIndex] = last.copy(content = last.content + "\n\n" + m.content)
        } else {
            acc += m
        }
        acc
    }

/** SSE 事件可以含多行 data，註解及未知欄位皆忽略。false 表示已收到結束事件。 */
internal fun readSse(reader: Reader, event: (String, String) -> Boolean) {
    var type = ""
    val data = mutableListOf<String>()
    fun dispatch(): Boolean {
        val keepReading = data.isEmpty() || event(type, data.joinToString("\n"))
        type = ""
        data.clear()
        return keepReading
    }
    val lines = reader.buffered()
    while (true) {
        val line = lines.readLine() ?: break
        if (line.isEmpty()) {
            if (!dispatch()) return
        } else if (!line.startsWith(":")) {
            val field = line.substringBefore(':')
            val value = line.substringAfter(':', "").removePrefix(" ")
            when (field) {
                "event" -> type = value
                "data" -> data += value
            }
        }
    }
    dispatch()
}

internal class ChatHttpException(val code: Int, val serverMessage: String) : IOException(serverMessage)
internal class ChatStreamException(message: String) : Exception(message)

internal fun readableError(error: Throwable): String = when (error) {
    is ChatHttpException -> {
        val reason = when (error.code) {
            401, 403 -> "密鑰無效或無權限"
            429 -> "請求過於頻繁或額度不足"
            in 500..599 -> "伺服器暫時無法服務"
            else -> "請求失敗"
        }
        "HTTP ${error.code}：$reason" + error.serverMessage.takeIf { it.isNotBlank() }
            ?.let { "（$it）" }.orEmpty()
    }
    is SocketTimeoutException -> "連線逾時"
    is ChatStreamException -> "伺服器錯誤：${error.message}"
    is IOException -> "無法連線：${error.message ?: "網路連線中斷"}"
    else -> "請求失敗：${error.message ?: "未知錯誤"}"
}

internal class ChatStreamingClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build(),
) {
    fun stream(provider: ProviderConfig, model: String, settings: ChatSettings,
               history: List<HistoryMessage>): Flow<StreamDelta> = callbackFlow {
        val active = AtomicReference<Call?>()
        val cancelled = AtomicBoolean(false)
        fun enqueue(includeUsage: Boolean) {
            val call = client.newCall(request(provider, model, settings, history, includeUsage))
            active.set(call)
            if (cancelled.get()) { call.cancel(); return }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { close(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) {
                                val body = it.body?.string().orEmpty()
                                if (provider.proto == "openai" && includeUsage && it.code == 400 &&
                                    body.contains("stream_options", ignoreCase = true)) {
                                    enqueue(false)
                                    return
                                }
                                throw ChatHttpException(it.code, serverMessage(body))
                            }
                            val body = it.body ?: throw IOException("伺服器未回傳內容")
                            readSse(body.charStream()) { type, data ->
                                // 空白 data（部分中轉站的保活訊號）直接略過
                                if (data == "[DONE]") false else if (data.isBlank()) true else {
                                    val j = JSONObject(data)
                                    if (type == "error" || j.optString("type") == "error" || j.has("error") ||
                                        j.optString("type") == "response.failed" || type == "response.failed") {
                                        val failure = j.optJSONObject("response")?.optJSONObject("error")
                                            ?.optString("message") ?: serverMessage(data)
                                        throw ChatStreamException(failure)
                                    }
                                    parse(provider.proto, type, j)?.let { delta -> trySend(delta) }
                                    type != "response.completed" && j.optString("type") != "response.completed" &&
                                        type != "message_stop" && j.optString("type") != "message_stop"
                                }
                            }
                        }
                        close()
                    } catch (e: Exception) { close(e) }
                }
            })
        }
        try { enqueue(true) } catch (e: Exception) { close(e) }
        awaitClose { cancelled.set(true); active.get()?.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun request(p: ProviderConfig, model: String, s: ChatSettings,
                        history: List<HistoryMessage>, includeUsage: Boolean): Request {
        val base = p.base.trimEnd('/')
        val body = JSONObject().put("model", model).put("stream", true)
        val messages = JSONArray().apply { history.forEach {
            put(JSONObject().put("role", it.role).put("content", it.content))
        } }
        val builder = Request.Builder()
        if (p.proto != "gemini") builder.header("Authorization", "Bearer ${p.key}")
        s.temperature?.let { body.put("temperature", it) }
        val url = when (p.proto) {
            "openai" -> {
                val all = JSONArray()
                if (s.systemPrompt.isNotBlank()) all.put(JSONObject().put("role", "system").put("content", s.systemPrompt))
                history.forEach { all.put(JSONObject().put("role", it.role).put("content", it.content)) }
                body.put("messages", all)
                if (includeUsage) body.put("stream_options", JSONObject().put("include_usage", true))
                s.maxTokens?.let { body.put("max_tokens", it) }
                "$base/chat/completions".toHttpUrl()
            }
            "openai-responses" -> {
                body.put("input", messages)
                if (s.systemPrompt.isNotBlank()) body.put("instructions", s.systemPrompt)
                s.maxTokens?.let { body.put("max_output_tokens", it) }
                "$base/responses".toHttpUrl()
            }
            "anthropic" -> {
                builder.header("x-api-key", p.key).header("anthropic-version", "2023-06-01")
                body.put("messages", messages).put("max_tokens", s.maxTokens ?: 8192)
                if (s.systemPrompt.isNotBlank()) body.put("system", s.systemPrompt)
                "${if (base.endsWith("/v1")) base else "$base/v1"}/messages".toHttpUrl()
            }
            "gemini" -> {
                body.remove("model"); body.remove("stream"); body.remove("temperature")
                body.put("contents", JSONArray().apply { history.forEach {
                    put(JSONObject().put("role", if (it.role == "assistant") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", it.content))))
                } })
                if (s.systemPrompt.isNotBlank()) body.put("systemInstruction", JSONObject()
                    .put("parts", JSONArray().put(JSONObject().put("text", s.systemPrompt))))
                val config = JSONObject()
                s.temperature?.let { config.put("temperature", it) }
                s.maxTokens?.let { config.put("maxOutputTokens", it) }
                if (config.length() > 0) body.put("generationConfig", config)
                base.removeSuffix("/v1beta").removeSuffix("/v1").toHttpUrl().newBuilder()
                    .addPathSegment("v1beta").addPathSegment("models")
                    .addPathSegment("${model.removePrefix("models/")}:streamGenerateContent")
                    .addQueryParameter("alt", "sse").addQueryParameter("key", p.key).build()
            }
            else -> throw IllegalArgumentException("不支援的通訊協定：${p.proto}")
        }
        return builder.url(url).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }

    private fun parse(proto: String, event: String, j: JSONObject): StreamDelta? = when (proto) {
        "openai" -> {
            val d = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
            val u = j.optJSONObject("usage")
            StreamDelta(d?.text("content").orEmpty(),
                d?.text("reasoning_content")?.takeIf { it.isNotEmpty() } ?: d?.text("reasoning").orEmpty(),
                u?.integer("prompt_tokens"), u?.integer("completion_tokens"))
        }
        "openai-responses" -> when (j.optString("type").ifEmpty { event }) {
            "response.output_text.delta" -> StreamDelta(content = j.text("delta"))
            "response.reasoning_summary_text.delta" -> StreamDelta(reasoning = j.text("delta"))
            "response.completed" -> j.optJSONObject("response")?.optJSONObject("usage")?.let {
                StreamDelta(inputTokens = it.integer("input_tokens"), outputTokens = it.integer("output_tokens"))
            }
            else -> null
        }
        "anthropic" -> when (j.optString("type").ifEmpty { event }) {
            "content_block_delta" -> j.optJSONObject("delta")?.let {
                when (it.optString("type")) {
                    "text_delta" -> StreamDelta(content = it.text("text"))
                    "thinking_delta" -> StreamDelta(reasoning = it.text("thinking"))
                    else -> null
                }
            }
            "message_start" -> j.optJSONObject("message")?.optJSONObject("usage")?.let {
                StreamDelta(inputTokens = it.integer("input_tokens"), outputTokens = it.integer("output_tokens"))
            }
            "message_delta" -> j.optJSONObject("usage")?.let {
                StreamDelta(inputTokens = it.integer("input_tokens"), outputTokens = it.integer("output_tokens"))
            }
            else -> null
        }
        "gemini" -> {
            val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val content = StringBuilder()
            val reasoning = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                (if (part.optBoolean("thought")) reasoning else content).append(part.text("text"))
            }
            val u = j.optJSONObject("usageMetadata")
            StreamDelta(content.toString(), reasoning.toString(), u?.integer("promptTokenCount"), u?.integer("candidatesTokenCount"))
        }
        else -> null
    }
}

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
private fun JSONObject.integer(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun serverMessage(body: String): String = runCatching {
    val j = JSONObject(body)
    j.optJSONObject("error")?.text("message")?.takeIf { it.isNotBlank() }
        ?: j.text("message").takeIf { it.isNotBlank() } ?: body
}.getOrDefault(body)
