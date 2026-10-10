package com.tokencoin.client.chat

import com.tokencoin.client.card.Presets
import com.tokencoin.client.card.TokenCard
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * 聊天系統的「前後端契約」。
 * - 前端（ui/chat/）只依賴本檔的型別與 [ChatEngine] 介面。
 * - 後端實作 [ChatEngine]，並提供 `fun createChatEngine(context: Context, scope: CoroutineScope): ChatEngine`
 *   （放在 ChatEngineImpl.kt）。
 * 修改本檔需同時更新前後端。
 */

enum class Role { USER, ASSISTANT }

enum class MessageStatus {
    /** 正常完成 */
    DONE,
    /** 串流中（只會出現在最後一則助手訊息） */
    STREAMING,
    /** 請求失敗，[ChatMessage.error] 有原因 */
    ERROR,
    /** 使用者中途停止，保留已收到的內容 */
    STOPPED,
}

data class TokenUsage(val input: Int, val output: Int)

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    /** 推理 / 思考內容（DeepSeek reasoning_content、Claude thinking、Gemini thought 等） */
    val reasoning: String = "",
    /** 產生此訊息的模型（助手訊息才有） */
    val model: String = "",
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.DONE,
    val error: String? = null,
    val usage: TokenUsage? = null,
    /** 從送出到完成的耗時（毫秒） */
    val durationMs: Long? = null,
)

data class ChatSettings(
    val systemPrompt: String = "",
    /** null 表示不送，使用供應商預設 */
    val temperature: Double? = null,
    /** null 表示不送（Anthropic 必填時後端自行補預設值） */
    val maxTokens: Int? = null,
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 建立對話時使用的卡片 ID */
    val cardId: String,
    val model: String,
    val settings: ChatSettings,
    val messages: List<ChatMessage>,
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val model: String,
    /** 最後一則訊息的前段文字 */
    val preview: String,
    val messageCount: Int,
)

/** 由目前刷入的卡片轉換而來的 API 設定。 */
data class ProviderConfig(
    val cardId: String,
    val providerName: String,
    /** openai / openai-responses / anthropic / gemini */
    val proto: String,
    val base: String,
    val key: String,
    val defaultModel: String,
    /** 預設模型 + 其他可用模型，去重 */
    val models: List<String>,
    /** 角色名稱，可為空 */
    val characterName: String,
) {
    companion object {
        fun fromCard(c: TokenCard) = ProviderConfig(
            cardId = c.id,
            providerName = c.provider,
            proto = c.proto,
            base = c.base,
            key = c.key,
            defaultModel = c.model,
            // 「*」表示 API 的所有模型，由 ClientState 讀卡後向 API 取得清單再補進來
            models = (listOf(c.model) + c.models).filter { it.isNotBlank() && it != Presets.ALL_MODELS }.distinct(),
            characterName = c.char,
        )
    }
}

/**
 * UI 面對的聊天引擎。所有方法皆可在主執行緒呼叫且立即返回，耗時工作由實作在自己的 scope 處理。
 * 狀態一律透過 StateFlow 推送。
 */
interface ChatEngine {
    /** 所有已儲存對話，依 updatedAt 由新到舊 */
    val conversations: StateFlow<List<ConversationSummary>>

    /** 目前開啟的對話；null 表示尚未開啟任何對話 */
    val current: StateFlow<Conversation?>

    /** 是否正在產生回覆 */
    val generating: StateFlow<Boolean>

    /** 目前的 API 設定；null 表示沒有卡片，無法送出 */
    val provider: StateFlow<ProviderConfig?>

    /** 一次性錯誤訊息（例如沒有卡片時送出、存檔失敗），UI 以 Snackbar 顯示 */
    val errors: SharedFlow<String>

    fun setProvider(config: ProviderConfig?)

    /** 開新對話：建立一個尚未存檔的空對話，模型為 provider 預設模型，第一次送出時才存檔 */
    fun newConversation()

    fun open(id: String)

    /** 送出使用者訊息並開始串流回覆。generating 中呼叫會被忽略 */
    fun send(text: String)

    /** 停止目前的串流，已收到的內容保留，狀態設為 STOPPED */
    fun stop()

    /** 重新產生指定的助手訊息：刪除它與其後的訊息，用之前的歷史重新請求 */
    fun regenerate(assistantMessageId: String)

    /** 修改使用者訊息：刪除其後所有訊息，以新內容重新送出 */
    fun editAndResend(userMessageId: String, newText: String)

    fun deleteMessage(id: String)

    fun setModel(model: String)

    fun updateSettings(settings: ChatSettings)

    fun rename(id: String, title: String)

    fun delete(id: String)
}
