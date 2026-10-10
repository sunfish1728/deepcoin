package com.tokencoin.client.avatar

import android.net.Uri
import com.tokencoin.client.card.TokenCard
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/*
 * 虛擬形象模組的「前後端契約」。
 * - 前端（ui/avatar/、assets/avatar/ 渲染器）只依賴本檔。
 * - 後端實作 [AvatarRepository] 與 [EmotionAnalyzer]，並提供
 *   `fun createAvatarRepository(context: Context, scope: CoroutineScope): AvatarRepository`（放在 AvatarRepositoryImpl.kt）。
 * 修改本檔需同時更新前後端。
 *
 * 角色包（公式化格式，見 讀卡工具/AVATAR.md）：
 * - 最簡形式：單一 `.vrm` 檔（VRM 0.x / 1.0）。所有行為由渲染器依 VRM 標準預設推導。
 * - 完整形式：`.zip`，根目錄有 `avatar.json`（manifest）與模型檔，可附 `motions/`、`thumb.png` 等。
 *   manifest 的行為欄位（表情對應、互動台詞、鏡頭…）由渲染器解讀，後端只讀取 [AvatarInfo] 需要的欄位，
 *   並把整份 JSON 原樣放在 [AvatarInfo.manifestJson]。
 */

/** 已安裝的形象。檔案放在 `filesDir/avatars/<id>/`。 */
data class AvatarInfo(
    /** 安裝 ID（目錄名，僅 [A-Za-z0-9_-]） */
    val id: String,
    /** 顯示名稱：manifest.name → VRM meta 名稱 → 檔名 */
    val name: String,
    /** 角色 ID：manifest.char_id，或安裝時卡片的 char_id；可為空 */
    val charId: String,
    /** 模型檔相對於形象目錄的路徑，例如 `model.vrm` */
    val modelPath: String,
    /** VRM 規格主版本：0 或 1 */
    val vrmVersion: Int,
    /** 作者（VRM meta） */
    val authors: List<String>,
    /** 授權摘要，例如「個人非商業 · 禁止再配布」，供 UI 顯示 */
    val license: String,
    /** 縮圖相對路徑（從 VRM 內嵌縮圖抽出或包內附），沒有則為空 */
    val thumbPath: String,
    /** 來源：`import`（手動匯入）或下載的 URL */
    val source: String,
    val sizeBytes: Long,
    val installedAt: Long,
    /** avatar.json 原文；單一 .vrm 匯入時為 `{}` */
    val manifestJson: String,
)

/** 匯入 / 下載中的工作。 */
data class AvatarTask(
    val label: String,
    /** 0f..1f；null 表示無法估計 */
    val progress: Float?,
)

/**
 * 形象倉庫。所有方法可在主執行緒呼叫並立即返回，耗時工作在實作自己的 scope 處理。
 *
 * 目前形象的決定規則（[active]）：
 * 1. 卡片有 char_id，且使用者曾為此 char_id 選過形象 → 該形象
 * 2. 卡片有 char_id，且有已安裝形象的 [AvatarInfo.charId] 相同 → 最新安裝的那個
 * 3. 卡片有 avatar URL：已從同一來源下載過 → 該形象；否則自動下載安裝（charId 設為卡片 char_id）後採用。
 *    URL 可以直接指向 .vrm / .zip，也可以是形象庫（GitHub 倉庫網址或 deepcoin-avatars.json，見 AVATAR.md §6），
 *    形象庫以「倉庫網址#char_id」當來源（[AvatarRepoFormat.sourceKey]），同一張卡下次刷直接用快取
 * 4. 使用者最後手動選擇的形象
 * 5. 任一已安裝形象（最新安裝）
 * 6. 都沒有 → null
 */
interface AvatarRepository {
    /** 已安裝形象，依安裝時間由新到舊 */
    val avatars: StateFlow<List<AvatarInfo>>

    /** 依上述規則決定的目前形象 */
    val active: StateFlow<AvatarInfo?>

    /** 進行中的匯入 / 下載；null 表示閒置 */
    val task: StateFlow<AvatarTask?>

    /** 一次性訊息（匯入成功、失敗原因等），UI 以 Snackbar 顯示 */
    val messages: SharedFlow<String>

    /** 換卡（或啟動時）呼叫，觸發規則 1–3；card 為 null 表示沒有卡片 */
    fun setCard(card: TokenCard?)

    /** 從系統檔案選擇器匯入 `.vrm` / `.glb` / `.zip` 角色包；成功後自動選為目前形象 */
    fun import(uri: Uri)

    /** 手動選擇形象；目前卡片有 char_id 時同時記住「此 char_id 用這個形象」 */
    fun select(id: String)

    fun delete(id: String)

    /** 用 GitHub topic `deepcoin-avatar` 搜尋公開的形象庫；query 可空 */
    suspend fun searchRepos(query: String): List<AvatarRepoListing>

    /** 讀取形象庫的索引（GitHub 倉庫網址或 deepcoin-avatars.json 網址） */
    suspend fun loadRepo(url: String): AvatarRepoIndex

    /** 從形象庫安裝（已安裝同一來源時直接選用）；成功後自動選為目前形象 */
    fun installFromRepo(entry: AvatarRepoEntry)

    /** 形象目錄的根，WebView 以 `https://appassets.androidplatform.net/avatars/` 對應到這裡 */
    val rootDir: File
}

/** 形象庫（deepcoin-avatars.json）裡的一個角色。 */
data class AvatarRepoEntry(
    val charId: String,
    val name: String,
    val version: String,
    /** 模型或角色包的下載網址（相對路徑已解析成絕對網址） */
    val fileUrl: String,
    /** 小寫十六進位；空字串表示索引沒提供，不驗證 */
    val sha256: String,
    val size: Long?,
    val thumbUrl: String,
    val authors: List<String>,
    val license: String,
    val description: String,
    /** 安裝後的 [AvatarInfo.source]：倉庫網址#char_id */
    val sourceKey: String,
)

data class AvatarRepoIndex(
    /** 倉庫頁面網址（GitHub）或索引檔網址，寫進卡片 avatar 欄位用這個 */
    val url: String,
    val name: String,
    val description: String,
    val entries: List<AvatarRepoEntry>,
)

/** GitHub 搜尋結果 */
data class AvatarRepoListing(val fullName: String, val url: String, val description: String, val stars: Int)

/** 情緒，與渲染器的情緒名稱一一對應。 */
enum class Emotion(val key: String) {
    NEUTRAL("neutral"), JOY("joy"), ANGRY("angry"), SAD("sad"),
    SURPRISED("surprised"), RELAXED("relaxed"), THINKING("thinking"),
}

/** 情緒判斷結果；intensity 0f..1f */
data class EmotionResult(val emotion: Emotion, val intensity: Float)

/** 從助手回覆文字推測情緒（關鍵字 / 表情符號 / 顏文字 / 標點），純函式，不連網。 */
interface EmotionAnalyzer {
    fun analyze(text: String): EmotionResult
}
