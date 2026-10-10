package com.tokencoin.client.avatar

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI

/** 解析後的形象庫位置 */
data class RepoRef(
    /** deepcoin-avatars.json 的實際下載網址 */
    val indexUrl: String,
    /** 索引裡相對路徑的基準（結尾有 /） */
    val baseUrl: String,
    /** 正規化後的倉庫網址（不含 #），當快取鍵與寫卡用 */
    val pageUrl: String,
    /** 網址 # 後面指定的 char_id，可空 */
    val fragment: String,
)

/**
 * 形象庫格式 v1（規格見 讀卡工具/AVATAR.md §6）。純函式，不連網。
 *
 * 卡片 avatar 或使用者輸入的網址有三種：
 * - `https://github.com/<owner>/<repo>`（可加 `/tree/<ref>`、`#<char_id>`）→ 讀 raw.githubusercontent.com 上的索引
 * - 任何以 `/deepcoin-avatars.json` 結尾的網址（可加 `#<char_id>`）→ 直接讀這份索引（自架、測試用）
 * - 其他 → 直接當模型 / 角色包下載（舊行為）
 */
object AvatarRepoFormat {
    const val INDEX_FILE = "deepcoin-avatars.json"
    const val FORMAT = "deepcoin-avatars/1"
    const val TOPIC = "deepcoin-avatar"

    private val GH_NAME = Regex("^[A-Za-z0-9_.-]{1,100}$")
    private val CHAR_ID = Regex("^[A-Za-z0-9_.-]{1,64}$")
    private val SHA = Regex("^[0-9a-f]{64}$")

    fun parseRef(raw: String): RepoRef? {
        val text = raw.trim()
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val fragment = uri.rawFragment?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
        val path = uri.rawPath.orEmpty()

        if (host == "github.com" || host == "www.github.com") {
            val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            val owner = parts[0].lowercase()
            val repo = parts[1].removeSuffix(".git").lowercase()
            if (!GH_NAME.matches(owner) || !GH_NAME.matches(repo)) return null
            // 只接受倉庫首頁或 /tree/<ref>；其他（releases、blob…）當一般檔案網址
            val ref = when {
                parts.size == 2 -> null
                parts.size >= 4 && parts[2] == "tree" -> parts.drop(3).joinToString("/")
                else -> return null
            }
            val page = "https://github.com/$owner/$repo" + (ref?.let { "/tree/$it" } ?: "")
            val base = "https://raw.githubusercontent.com/$owner/$repo/${ref ?: "HEAD"}/"
            return RepoRef(base + INDEX_FILE, base, page, fragment)
        }
        if (path.endsWith("/$INDEX_FILE")) {
            val noFragment = text.substringBefore('#')
            return RepoRef(noFragment, noFragment.removeSuffix(INDEX_FILE), noFragment, fragment)
        }
        return null
    }

    /** 安裝來源鍵：形象庫為「倉庫網址#char_id」，一般網址原樣 */
    fun sourceKey(url: String, cardCharId: String): String {
        val ref = parseRef(url) ?: return url.trim()
        val id = ref.fragment.ifEmpty { cardCharId }
        return if (id.isEmpty()) ref.pageUrl else ref.pageUrl + "#" + id
    }

    fun parseIndex(json: String, ref: RepoRef): AvatarRepoIndex {
        val o = runCatching { JSONObject(json) }.getOrElse { throw IOException("形象庫索引不是有效的 JSON") }
        val format = o.optString("format")
        if (!format.startsWith(FORMAT)) throw IOException("不是鯨幣形象庫（format 應為 $FORMAT）")
        val arr = o.optJSONArray("avatars") ?: JSONArray()
        val seen = mutableSetOf<String>()
        val entries = (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val charId = a.optString("char_id").trim()
            val name = a.optString("name").trim()
            val file = resolve(ref.baseUrl, a.optString("file"))
            if (!CHAR_ID.matches(charId) || name.isEmpty() || file == null || !seen.add(charId)) return@mapNotNull null
            AvatarRepoEntry(
                charId = charId,
                name = name,
                version = a.optString("version"),
                fileUrl = file,
                sha256 = a.optString("sha256").lowercase().takeIf { SHA.matches(it) }.orEmpty(),
                size = a.optLong("size", -1).takeIf { it > 0 },
                thumbUrl = resolve(ref.baseUrl, a.optString("thumb")).orEmpty(),
                authors = a.optJSONArray("authors")?.let { au -> (0 until au.length()).map { au.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                license = a.optString("license"),
                description = a.optString("description"),
                sourceKey = ref.pageUrl + "#" + charId,
            )
        }
        return AvatarRepoIndex(ref.pageUrl, o.optString("name").ifBlank { ref.pageUrl.removePrefix("https://github.com/") }, o.optString("description"), entries)
    }

    /** 依 # 指定 → 卡片 char_id → 第一個 的順序挑角色 */
    fun pick(index: AvatarRepoIndex, fragment: String, cardCharId: String): AvatarRepoEntry? =
        index.entries.firstOrNull { fragment.isNotEmpty() && it.charId == fragment }
            ?: if (fragment.isNotEmpty()) null
            else index.entries.firstOrNull { cardCharId.isNotEmpty() && it.charId == cardCharId } ?: index.entries.firstOrNull()

    /** 相對路徑以 base 解析；不允許 .. 跳出倉庫；只接受 http(s) */
    private fun resolve(base: String, path: String): String? {
        val p = path.trim()
        if (p.isEmpty()) return null
        val abs = runCatching { if (p.startsWith("http://") || p.startsWith("https://")) URI(p) else {
            if (p.split('/').any { it == ".." } || p.startsWith("/")) return null
            URI(base).resolve(p.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") })
        } }.getOrNull() ?: return null
        return abs.toString().takeIf { abs.scheme == "http" || abs.scheme == "https" }
    }

    /** GitHub 搜尋 API 的回應 */
    fun parseSearch(json: String): List<AvatarRepoListing> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val it = items.optJSONObject(i) ?: return@mapNotNull null
            AvatarRepoListing(it.optString("full_name"), it.optString("html_url"), it.optString("description").takeIf { d -> d != "null" }.orEmpty(), it.optInt("stargazers_count"))
        }.filter { it.url.startsWith("https://github.com/") }
    }

    fun searchUrl(query: String): String {
        val q = ("topic:$TOPIC " + query.trim()).trim()
        return "https://api.github.com/search/repositories?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&sort=stars&order=desc&per_page=30"
    }
}
