package com.tokencoin.client.writer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokencoin.client.card.Presets
import com.tokencoin.client.card.TokenCard
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** 表單狀態：全部以字串保存，寫卡前才轉成 [TokenCard]。 */
data class CardForm(
    val id: String = newCardId(),
    val title: String = "",
    val vendor: String = "deepseek",
    val model: String = "deepseek-chat",
    val models: String = "",
    val provider: String = "DeepSeek 官方",
    val proto: String = "openai",
    val base: String = "https://api.deepseek.com",
    val key: String = "",
    val apps: Set<String> = emptySet(),
    val exp: String = "",
    val balanceUsd: String = "",
    val char: String = "",
    val charId: String = "",
    val avatar: String = "",
    val note: String = "",
    val aarEnabled: Boolean = true,
    val aarPackage: String = "",
) {
    /** 轉換並驗證；失敗時回傳錯誤訊息清單。 */
    fun toCard(now: Long = Instant.now().epochSecond): Pair<TokenCard?, List<String>> {
        val errors = mutableListOf<String>()
        if (vendor.isBlank()) errors += "請填寫 AI 模型供應商"
        if (model.isBlank()) errors += "請填寫預設模型"
        if (provider.isBlank()) errors += "請填寫 API 供應商名稱"
        if (proto.isBlank()) errors += "請選擇 API 協議"
        if (!base.trim().matches(Regex("^https?://\\S+$"))) errors += "API 地址需以 http:// 或 https:// 開頭"
        if (key.isBlank()) errors += "請填寫 API 密鑰"
        if (key.any { it.isWhitespace() }) errors += "API 密鑰含有空白字元"
        if (avatar.isNotBlank() && !avatar.trim().matches(Regex("^https?://\\S+$"))) errors += "形象 URL 格式錯誤"

        val expSec = exp.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                // 到期日當天結束（本地時區）
                LocalDate.parse(it).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1
            } catch (_: DateTimeParseException) {
                errors += "到期日格式需為 YYYY-MM-DD"; null
            }
        }
        val usd = balanceUsd.trim().removePrefix("$").replace(",", "")
        if (usd.isNotEmpty() && !usd.matches(Regex("^\\d+(\\.\\d{1,6})?$"))) errors += "美元餘額需為數字，例如 5.00"
        if (errors.isNotEmpty()) return null to errors

        return TokenCard(
            id = id.trim(), title = title, vendor = vendor.trim(), model = model.trim(),
            models = models.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() && it != model.trim() }
                .let { if (Presets.ALL_MODELS in it) listOf(Presets.ALL_MODELS) else it },
            provider = provider, proto = proto.trim(), base = base.trim().trimEnd('/'), key = key.trim(),
            apps = apps.sorted(), iat = now, exp = expSec, balanceUsd = usd,
            char = char, charId = charId.trim(), avatar = avatar.trim(), note = note,
        ) to emptyList()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("vendor", vendor); put("model", model)
        put("models", models); put("provider", provider); put("proto", proto); put("base", base)
        put("key", key); put("apps", JSONArray(apps.toList())); put("exp", exp); put("balanceUsd", balanceUsd)
        put("char", char); put("charId", charId); put("avatar", avatar)
        put("note", note); put("aarEnabled", aarEnabled); put("aarPackage", aarPackage)
    }

    companion object {
        fun fromJson(o: JSONObject) = CardForm(
            id = o.optString("id", newCardId()), title = o.optString("title"),
            vendor = o.optString("vendor"), model = o.optString("model"), models = o.optString("models"),
            provider = o.optString("provider"), proto = o.optString("proto"), base = o.optString("base"),
            key = o.optString("key"),
            apps = o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            exp = o.optString("exp"), balanceUsd = o.optString("balanceUsd"), char = o.optString("char"),
            charId = o.optString("charId"), avatar = o.optString("avatar"),
            note = o.optString("note"), aarEnabled = o.optBoolean("aarEnabled", true),
            aarPackage = o.optString("aarPackage"),
        )

        fun fromCard(c: TokenCard) = CardForm(
            id = c.id.ifBlank { newCardId() }, title = c.title, vendor = c.vendor, model = c.model,
            models = c.models.joinToString(", "), provider = c.provider, proto = c.proto, base = c.base,
            key = c.key, apps = c.apps.toSet(),
            exp = c.exp?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() } ?: "",
            balanceUsd = c.balanceUsd, char = c.char, charId = c.charId, avatar = c.avatar, note = c.note,
        )
    }
}

fun newCardId(): String = ByteArray(4).also { SecureRandom().nextBytes(it) }
    .joinToString("") { "%02x".format(it) }

data class Profile(val name: String, val form: CardForm)

/** 寫卡任務：等待使用者把卡片靠近。 */
data class WriteJob(val card: TokenCard, val aarPackage: String?, val lock: Boolean)

sealed interface WriteStatus {
    data object Waiting : WriteStatus
    /** card：剛寫入的內容，可直接套用為本機卡片 */
    data class Success(val message: String, val card: TokenCard) : WriteStatus
    data class Failed(val message: String) : WriteStatus
}

/**
 * 寫卡分頁的狀態（移植自寫卡工具的 AppState）。
 * 表單草稿與設定檔存在獨立的 SharedPreferences `tokencoin_writer`，和本機正在使用的卡片分開。
 */
class WriterState(context: Context) {
    private val prefs = context.getSharedPreferences("tokencoin_writer", Context.MODE_PRIVATE)

    var form by mutableStateOf(loadDraft())
        private set
    var lockAfterWrite by mutableStateOf(false)

    /** 由 NFC binder 執行緒讀取，所以標成 volatile。 */
    @Volatile
    var pendingWrite: WriteJob? = null
        private set
    var writeStatus by mutableStateOf<WriteStatus?>(null)

    val profiles = mutableStateListOf<Profile>().apply { addAll(loadProfiles()) }

    fun updateForm(f: CardForm) {
        form = f
        prefs.edit().putString("draft", f.toJson().toString()).apply()
    }

    /** 以現有卡片為底稿（換新卡片 ID，避免和原卡重複）。 */
    fun loadFromCard(c: TokenCard) = updateForm(CardForm.fromCard(c).copy(id = newCardId()))

    fun startWrite(job: WriteJob) {
        pendingWrite = job
        writeStatus = WriteStatus.Waiting
    }

    fun cancelWrite() {
        pendingWrite = null
        writeStatus = null
    }

    fun finishWrite(status: WriteStatus) {
        pendingWrite = null
        writeStatus = status
    }

    fun dismissStatus() {
        writeStatus = null
    }

    fun saveProfile(name: String, f: CardForm) {
        val i = profiles.indexOfFirst { it.name == name }
        if (i >= 0) profiles[i] = Profile(name, f) else profiles += Profile(name, f)
        persistProfiles()
    }

    fun deleteProfile(p: Profile) {
        profiles.remove(p)
        persistProfiles()
    }

    private fun persistProfiles() {
        val arr = JSONArray()
        profiles.forEach { arr.put(JSONObject().put("name", it.name).put("form", it.form.toJson())) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun loadProfiles(): List<Profile> = runCatching {
        val arr = JSONArray(prefs.getString("profiles", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Profile(o.getString("name"), CardForm.fromJson(o.getJSONObject("form")))
        }
    }.getOrDefault(emptyList())

    private fun loadDraft(): CardForm = prefs.getString("draft", null)
        ?.let { runCatching { CardForm.fromJson(JSONObject(it)) }.getOrNull() }
        ?: CardForm()
}
