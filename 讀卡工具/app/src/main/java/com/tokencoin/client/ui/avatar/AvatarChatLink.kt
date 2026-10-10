package com.tokencoin.client.ui.avatar

import com.tokencoin.client.avatar.Emotion
import com.tokencoin.client.avatar.EmotionAnalyzer
import com.tokencoin.client.chat.ChatEngine
import com.tokencoin.client.chat.MessageStatus
import com.tokencoin.client.chat.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 聊天 → 形象連動（AVATAR.md §4）：
 * - 回覆開始、還沒有正文時：思考
 * - 串流正文：依新增的文字動嘴
 * - 完成：依全文推測情緒；失敗：難過；停止：回到平常
 * 只對「親眼看到在串流」的訊息反應，打開舊對話不會觸發。
 */
class AvatarChatLink(chat: ChatEngine, private val host: AvatarHost, private val analyzer: EmotionAnalyzer, scope: CoroutineScope) {
    private var trackedId: String? = null
    private var spoken = 0
    private var thinking = false
    private var talking = false

    init {
        scope.launch {
            chat.current.collect { conv ->
                val m = conv?.messages?.lastOrNull()?.takeIf { it.role == Role.ASSISTANT }
                if (m == null || m.id != trackedId) {
                    // 換了訊息（或換了對話）：前一則若還在動作就收掉
                    reset()
                    if (m == null || m.status != MessageStatus.STREAMING) {
                        trackedId = m?.id
                        spoken = m?.content?.length ?: 0
                        return@collect
                    }
                    trackedId = m.id
                    spoken = 0
                }
                when (m.status) {
                    MessageStatus.STREAMING -> {
                        if (m.content.isEmpty()) {
                            if (!thinking) { host.think(true); thinking = true }
                        } else {
                            if (thinking) { host.think(false); thinking = false }
                            if (!talking) { host.talk(true); talking = true }
                            if (m.content.length > spoken) host.speak(m.content.substring(spoken))
                            spoken = m.content.length
                        }
                    }
                    else -> {
                        val wasActive = thinking || talking
                        reset()
                        if (wasActive) when (m.status) {
                            MessageStatus.DONE -> {
                                val r = analyzer.analyze(m.content)
                                if (r.emotion != Emotion.NEUTRAL) host.emote(r.emotion, r.intensity, 6000)
                            }
                            MessageStatus.ERROR -> host.emote(Emotion.SAD, 0.8f, 4000)
                            else -> {}
                        }
                        spoken = m.content.length
                    }
                }
            }
        }
    }

    private fun reset() {
        if (thinking) host.think(false)
        if (talking) host.talk(false)
        thinking = false
        talking = false
    }
}
