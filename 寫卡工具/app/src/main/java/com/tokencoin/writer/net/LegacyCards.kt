package com.tokencoin.writer.net

import android.content.Context
import com.tokencoin.writer.card.*
import org.json.JSONObject
import java.net.URI

/** 僅遷移已停用的 v2 舊卡，使用本機快取或撤站前備份；不連線、不提供上傳。 */
object LegacyCards {
    private const val LIMIT = 65536
    fun merge(reference: TokenCard, json: String): DecodedCard {
        require(reference.v == 2 && reference.ref.isNotBlank()) { "不是託管卡片" }
        require(json.toByteArray(Charsets.UTF_8).size <= LIMIT) { "託管設定過大" }
        val o = JSONObject(json)
        require(!o.has("key") && !o.has("ref")) { "託管設定不可包含密鑰或另一個託管連結" }
        require(o.optInt("v") == 1 && o.optString("id") == reference.id) { "託管設定與卡片不符" }
        require(o.opt("iat") is Number && o.getDouble("iat") == o.getLong("iat").toDouble()) { "託管設定寫入時間格式錯誤" }
        for (field in listOf("vendor", "provider", "model", "proto", "base")) {
            require(o.optString(field).isNotBlank()) { "託管設定缺少 $field" }
        }
        require(o.optString("proto") in listOf("openai", "openai-responses", "anthropic", "gemini")) { "不支援的 API 協議" }
        val base = URI(o.getString("base"))
        require(base.scheme in listOf("http", "https") && !base.host.isNullOrBlank() && base.userInfo == null) { "API 地址格式錯誤" }
        o.put("key", reference.key)
        return TokenCardCodec.decode(o.toString().toByteArray(Charsets.UTF_8))
    }

    fun resolve(context: Context, decoded: DecodedCard): Pair<DecodedCard, Boolean> {
        val ref = decoded.card
        if (ref.v != 2) return decoded to false
        val cached = context.getSharedPreferences("hosted_cards", Context.MODE_PRIVATE).getString(ref.ref, null)
        if (cached != null) runCatching { merge(ref, cached) }.getOrNull()?.let { return it to true }
        val catalog = context.assets.open("retired-cards.json").bufferedReader().use { JSONObject(it.readText()) }
        val data = catalog.optJSONObject(ref.ref) ?: throw IllegalArgumentException("這張舊託管卡沒有可用備份，請以完整設定重新寫卡")
        return merge(ref, data.toString()) to true
    }
}
