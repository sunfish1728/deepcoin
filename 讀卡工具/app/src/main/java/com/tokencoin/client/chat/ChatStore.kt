package com.tokencoin.client.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 每個對話獨立存檔；不儲存卡片密鑰。呼叫端負責在 IO 執行緒操作。 */
class ChatStore(private val dir: File) {
    fun load(): List<Conversation> = dir.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .mapNotNull { file -> runCatching {
            decode(JSONObject(file.readText(Charsets.UTF_8))).also {
                require(file.name == "${it.id}.json")
                path(it.id)
            }
        }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun save(conversation: Conversation) {
        check(dir.isDirectory || dir.mkdirs()) { "無法建立聊天儲存目錄" }
        val destination = path(conversation.id)
        val temp = File.createTempFile("chat-", ".tmp", dir)
        try {
            temp.outputStream().use { stream ->
                stream.write(encode(conversation).toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temp.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    fun delete(id: String) {
        Files.deleteIfExists(path(id).toPath())
    }

    private fun path(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "無效的對話 ID" }
        return File(dir, "$id.json")
    }

    private fun encode(c: Conversation) = JSONObject().apply {
        put("id", c.id); put("title", c.title); put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt); put("cardId", c.cardId); put("model", c.model)
        put("settings", JSONObject().apply {
            put("systemPrompt", c.settings.systemPrompt)
            put("temperature", c.settings.temperature ?: JSONObject.NULL)
            put("maxTokens", c.settings.maxTokens ?: JSONObject.NULL)
        })
        put("messages", JSONArray().apply { c.messages.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id); put("role", m.role.name); put("content", m.content)
                put("reasoning", m.reasoning); put("model", m.model)
                put("createdAt", m.createdAt); put("status", m.status.name)
                put("error", m.error ?: JSONObject.NULL)
                put("durationMs", m.durationMs ?: JSONObject.NULL)
                put("usage", m.usage?.let {
                    JSONObject().put("input", it.input).put("output", it.output)
                } ?: JSONObject.NULL)
            })
        } })
    }

    private fun decode(j: JSONObject): Conversation {
        val s = j.getJSONObject("settings")
        val messages = j.getJSONArray("messages")
        return Conversation(j.getString("id"), j.getString("title"), j.getLong("createdAt"),
            j.getLong("updatedAt"), j.getString("cardId"), j.getString("model"),
            ChatSettings(s.getString("systemPrompt"),
                if (s.isNull("temperature")) null else s.getDouble("temperature"),
                if (s.isNull("maxTokens")) null else s.getInt("maxTokens")),
            List(messages.length()) { index ->
                val m = messages.getJSONObject(index)
                val status = MessageStatus.valueOf(m.getString("status"))
                ChatMessage(m.getString("id"), Role.valueOf(m.getString("role")),
                    m.getString("content"), m.optString("reasoning"), m.optString("model"),
                    m.getLong("createdAt"),
                    if (status == MessageStatus.STREAMING) MessageStatus.STOPPED else status,
                    if (m.isNull("error")) null else m.getString("error"),
                    m.optJSONObject("usage")?.let { TokenUsage(it.getInt("input"), it.getInt("output")) },
                    if (m.isNull("durationMs")) null else m.getLong("durationMs"))
            })
    }
}
