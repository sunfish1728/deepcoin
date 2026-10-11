package com.tokencoin.writer.net

import android.content.Context
import com.tokencoin.writer.BuildConfig
import com.tokencoin.writer.card.DecodedCard
import com.tokencoin.writer.card.TokenCard
import com.tokencoin.writer.card.TokenCardCodec
import com.tokencoin.writer.card.WebCardLink
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.UUID

/** 公開設定的讀寫。只有上傳 GitHub 時才使用共用授權；設定 GET 不帶任何密鑰。 */
object HostedCards {
    const val REPO = "sunfish1728/deepcoin-web-API"
    private const val LIMIT = 65536

    fun publicJson(card: TokenCard): String {
        require(card.ref.isBlank()) { "請先載入完整卡片" }
        return TokenCardCodec.canonicalJson(TokenCardCodec.fields(card) - setOf("key", "sha", "ref"))
    }

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
        WebCardLink.validate(ref.ref)
        val cache = context.getSharedPreferences("hosted_cards", Context.MODE_PRIVATE)
        val body = try { request(ref.ref) } catch (e: Exception) {
            val cached = cache.getString(ref.ref, null) ?: throw e
            return merge(ref, cached) to true
        }
        val full = merge(ref, body)
        cache.edit().putString(ref.ref, body).apply()
        return full to false
    }

    fun upload(card: TokenCard): TokenCard {
        val token = BuildConfig.HOSTING_TOKEN
        require(token.isNotBlank()) { "此 APK 尚未設定共用託管授權" }
        val json = publicJson(card)
        require(json.toByteArray(Charsets.UTF_8).size <= LIMIT) { "託管設定過大" }
        val path = "cards/${UUID.randomUUID()}.json"
        val body = JSONObject().put("message", "Add hosted card ${card.id}")
            .put("content", Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)))
            .put("branch", "main").toString()
        request("https://api.github.com/repos/$REPO/contents/$path", body, token)
        val url = "https://raw.githubusercontent.com/$REPO/main/$path"
        val ref = card.copy(v = 2, ref = url)
        // 隨機檔名避免覆寫及舊快取。下載核對成功後才將此連結交給寫卡流程。
        var failure: Exception? = null
        repeat(6) {
            try {
                val downloaded = request(url)
                merge(ref, downloaded)
                require(TokenCardCodec.canonicalJson(JSONObject(downloaded).let { o ->
                    TokenCardCodec.fields(TokenCardCodec.decode(o.put("key", card.key).toString().toByteArray(Charsets.UTF_8)).card) - "key"
                }) == json) { "託管內容核對失敗" }
                return ref
            } catch (e: Exception) { failure = e; Thread.sleep(1500) }
        }
        throw IllegalStateException("已上傳，但公開設定尚未能確認，請重試", failure)
    }

    private fun request(url: String, body: String? = null, token: String? = null): String {
        WebCardLink.validate(url)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "DeepCoin")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.requestMethod = "PUT"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            require(code in 200..299) { if (token != null) "GitHub 上傳失敗（HTTP $code），請檢查共用授權" else "託管設定讀取失敗（HTTP $code）" }
            val bytes = connection.inputStream.use { it.readBytesLimited(LIMIT) }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            require(out.size() + n <= limit) { "託管回應過大" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
