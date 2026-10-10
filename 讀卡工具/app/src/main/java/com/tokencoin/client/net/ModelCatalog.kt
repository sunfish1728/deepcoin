package com.tokencoin.client.net

import android.content.Context
import com.tokencoin.client.card.Presets
import com.tokencoin.client.card.TokenCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 卡片 models 為「*」（使用 API 的所有模型）時，向 API 取得模型清單（沿用 [ApiProbe.listModels]）。
 * 最近一次的清單存在 SharedPreferences `tokencoin_models`，換卡時先用快取，再背景更新。
 */
class ModelCatalog(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("tokencoin_models", Context.MODE_PRIVATE)
    private var job: Job? = null

    /** 卡片是否要求自動檢測 */
    fun wantsAll(c: TokenCard) = Presets.ALL_MODELS in c.models

    /** 立即回傳快取（可能為空），並在背景查詢；查到新清單時呼叫 onUpdate */
    fun resolve(c: TokenCard, onUpdate: (List<String>) -> Unit): List<String> {
        job?.cancel()
        val cached = cached(c)
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ApiProbe.listModels(c.proto, c.base.trim().trimEnd('/'), c.key) } }
            r.getOrNull()?.takeIf { it.isNotEmpty() && it != cached }?.let {
                prefs.edit().putString(key(c), JSONArray(it).toString()).apply()
                onUpdate(it)
            }
        }
        return cached
    }

    fun cancel() {
        job?.cancel()
    }

    private fun key(c: TokenCard) = "models:" + c.id

    private fun cached(c: TokenCard): List<String> = prefs.getString(key(c), null)?.let { raw ->
        runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
    }.orEmpty()
}
