package com.tokencoin.client

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokencoin.client.avatar.AvatarRepository
import com.tokencoin.client.card.DecodedCard
import com.tokencoin.client.card.TokenCard
import com.tokencoin.client.card.TokenCardCodec
import com.tokencoin.client.chat.ChatEngine
import com.tokencoin.client.chat.ProviderConfig
import com.tokencoin.client.net.BalanceTracker
import com.tokencoin.client.net.ModelCatalog
import com.tokencoin.client.writer.WriterState

/** 主題：跟隨系統 / 淺色 / 深色 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟隨系統"), LIGHT("light", "淺色"), DARK("dark", "深色");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class NfcAvailability { UNSUPPORTED, DISABLED, READY }

class ClientState(
    context: Context,
    val chat: ChatEngine,
    val avatars: AvatarRepository,
    val balance: BalanceTracker,
    val writer: WriterState,
    private val models: ModelCatalog,
) {
    private val prefs = context.getSharedPreferences("tokencoin_client", Context.MODE_PRIVATE)

    var tab by mutableStateOf(0)

    /** 由 MainActivity 在 onResume 更新 */
    var nfc by mutableStateOf(NfcAvailability.READY)

    /** 聊天畫面是否開啟（覆蓋在分頁之上） */
    var chatOpen by mutableStateOf(false)

    /** 最近一次刷入的卡片，重開 App 仍保留。 */
    var card by mutableStateOf(loadCard())
        private set

    /** 聊天畫面是否顯示形象面板（記住使用者的選擇） */
    var avatarInChat by mutableStateOf(prefs.getBoolean("avatar_in_chat", true))
        private set

    fun toggleAvatarInChat() {
        avatarInChat = !avatarInChat
        prefs.edit().putBoolean("avatar_in_chat", avatarInChat).apply()
    }

    // ---- 設定 ----

    var themeMode by mutableStateOf(ThemeMode.of(prefs.getString("theme", null)))
        private set

    fun updateThemeMode(m: ThemeMode) {
        themeMode = m
        prefs.edit().putString("theme", m.key).apply()
    }

    /** 換卡 / 回到 App 時自動查詢餘額 */
    var autoBalance by mutableStateOf(prefs.getBoolean("auto_balance", true))
        private set

    fun updateAutoBalance(on: Boolean) {
        autoBalance = on
        prefs.edit().putBoolean("auto_balance", on).apply()
        if (on) balance.refreshIfStale()
    }

    /** 形象的觸控互動（點擊反應、摸頭、拖曳轉身） */
    var avatarTouch by mutableStateOf(prefs.getBoolean("avatar_touch", true))
        private set

    fun updateAvatarTouch(on: Boolean) {
        avatarTouch = on
        prefs.edit().putBoolean("avatar_touch", on).apply()
    }

    /** 主頁形象的預設鏡頭：full / bust / face */
    var homeCamera by mutableStateOf(prefs.getString("home_camera", "full") ?: "full")
        private set

    fun updateHomeCamera(preset: String) {
        homeCamera = preset
        prefs.edit().putString("home_camera", preset).apply()
    }

    /** 一次性提示訊息（顯示於 Snackbar 後清除）。 */
    var snack by mutableStateOf<String?>(null)

    fun acceptCard(d: DecodedCard) {
        applyCard(d)
        // 刷卡：回到主頁，讓形象跳出來打招呼
        chatOpen = false
        tab = 0
        cardTapSeq++
        message("已讀取：${d.card.title.ifBlank { d.card.provider + " · " + d.card.model }}")
    }

    /** 把剛寫入的卡片直接設為本機卡片（不用再刷一次） */
    fun acceptWritten(c: TokenCard) {
        val complete = c.copy(v = 1, ref = "")
        applyCard(TokenCardCodec.decode(TokenCardCodec.encode(complete)))
        message("已套用：${c.title.ifBlank { c.provider + " · " + c.model }}")
    }

    /** 移除本機保存的卡片（不影響實體卡與對話紀錄） */
    fun forgetCard() {
        card?.card?.let { balance.forget(it) }
        card = null
        prefs.edit().remove("card").apply()
        setProvider(null)
        avatars.setCard(null)
        balance.setCard(null, false)
        message("已移除本機卡片")
    }

    private fun applyCard(d: DecodedCard) {
        card = d
        prefs.edit().putString("card", d.canonicalJson).apply()
        setProvider(d.card)
        avatars.setCard(d.card)
        balance.setCard(d.card, autoBalance)
    }

    /** 每次刷卡加一，形象舞台據此播放「跳出」動畫 */
    var cardTapSeq by mutableStateOf(0)
        private set

    /** 卡片 models 為「*」時，從 API 檢測到的模型（設定頁顯示用） */
    var detectedModels by mutableStateOf<List<String>>(emptyList())
        private set

    private fun setProvider(c: TokenCard?) {
        models.cancel()
        detectedModels = emptyList()
        if (c == null) return chat.setProvider(null)
        val base = ProviderConfig.fromCard(c)
        fun apply(list: List<String>) {
            detectedModels = list
            chat.setProvider(base.copy(models = (base.models + list).distinct()))
        }
        if (models.wantsAll(c)) apply(models.resolve(c) { if (card?.card?.id == c.id) apply(it) })
        else chat.setProvider(base)
    }

    fun message(text: String) {
        snack = text
    }

    init {
        setProvider(card?.card)
        avatars.setCard(card?.card)
        balance.setCard(card?.card, autoBalance)
    }

    private fun loadCard(): DecodedCard? = prefs.getString("card", null)
        ?.let { runCatching { TokenCardCodec.decode(it.toByteArray(Charsets.UTF_8)) }.getOrNull() }
}
