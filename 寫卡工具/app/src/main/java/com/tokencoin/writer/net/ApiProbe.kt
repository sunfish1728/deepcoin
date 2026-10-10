package com.tokencoin.writer.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Balance(val currency: String, val amount: String, val label: String)

data class ProbeResult(
    val models: List<String>,
    val modelsError: String?,
    val balances: List<Balance>,
    /** 偵測到的到期時間（Unix 秒），例如 new-api 的 access_until */
    val expiresAt: Long?,
    val balanceError: String?,
)

/**
 * 依 API 協議查模型列表，依網域或通用中轉（new-api / one-api）介面查餘額。
 * 全部是阻塞呼叫，請在 IO 執行緒使用。
 */
object ApiProbe {

    fun probe(proto: String, base: String, key: String): ProbeResult {
        val b = base.trim().trimEnd('/')
        val models = runCatching { listModels(proto, b, key) }
        val balance = runCatching { queryBalance(b, key) }
        return ProbeResult(
            models = models.getOrDefault(emptyList()),
            modelsError = models.exceptionOrNull()?.let(::describe),
            balances = balance.getOrNull()?.first.orEmpty(),
            expiresAt = balance.getOrNull()?.second,
            balanceError = balance.exceptionOrNull()?.let(::describe),
        )
    }

    // ---- 模型列表 ----

    fun listModels(proto: String, base: String, key: String): List<String> = when (proto) {
        "anthropic" -> {
            val root = if (base.endsWith("/v1")) base else "$base/v1"
            val o = getJson("$root/models?limit=1000", anthropicHeaders(key))
            ids(o, "data", "id")
        }
        "gemini" -> {
            val root = base.removeSuffix("/v1beta").removeSuffix("/v1")
            val o = getJson("$root/v1beta/models?pageSize=1000&key=${enc(key)}", emptyMap())
            ids(o, "models", "name").map { it.removePrefix("models/") }
        }
        else -> {
            // OpenAI 相容：先試 {base}/models，失敗再試 {base}/v1/models
            val urls = buildList {
                add("$base/models")
                if (!base.endsWith("/v1")) add("$base/v1/models")
            }
            firstSuccess(urls) { ids(getJson(it, bearer(key)), "data", "id") }
        }
    }.distinct().sorted()

    // ---- 餘額 ----

    /** 回傳（餘額列表, 到期時間） */
    fun queryBalance(base: String, key: String): Pair<List<Balance>, Long?> {
        val uri = URI(base)
        val host = uri.host ?: throw IOException("API 地址無效")
        val origin = "${uri.scheme}://${uri.authority}"
        return when {
            host.endsWith("deepseek.com") -> {
                val o = getJson("$origin/user/balance", bearer(key))
                val list = o.optJSONArray("balance_infos") ?: throw IOException("回應缺少 balance_infos")
                (0 until list.length()).map {
                    val i = list.getJSONObject(it)
                    Balance(i.getString("currency"), i.getString("total_balance"), "DeepSeek 帳戶餘額")
                } to null
            }
            host.endsWith("openrouter.ai") -> {
                val d = getJson("$origin/api/v1/key", bearer(key)).getJSONObject("data")
                if (!d.isNull("limit_remaining")) {
                    listOf(Balance("USD", money(d.getDouble("limit_remaining")), "此密鑰剩餘額度")) to null
                } else {
                    val c = getJson("$origin/api/v1/credits", bearer(key)).getJSONObject("data")
                    listOf(Balance("USD", money(c.getDouble("total_credits") - c.getDouble("total_usage")), "帳戶餘額")) to null
                }
            }
            host.endsWith("siliconflow.cn") || host.endsWith("siliconflow.com") -> {
                val d = getJson("$origin/v1/user/info", bearer(key)).getJSONObject("data")
                val cur = if (host.endsWith(".cn")) "CNY" else "USD"
                listOf(Balance(cur, d.getString("totalBalance"), "SiliconFlow 帳戶餘額")) to null
            }
            host.startsWith("api.moonshot.") -> {
                val d = getJson("$origin/v1/users/me/balance", bearer(key)).getJSONObject("data")
                val cur = if (host.endsWith(".cn")) "CNY" else "USD"
                listOf(Balance(cur, money(d.getDouble("available_balance")), "Moonshot 可用餘額")) to null
            }
            else -> relayBalance(origin, key)
        }
    }

    /** new-api / one-api 等中轉站相容的 OpenAI 舊版 billing 介面，以密鑰為單位計算。 */
    private fun relayBalance(origin: String, key: String): Pair<List<Balance>, Long?> {
        val (prefix, sub) = firstSuccess(listOf("$origin/v1", origin)) {
            it to getJson("$it/dashboard/billing/subscription", bearer(key))
        }
        val limit = sub.getDouble("hard_limit_usd")
        val expires = sub.optLong("access_until", 0L).takeIf { it > 0 }
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val usage = getJson(
            "$prefix/dashboard/billing/usage?start_date=${LocalDate.now().minusDays(99).format(fmt)}" +
                "&end_date=${LocalDate.now().plusDays(1).format(fmt)}",
            bearer(key),
        )
        val usedUsd = usage.optDouble("total_usage", 0.0) / 100.0
        // new-api 對無限額度的密鑰回傳極大的 hard_limit_usd
        if (limit >= 1e7) return listOf(Balance("USD", "∞", "此密鑰不限額度")) to expires
        return listOf(Balance("USD", money(limit - usedUsd), "此密鑰剩餘額度（上限 ${money(limit)}）")) to expires
    }

    // ---- HTTP ----

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, body.take(200))
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private class HttpException(val code: Int, body: String) : IOException("HTTP $code ${extractMessage(body)}")

    private fun extractMessage(body: String): String = runCatching {
        val o = JSONObject(body)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: o.optString("message").takeIf { it.isNotBlank() }
            ?: body
    }.getOrDefault(body)

    private fun <T> firstSuccess(candidates: List<String>, block: (String) -> T): T {
        var last: Exception? = null
        for (c in candidates) {
            try {
                return block(c)
            } catch (e: Exception) {
                last = e
                // 401/403 表示密鑰問題，換路徑也沒用
                if (e is HttpException && (e.code == 401 || e.code == 403)) throw e
            }
        }
        throw last ?: IOException("無可用路徑")
    }

    private fun ids(o: JSONObject, arrayKey: String, idKey: String): List<String> {
        val arr = o.optJSONArray(arrayKey) ?: throw IOException("回應缺少 $arrayKey")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString(idKey)?.takeIf { s -> s.isNotBlank() } }
    }

    private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

    private fun anthropicHeaders(key: String) = mapOf(
        "x-api-key" to key,
        "anthropic-version" to "2023-06-01",
        // 部分中轉站只認 Bearer
        "Authorization" to "Bearer $key",
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun money(d: Double) = "%.2f".format(java.util.Locale.US, d)

    private fun describe(e: Throwable): String = when (e) {
        is HttpException -> when (e.code) {
            401, 403 -> "密鑰無效或無權限（${e.message}）"
            404 -> "此供應商不支援（${e.message}）"
            else -> e.message ?: "HTTP 錯誤"
        }
        is java.net.UnknownHostException -> "找不到主機，請檢查地址或網路"
        is java.net.SocketTimeoutException -> "連線逾時"
        else -> e.message ?: e.javaClass.simpleName
    }
}
