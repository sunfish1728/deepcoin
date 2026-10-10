package com.tokencoin.client.chat

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.StringReader

class ChatStreamingClientTest {
    private lateinit var server: MockWebServer
    private val history = listOf(HistoryMessage("user", "你好"), HistoryMessage("assistant", "您好"))
    private val settings = ChatSettings("系統提示", 0.3, 123)

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }
    private fun provider(proto: String, suffix: String = "") = ProviderConfig(
        "card", "test", proto, server.url(suffix).toString(), "secret&key", "model", listOf("model"), "")
    private fun response(sse: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse)
    private fun event(data: String, type: String = "") =
        (if (type.isEmpty()) "" else "event: $type\n") + "data: $data\n\n"
    private fun stream(proto: String, suffix: String = "") = runBlocking {
        ChatStreamingClient().stream(provider(proto, suffix), "model", settings, history).toList()
    }

    @Test fun sseHandlesCommentsMultilineCrLfAndFinalUnterminatedEvent() {
        val events = mutableListOf<Pair<String, String>>()
        readSse(StringReader(": comment\r\nevent: test\r\nid: 1\r\ndata: first\r\ndata: second\r\n\r\ndata:last")) { t, d ->
            events += t to d; true
        }
        assertEquals(listOf("test" to "first\nsecond", "" to "last"), events)
        events.clear()
        readSse(StringReader("data: [DONE]\n\ndata: ignored\n\n")) { t, d -> events += t to d; false }
        assertEquals(1, events.size)
    }

    @Test fun openAiContentReasoningUsageAndRequest() {
        server.enqueue(response(event("""{"choices":[{"delta":{"content":"你好","reasoning_content":"思考"}}]}""") +
            event("""{"choices":[{"delta":{"reasoning":"更多"}}]}""") +
            event("""{"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":9}}""") + event("[DONE]")))
        val deltas = stream("openai", "v1/")
        assertEquals("你好", deltas.joinToString("") { it.content })
        assertEquals("思考更多", deltas.joinToString("") { it.reasoning })
        assertEquals(7, deltas.last().inputTokens); assertEquals(9, deltas.last().outputTokens)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer secret&key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("stream")); assertTrue(body.getJSONObject("stream_options").getBoolean("include_usage"))
        assertEquals("model", body.getString("model")); assertEquals(123, body.getInt("max_tokens"))
        assertEquals(0.3, body.getDouble("temperature"), 0.0)
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals("你好", body.getJSONArray("messages").getJSONObject(1).getString("content"))
    }

    @Test fun openAiRetriesOnlyStreamOptionsRejection() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"unsupported stream_options"}}"""))
        server.enqueue(response(event("""{"choices":[{"delta":{"content":"ok"}}]}""") + event("[DONE]")))
        assertEquals("ok", stream("openai").joinToString("") { it.content })
        val first = JSONObject(server.takeRequest().body.readUtf8())
        val second = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(first.has("stream_options")); assertFalse(second.has("stream_options"))
        first.remove("stream_options")
        assertTrue(first.similar(second))
    }

    @Test fun responsesContentReasoningUsageAndRequest() {
        server.enqueue(response(event("""{"delta":"回答"}""", "response.output_text.delta") +
            event("""{"type":"response.reasoning_summary_text.delta","delta":"推理"}""") +
            event("""{"response":{"usage":{"input_tokens":11,"output_tokens":12}}}""", "response.completed")))
        val deltas = stream("openai-responses", "v1")
        assertEquals("回答", deltas.joinToString("") { it.content }); assertEquals("推理", deltas.joinToString("") { it.reasoning })
        assertEquals(11, deltas.last().inputTokens); assertEquals(12, deltas.last().outputTokens)
        val req = server.takeRequest(); assertEquals("/v1/responses", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("系統提示", body.getString("instructions")); assertEquals(123, body.getInt("max_output_tokens"))
        assertEquals(2, body.getJSONArray("input").length())
        assertFalse(body.has("stream_options"))
    }

    @Test fun anthropicContentThinkingUsageHeadersAndVersionPath() {
        server.enqueue(response(event("""{"type":"message_start","message":{"usage":{"input_tokens":20,"output_tokens":0}}}""") +
            event("""{"delta":{"type":"text_delta","text":"文字"}}""", "content_block_delta") +
            event("""{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"思考"}}""") +
            event("""{"type":"message_delta","usage":{"output_tokens":30}}""") +
            event("""{"type":"message_stop"}""")))
        val deltas = stream("anthropic", "v1/")
        assertEquals("文字", deltas.joinToString("") { it.content }); assertEquals("思考", deltas.joinToString("") { it.reasoning })
        assertEquals(20, deltas.first().inputTokens); assertEquals(30, deltas.last().outputTokens)
        val req = server.takeRequest(); assertEquals("/v1/messages", req.path)
        assertEquals("secret&key", req.getHeader("x-api-key")); assertEquals("2023-06-01", req.getHeader("anthropic-version"))
        assertEquals("Bearer secret&key", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("系統提示", body.getString("system")); assertEquals(123, body.getInt("max_tokens"))
    }

    @Test fun geminiContentThoughtUsageEncodedKeyAndRequest() {
        server.enqueue(response(event("""{"candidates":[{"content":{"parts":[{"text":"想法","thought":true},{"text":"內容"}]}}],"usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":8}}""")))
        val delta = stream("gemini", "v1beta/").single()
        assertEquals("內容", delta.content); assertEquals("想法", delta.reasoning)
        assertEquals(4, delta.inputTokens); assertEquals(8, delta.outputTokens)
        val req = server.takeRequest()
        assertEquals("/v1beta/models/model:streamGenerateContent", req.requestUrl!!.encodedPath)
        assertEquals("secret&key", req.requestUrl!!.queryParameter("key"))
        assertNull(req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("model", body.getJSONArray("contents").getJSONObject(1).getString("role"))
        assertEquals("系統提示", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals(123, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
        assertFalse(body.has("stream"))
    }

    @Test fun optionalSettingsOmittedAndAnthropicMaxTokensDefault() = runBlocking {
        for (proto in listOf("openai", "openai-responses", "anthropic", "gemini")) {
            server.enqueue(response(event("[DONE]")))
            ChatStreamingClient().stream(provider(proto), "model", ChatSettings(), history).toList()
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertFalse(body.has("temperature")); assertFalse(body.has("instructions")); assertFalse(body.has("system"))
            assertFalse(body.has("systemInstruction")); assertFalse(body.has("generationConfig"))
            if (proto == "anthropic") assertEquals(8192, body.getInt("max_tokens")) else assertFalse(body.has("max_tokens"))
        }
    }

    @Test fun httpErrorAndStreamErrorsPropagate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid key"}}"""))
        val failure = runCatching { stream("openai") }.exceptionOrNull()!!
        assertEquals("HTTP 401：密鑰無效或無權限（invalid key）", readableError(failure))
        for ((proto, data) in listOf(
            "openai-responses" to """{"type":"response.failed","response":{"error":{"message":"failed response"}}}""",
            "anthropic" to """{"type":"error","error":{"message":"overloaded"}}""")) {
            server.enqueue(response(event(data)))
            assertNotNull(runCatching { stream(proto) }.exceptionOrNull())
        }
        assertEquals("連線逾時", readableError(java.net.SocketTimeoutException()))
    }

    @Test fun historyExcludesErrorsEmptyContentAndReasoning() {
        val messages = listOf(
            ChatMessage("1", Role.USER, "問題", createdAt = 1),
            ChatMessage("2", Role.ASSISTANT, "失敗", createdAt = 2, status = MessageStatus.ERROR),
            ChatMessage("3", Role.ASSISTANT, "", reasoning = "秘密", createdAt = 3),
            ChatMessage("4", Role.ASSISTANT, "部分", reasoning = "隱藏", createdAt = 4, status = MessageStatus.STOPPED),
            ChatMessage("5", Role.USER, "  ", createdAt = 5))
        assertEquals(listOf(HistoryMessage("user", "問題"), HistoryMessage("assistant", "部分")), buildHistory(messages))
    }

    @Test fun historyMergesConsecutiveSameRoleMessages() {
        // 中間的失敗回覆被排除後，兩則使用者訊息相鄰，需合併以符合 Gemini 的交替要求
        val messages = listOf(
            ChatMessage("1", Role.USER, "第一次", createdAt = 1),
            ChatMessage("2", Role.ASSISTANT, "失敗", createdAt = 2, status = MessageStatus.ERROR),
            ChatMessage("3", Role.USER, "第二次", createdAt = 3))
        assertEquals(listOf(HistoryMessage("user", "第一次\n\n第二次")), buildHistory(messages))
    }

    @Test fun blankSseDataIsIgnoredInStream() {
        server.enqueue(response("data: \n\n" + event("""{"choices":[{"delta":{"content":"ok"}}]}""") + event("[DONE]")))
        assertEquals("ok", stream("openai").joinToString("") { it.content })
    }
}
