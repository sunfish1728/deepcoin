package com.tokencoin.client.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokencoin.client.card.TokenCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 最近一次成功查到的餘額。 */
data class BalanceSnapshot(
    val balances: List<Balance>,
    /** 密鑰到期（Unix 秒），中轉站才有 */
    val expiresAt: Long?,
    val fetchedAt: Long,
)

data class BalanceState(
    val snapshot: BalanceSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 供應商不提供以密鑰查餘額（官方 OpenAI / Anthropic 等），主頁改顯示卡片記錄的金額 */
    val unsupported: Boolean = false,
)

/**
 * 查詢目前卡片的即時餘額（沿用 [ApiProbe.queryBalance]）。
 * 每張卡最近一次的結果存在 SharedPreferences `tokencoin_balance`，沒網路時仍可顯示上次的數字。
 */
class BalanceTracker(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("tokencoin_balance", Context.MODE_PRIVATE)

    var state by mutableStateOf(BalanceState())
        private set

    private var card: TokenCard? = null
    private var job: Job? = null

    fun setCard(c: TokenCard?, autoRefresh: Boolean) {
        val same = c != null && card?.id == c.id && card?.key == c.key && card?.base == c.base
        card = c
        if (!same) {
            job?.cancel()
            state = BalanceState(snapshot = c?.let { load(it) })
        }
        if (autoRefresh) refreshIfStale()
    }

    /** 上次查詢超過 [STALE_MS] 才重查（App 回到前景、換卡時） */
    fun refreshIfStale() {
        val s = state
        if (card == null || s.loading || s.unsupported) return
        val age = System.currentTimeMillis() - (s.snapshot?.fetchedAt ?: 0L)
        if (age > STALE_MS) refresh()
    }

    fun refresh() {
        val c = card ?: return
        job?.cancel()
        state = state.copy(loading = true, error = null)
        job = scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ApiProbe.queryBalance(c.base.trim().trimEnd('/'), c.key) }
            }
            if (card?.id != c.id) return@launch
            r.onSuccess { (list, exp) ->
                val snap = BalanceSnapshot(list, exp, System.currentTimeMillis())
                save(c, snap)
                state = BalanceState(snapshot = snap)
            }.onFailure { e ->
                state = state.copy(
                    loading = false,
                    error = ApiProbe.describe(e),
                    unsupported = e is ApiProbe.UnsupportedBalanceException,
                )
            }
        }
    }

    fun forget(c: TokenCard) {
        prefs.edit().remove(prefKey(c)).apply()
    }

    private fun prefKey(c: TokenCard) = "snap:" + c.id

    private fun save(c: TokenCard, s: BalanceSnapshot) {
        val arr = JSONArray()
        s.balances.forEach { arr.put(JSONObject().put("currency", it.currency).put("amount", it.amount).put("label", it.label)) }
        val o = JSONObject().put("balances", arr).put("fetchedAt", s.fetchedAt)
        s.expiresAt?.let { o.put("expiresAt", it) }
        prefs.edit().putString(prefKey(c), o.toString()).apply()
    }

    private fun load(c: TokenCard): BalanceSnapshot? = prefs.getString(prefKey(c), null)?.let { raw ->
        runCatching {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("balances")
            BalanceSnapshot(
                balances = (0 until arr.length()).map {
                    val b = arr.getJSONObject(it)
                    Balance(b.getString("currency"), b.getString("amount"), b.optString("label"))
                },
                expiresAt = if (o.has("expiresAt")) o.getLong("expiresAt") else null,
                fetchedAt = o.getLong("fetchedAt"),
            )
        }.getOrNull()
    }

    companion object {
        const val STALE_MS = 5 * 60_000L
    }
}
