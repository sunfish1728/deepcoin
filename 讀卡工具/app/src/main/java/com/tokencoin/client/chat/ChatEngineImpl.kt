package com.tokencoin.client.chat

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

fun createChatEngine(context: Context, scope: CoroutineScope): ChatEngine =
    ChatEngineImpl(ChatStore(File(context.filesDir, "chat")), scope)

/** 所有狀態與磁碟操作由同一個佇列處理，串流只將事件送回佇列。 */
class ChatEngineImpl(private val store: ChatStore, private val scope: CoroutineScope) : ChatEngine {
    private val client = ChatStreamingClient()
    private val conversationState = MutableStateFlow<List<ConversationSummary>>(emptyList())
    private val currentState = MutableStateFlow<Conversation?>(null)
    private val generatingState = MutableStateFlow(false)
    private val providerState = MutableStateFlow<ProviderConfig?>(null)
    private val errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val conversations = conversationState.asStateFlow()
    override val current = currentState.asStateFlow()
    override val generating = generatingState.asStateFlow()
    override val provider = providerState.asStateFlow()
    override val errors = errorEvents.asSharedFlow()

    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val saved = mutableMapOf<String, Conversation>()
    private var active: Generation? = null

    private class Generation(val conversationId: String, val message: ChatMessage) {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val started = System.nanoTime()
        var dirty = false
        var input: Int? = null
        var output: Int? = null
        var job: Job? = null
        var ticker: Job? = null
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                store.load().forEach { saved[it.id] = it }
                publishSummaries()
            } catch (e: Exception) { report("讀取對話失敗：${e.message}") }
            try {
                for (command in commands) {
                    try { command() } catch (e: Exception) {
                        report("操作失敗：${e.message}")
                    }
                }
            } finally {
                commands.close()
                active?.job?.cancel()
            }
        }
    }

    private fun submit(action: () -> Unit) { commands.trySend(action) }
    private fun report(message: String) { errorEvents.tryEmit(message) }
    private fun now() = System.currentTimeMillis()
    private fun id() = UUID.randomUUID().toString()

    override fun setProvider(config: ProviderConfig?) = submit { providerState.value = config }
    override fun newConversation() = submit { stopActive(); fresh() }

    private fun fresh() {
        val p = providerState.value
        val time = now()
        currentState.value = Conversation(id(), "新對話", time, time, p?.cardId.orEmpty(),
            p?.defaultModel.orEmpty(), saved.values.maxByOrNull { it.updatedAt }?.settings ?: ChatSettings(), emptyList())
    }

    override fun open(id: String) = submit {
        if (saved.containsKey(id)) {
            stopActive()
            currentState.value = saved[id]
        }
    }

    override fun send(text: String) = submit {
        if (text.isNotBlank() && active == null) {
            val p = requireProvider() ?: return@submit
            if (currentState.value == null) fresh()
            val c = currentState.value!!
            val user = ChatMessage(id(), Role.USER, text, createdAt = now())
            change(c.copy(messages = c.messages + user, updatedAt = now()), persist = true)
            start(p)
        }
    }

    private fun requireProvider(): ProviderConfig? = providerState.value.also {
        if (it == null) report("尚未刷入鯨幣卡片")
    }

    private fun start(p: ProviderConfig) {
        val c = currentState.value ?: return
        val model = c.model.ifBlank { p.defaultModel }
        val message = ChatMessage(id(), Role.ASSISTANT, "", model = model,
            createdAt = now(), status = MessageStatus.STREAMING)
        val g = Generation(c.id, message)
        active = g
        generatingState.value = true
        change(c.copy(model = model, messages = c.messages + message, updatedAt = now()), persist = true)
        g.ticker = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(50)
                submit {
                    if (active === g && g.dirty) {
                        emitMessage(g, MessageStatus.STREAMING)
                        g.dirty = false
                    }
                }
            }
        }
        g.job = scope.launch(Dispatchers.IO) {
            try {
                client.stream(p, model, c.settings, buildHistory(c.messages)).collect { delta ->
                    submit {
                        if (active === g) {
                            g.content.append(delta.content)
                            g.reasoning.append(delta.reasoning)
                            delta.inputTokens?.let { g.input = it }
                            delta.outputTokens?.let { g.output = it }
                            g.dirty = true
                        }
                    }
                }
                submit {
                    if (active === g) {
                        finish(g, MessageStatus.DONE)
                        val completed = saved[g.conversationId]
                        if (completed != null && completed.title == "新對話" &&
                            completed.messages.firstOrNull { it.role == Role.ASSISTANT && it.status == MessageStatus.DONE }?.id == message.id) {
                            autoTitle(completed, p, model, message.id)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submit { if (active === g) finish(g, MessageStatus.ERROR, readableError(e)) }
            }
        }
    }

    private fun emitMessage(g: Generation, status: MessageStatus, error: String? = null) {
        val c = currentState.value?.takeIf { it.id == g.conversationId } ?: return
        val final = status != MessageStatus.STREAMING
        val message = g.message.copy(content = g.content.toString(), reasoning = g.reasoning.toString(),
            status = status, error = error,
            usage = if (g.input != null || g.output != null) TokenUsage(g.input ?: 0, g.output ?: 0) else null,
            durationMs = if (final) (System.nanoTime() - g.started) / 1_000_000 else null)
        change(c.copy(messages = c.messages.map { if (it.id == message.id) message else it },
            updatedAt = now()), persist = final)
    }

    private fun finish(g: Generation, status: MessageStatus, error: String? = null) {
        emitMessage(g, status, error)
        g.ticker?.cancel()
        active = null
        generatingState.value = false
    }

    override fun stop() = submit { stopActive() }
    private fun stopActive() {
        active?.let {
            it.job?.cancel()
            finish(it, MessageStatus.STOPPED)
        }
    }

    override fun regenerate(assistantMessageId: String) = submit {
        val c = currentState.value ?: return@submit
        val index = c.messages.indexOfFirst { it.id == assistantMessageId && it.role == Role.ASSISTANT }
        if (index < 0) return@submit
        val p = requireProvider() ?: return@submit
        stopActive()
        change(c.copy(messages = c.messages.take(index), updatedAt = now()), persist = true)
        start(p)
    }

    override fun editAndResend(userMessageId: String, newText: String) = submit {
        if (newText.isBlank()) return@submit
        val c = currentState.value ?: return@submit
        val index = c.messages.indexOfFirst { it.id == userMessageId && it.role == Role.USER }
        if (index < 0) return@submit
        val p = requireProvider() ?: return@submit
        stopActive()
        val user = ChatMessage(id(), Role.USER, newText, createdAt = now())
        change(c.copy(messages = c.messages.take(index) + user, updatedAt = now()), persist = true)
        start(p)
    }

    override fun deleteMessage(id: String) = submit {
        if (currentState.value?.messages?.none { it.id == id } != false) return@submit
        stopActive()
        currentState.value?.let { change(it.copy(messages = it.messages.filterNot { m -> m.id == id }, updatedAt = now())) }
    }
    override fun setModel(model: String) = submit {
        currentState.value?.let { change(it.copy(model = model, updatedAt = now())) }
    }
    override fun updateSettings(settings: ChatSettings) = submit {
        currentState.value?.let { change(it.copy(settings = settings, updatedAt = now())) }
    }
    override fun rename(id: String, title: String) = submit {
        val c = currentState.value?.takeIf { it.id == id } ?: saved[id] ?: return@submit
        change(c.copy(title = title, updatedAt = now()))
    }
    override fun delete(id: String) = submit {
        val isCurrent = currentState.value?.id == id
        if (isCurrent) stopActive()
        try {
            store.delete(id)
            saved.remove(id)
            publishSummaries()
            if (isCurrent) fresh()
        } catch (e: Exception) { report("刪除對話失敗：${e.message}") }
    }

    private fun change(c: Conversation, persist: Boolean = saved.containsKey(c.id)) {
        if (currentState.value?.id == c.id) currentState.value = c
        if (persist) {
            saved[c.id] = c
            publishSummaries()
            try { store.save(c) } catch (e: Exception) { report("儲存對話失敗：${e.message}") }
        }
    }

    private fun publishSummaries() {
        conversationState.value = saved.values.sortedByDescending { it.updatedAt }.map {
            ConversationSummary(it.id, it.title, it.updatedAt, it.model,
                it.messages.lastOrNull()?.content.orEmpty().take(80), it.messages.size)
        }
    }

    private fun autoTitle(c: Conversation, p: ProviderConfig, model: String, assistantId: String) {
        scope.launch(Dispatchers.IO) {
            val title = StringBuilder()
            // 把對話整理成單一使用者訊息；若以助手訊息結尾，Anthropic 會當成預填而續寫回答
            val transcript = buildHistory(c.messages).joinToString("\n") {
                (if (it.role == "user") "使用者：" else "助手：") + it.content.take(400)
            }
            try {
                client.stream(p, model, ChatSettings(maxTokens = 64), listOf(HistoryMessage("user",
                    "為下面這段對話取一個標題，使用對話的語言，中文不超過 12 個字、英文不超過 6 個詞。" +
                        "只輸出標題本身，不要引號或標點。\n\n$transcript"))).collect { title.append(it.content) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { title.clear() }
            val cleaned = title.toString().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                .replace(Regex("[\\p{P}&&[^-]]"), "").replace(Regex("\\s+"), " ")
                // 中日文字旁的空白去掉，英文詞之間保留
                .replace(Regex("(?<=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]) | (?=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}])"), "")
                .trim().take(24)
            val result = cleaned.ifEmpty { c.messages.firstOrNull { it.role == Role.USER }?.content.orEmpty().take(16) }
            submit {
                val latest = currentState.value?.takeIf { it.id == c.id } ?: saved[c.id]
                if (latest != null && latest.title == "新對話" && result.isNotBlank() &&
                    latest.messages.any { it.id == assistantId && it.status == MessageStatus.DONE }) {
                    change(latest.copy(title = result, updatedAt = now()))
                }
            }
        }
    }
}
