package com.tokencoin.writer.card

import org.json.JSONArray
import org.json.JSONObject

/**
 * Token幣 卡片資料格式 v1（詳見 SPEC.md）。
 *
 * 卡片上存一筆 NDEF MIME 記錄 [MIME]，內容是「正規化 JSON」：
 * key 依字典序排列、無空白、空值欄位省略。舊版頂層 `sha` 讀取時忽略。
 */
data class TokenCard(
    val v: Int = VERSION,
    /** 卡片 ID（隨機 8 位十六進位） */
    val id: String,
    /** 卡片名稱，例如「DeepSeek娘幣」 */
    val title: String = "",
    /** AI 模型供應商（模型原廠），例如 deepseek / openai / anthropic */
    val vendor: String,
    /** 預設模型 */
    val model: String,
    /** 其他可用模型 */
    val models: List<String> = emptyList(),
    /** API 供應商名稱，例如「DeepSeek 官方」「OpenRouter」 */
    val provider: String,
    /** API 協議：openai / openai-responses / anthropic / gemini */
    val proto: String,
    /** API 供應商地址（Base URL） */
    val base: String,
    /** API 密鑰 */
    val key: String,
    /** 電腦端建議套用的工具：claude / codex / gemini */
    val apps: List<String> = emptyList(),
    /** 寫入時間（Unix 秒） */
    val iat: Long,
    /** 到期時間（Unix 秒），null 表示不限 */
    val exp: Long? = null,
    /** 美元餘額（十進位字串，例如 "5.00"），寫入當下的參考值 */
    val balanceUsd: String = "",
    /** 虛擬形象：角色名稱 */
    val char: String = "",
    /** 虛擬形象：角色 ID */
    val charId: String = "",
    /** 虛擬形象：形象資源 URL */
    val avatar: String = "",
    /** 備註 */
    val note: String = "",
    /** 未知欄位（向前相容，原樣保留，舊版 sha 除外） */
    val extra: Map<String, Any> = emptyMap(),
) {
    companion object {
        const val VERSION = 1
        const val MIME = "application/vnd.tokencoin+json"
    }
}

data class DecodedCard(
    val card: TokenCard,
    val canonicalJson: String,
)

object TokenCardCodec {

    private val KNOWN = setOf(
        "v", "id", "title", "vendor", "model", "models", "provider", "proto", "base", "key",
        "apps", "iat", "exp", "balance_usd", "char", "char_id", "avatar", "note", "sha",
    )

    /** 不含 sha 的欄位表，空值省略。 */
    fun fields(c: TokenCard): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m.putAll(c.extra - "sha")
        m["v"] = c.v.toLong()
        fun str(k: String, s: String) { if (s.isNotBlank()) m[k] = s.trim() }
        fun list(k: String, l: List<String>) {
            val clean = l.map { it.trim() }.filter { it.isNotEmpty() }
            if (clean.isNotEmpty()) m[k] = clean
        }
        str("id", c.id)
        str("title", c.title)
        str("vendor", c.vendor)
        str("model", c.model)
        list("models", c.models)
        str("provider", c.provider)
        str("proto", c.proto)
        str("base", c.base)
        str("key", c.key)
        list("apps", c.apps)
        m["iat"] = c.iat
        c.exp?.let { m["exp"] = it }
        str("balance_usd", c.balanceUsd)
        str("char", c.char)
        str("char_id", c.charId)
        str("avatar", c.avatar)
        str("note", c.note)
        return m
    }

    /** 卡片 payload：正規化 JSON 的 UTF-8 位元組，不含舊版 sha。 */
    fun encode(c: TokenCard): ByteArray = canonicalJson(fields(c)).toByteArray(Charsets.UTF_8)

    fun decode(payload: ByteArray): DecodedCard {
        val text = payload.toString(Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val raw = fromJson(JSONObject(text)) as Map<String, Any>
        val noSha = raw - "sha"

        fun s(k: String) = noSha[k] as? String ?: ""
        fun l(k: String) = (noSha[k] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        fun n(k: String) = (noSha[k] as? Number)?.toLong()

        val card = TokenCard(
            v = n("v")?.toInt() ?: 0,
            id = s("id"), title = s("title"), vendor = s("vendor"), model = s("model"),
            models = l("models"), provider = s("provider"), proto = s("proto"),
            base = s("base"), key = s("key"), apps = l("apps"),
            iat = n("iat") ?: 0L, exp = n("exp"), balanceUsd = s("balance_usd"),
            char = s("char"), charId = s("char_id"), avatar = s("avatar"), note = s("note"),
            extra = noSha.filterKeys { it !in KNOWN },
        )
        return DecodedCard(card, canonicalJson(noSha))
    }

    // ---- 正規化 JSON ----

    fun canonicalJson(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value)
            is Number -> {
                val d = value.toDouble()
                require(d == Math.floor(d) && !d.isInfinite()) { "Token幣格式只允許整數" }
                sb.append(d.toLong())
            }
            is Map<*, *> -> {
                sb.append('{')
                value.keys.map { it as String }.sorted().forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, value[k])
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, e -> if (i > 0) sb.append(','); write(sb, e) }
                sb.append(']')
            }
            else -> error("不支援的型別：${value::class.java}")
        }
    }

    /** 只跳脫 " \ 與控制字元，其餘（含中文、/）原樣輸出。 */
    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    private fun fromJson(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { fromJson(v.get(it)) }
            .filterValues { it != null }
        is JSONArray -> (0 until v.length()).map { fromJson(v.get(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}
