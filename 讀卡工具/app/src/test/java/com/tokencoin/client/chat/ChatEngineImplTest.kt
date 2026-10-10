package com.tokencoin.client.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class ChatEngineImplTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var store: ChatStore
    private lateinit var engine: ChatEngineImpl

    @Before fun setup() {
        server = MockWebServer(); server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = ChatStore(temp.newFolder())
        engine = ChatEngineImpl(store, scope)
    }
    @After fun tearDown() { scope.cancel(); server.shutdown() }
    private fun provider() = ProviderConfig("card", "test", "openai", server.url("/v1").toString(),
        "key", "default", listOf("default", "other"), "")
    private fun reply(content: String) = MockResponse().setHeader("Content-Type", "text/event-stream")
        .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"$content\"}}]}\n\n" +
            "data: {\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3}}\n\ndata: [DONE]\n\n")
    private suspend fun await(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(10)
    }
    private suspend fun prepare() {
        engine.setProvider(provider()); engine.newConversation()
        await { engine.current.value != null }
    }

    @Test fun sendPersistsUsageDurationAndAutoTitleThenInheritsSettings() = runBlocking {
        assertNull(engine.current.value)
        prepare()
        engine.setModel("other")
        engine.updateSettings(ChatSettings("系統", 0.4, 99))
        await { engine.current.value?.model == "other" && engine.current.value?.settings?.maxTokens == 99 }
        assertTrue(store.load().isEmpty())
        server.enqueue(reply("回答")); server.enqueue(reply("「測試， 標題！」"))
        engine.send("你好")
        await { engine.current.value?.title == "測試標題" }
        val c = engine.current.value!!
        assertEquals(2, c.messages.size)
        assertEquals(MessageStatus.DONE, c.messages.last().status)
        assertEquals("other", c.messages.last().model)
        assertEquals(TokenUsage(2, 3), c.messages.last().usage)
        assertNotNull(c.messages.last().durationMs)
        await { store.load().singleOrNull()?.title == "測試標題" }
        assertFalse(engine.generating.value)
        assertEquals(c.id, engine.conversations.value.single().id)
        val req = server.takeRequest(); val body = JSONObject(req.body.readUtf8())
        assertEquals("other", body.getString("model"))
        assertEquals(2, body.getJSONArray("messages").length())
        val title = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("other", title.getString("model"))
        engine.newConversation(); await { engine.current.value?.id != c.id }
        assertEquals(c.settings, engine.current.value!!.settings)
        assertEquals("default", engine.current.value!!.model)
        assertEquals("card", engine.current.value!!.cardId)
        assertEquals(1, store.load().size)
        engine.open(c.id); await { engine.current.value?.id == c.id }
        engine.delete(c.id); await { engine.current.value?.id != c.id }
        assertTrue(engine.current.value!!.messages.isEmpty())
        assertTrue(engine.conversations.value.isEmpty())
        assertTrue(store.load().isEmpty())
    }

    @Test fun stopKeepsPartialAndIgnoresDuplicateSend() = runBlocking {
        prepare()
        engine.rename(engine.current.value!!.id, "手動標題")
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"部分\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"結尾\"}}]}\n\ndata: [DONE]\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(body).throttleBody(body.substring(0, body.indexOf("data:", 6))
                .toByteArray(Charsets.UTF_8).size.toLong(), 2, TimeUnit.SECONDS))
        engine.send("問題"); engine.send("不應送出")
        await { engine.current.value?.messages?.lastOrNull()?.content == "部分" }
        assertTrue(engine.generating.value)
        engine.stop()
        await { engine.current.value?.messages?.lastOrNull()?.status == MessageStatus.STOPPED && !engine.generating.value }
        val c = engine.current.value!!
        assertEquals("部分", c.messages.last().content)
        assertEquals(2, c.messages.size)
        assertFalse(engine.generating.value)
        await { store.load().singleOrNull()?.messages?.lastOrNull()?.status == MessageStatus.STOPPED }
        assertEquals(1, server.requestCount)
    }

    @Test fun errorThenRegenerateAndEditResendTruncateHistory() = runBlocking {
        prepare()
        engine.rename(engine.current.value!!.id, "自訂")
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"bad key\"}"))
        engine.send("第一問")
        await { engine.current.value?.messages?.lastOrNull()?.status == MessageStatus.ERROR }
        assertEquals("HTTP 401：密鑰無效或無權限（bad key）", engine.current.value!!.messages.last().error)
        server.takeRequest()
        val assistantId = engine.current.value!!.messages.last().id
        server.enqueue(reply("重試")); engine.regenerate(assistantId)
        await { engine.current.value?.messages?.lastOrNull()?.content == "重試" && !engine.generating.value }
        val regenerated = engine.current.value!!
        assertEquals(2, regenerated.messages.size)
        assertEquals(1, JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages").length())
        server.enqueue(reply("第二答")); engine.send("第二問")
        await { engine.current.value?.messages?.size == 4 && !engine.generating.value }
        server.takeRequest()
        server.enqueue(reply("改答")); engine.editAndResend(regenerated.messages.first().id, "修改問題")
        await { engine.current.value?.messages?.lastOrNull()?.content == "改答" && !engine.generating.value }
        assertEquals(2, engine.current.value!!.messages.size)
        val history = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(1, history.length()); assertEquals("修改問題", history.getJSONObject(0).getString("content"))
        engine.deleteMessage(engine.current.value!!.messages.last().id)
        await { engine.current.value?.messages?.size == 1 }
    }

    @Test fun missingProviderEmitsErrorAndBlankSendDoesNothing() = runBlocking {
        engine.send("  ")
        assertNull(engine.current.value)
        var error: String? = null
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { error = engine.errors.first() }
        engine.send("你好")
        await { error != null }
        assertEquals("尚未刷入鯨幣卡片", error)
        assertNull(engine.current.value); assertFalse(engine.generating.value)
        collector.cancel()
    }

    @Test fun loadingHasSortedSummariesAndNoCurrentConversation() = runBlocking {
        val recoveredStore = ChatStore(temp.newFolder())
        val c = Conversation("loaded", "已有對話", 1, 2, "card", "model", ChatSettings(),
            listOf(ChatMessage("a", Role.ASSISTANT, "中斷", createdAt = 1, status = MessageStatus.STREAMING)))
        recoveredStore.save(c); recoveredStore.save(c.copy(id = "newer", updatedAt = 3))
        val recovered = ChatEngineImpl(recoveredStore, scope)
        await { recovered.conversations.value.size == 2 }
        assertEquals(listOf("newer", "loaded"), recovered.conversations.value.map { it.id })
        assertNull(recovered.current.value)
        recovered.open("loaded"); await { recovered.current.value != null }
        assertEquals(MessageStatus.STOPPED, recovered.current.value!!.messages.single().status)
    }

    @Test fun autoTitleFailureFallsBackWithoutHoldingGeneratingState() = runBlocking {
        prepare()
        server.enqueue(reply("完成"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("title failed")
            .setHeadersDelay(400, TimeUnit.MILLISECONDS))
        val question = "這是一個超過十六個字的使用者問題用來確認標題回退"
        engine.send(question)
        await { engine.current.value?.messages?.lastOrNull()?.content == "完成" && !engine.generating.value }
        assertEquals("新對話", engine.current.value!!.title)
        await { engine.current.value?.title == question.take(16) }
        assertEquals(2, server.requestCount)
    }
}
