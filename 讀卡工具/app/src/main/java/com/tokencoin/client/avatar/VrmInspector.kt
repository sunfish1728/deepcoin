package com.tokencoin.client.avatar

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

data class VrmMetadata(
    val vrmVersion: Int,
    val name: String,
    val authors: List<String>,
    val license: String,
    val thumbnailBytes: ByteArray? = null,
    val thumbnailMime: String? = null,
)

/** 只讀取 JSON 與縮圖所在的 BIN 區段，不載入整個模型。 */
object VrmInspector {
    fun inspect(file: File): VrmMetadata = RandomAccessFile(file, "r").use { input ->
        fun uint(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
        require(input.length() >= 12 && uint() == 0x46546c67L) { "不是 GLB 模型" }
        require(uint() == 2L) { "只支援 GLB 第 2 版" }
        val length = uint()
        require(length == input.length()) { "GLB 長度不正確" }
        var json: JSONObject? = null
        var binOffset = -1L
        var binLength = 0L
        while (input.filePointer < length) {
            require(length - input.filePointer >= 8) { "GLB 區塊標頭不完整" }
            val chunkLength = uint()
            val type = uint()
            val offset = input.filePointer
            require(chunkLength % 4 == 0L && chunkLength <= length - offset) { "GLB 區塊長度不正確" }
            when (type) {
                0x4e4f534aL -> {
                    require(json == null && offset == 20L) { "GLB JSON 區塊順序不正確" }
                    require(chunkLength <= Int.MAX_VALUE) { "模型 JSON 過大" }
                    val bytes = ByteArray(chunkLength.toInt())
                    input.readFully(bytes)
                    json = try { JSONObject(bytes.toString(Charsets.UTF_8).trimEnd(' ', '\u0000')) }
                    catch (e: Exception) { throw IllegalArgumentException("模型 JSON 格式不正確", e) }
                }
                0x004e4942L -> {
                    require(binOffset < 0 && json != null) { "GLB BIN 區塊順序不正確" }
                    binOffset = offset
                    binLength = chunkLength
                }
            }
            input.seek(offset + chunkLength)
        }
        val document = requireNotNull(json) { "GLB 缺少 JSON 區塊" }
        val extensions = document.optJSONObject("extensions")
        val vrm1 = extensions?.optJSONObject("VRMC_vrm")
        val vrm = vrm1 ?: extensions?.optJSONObject("VRM")
        require(vrm != null) { "模型沒有 VRM 擴充" }
        val version = if (vrm1 != null) 1 else 0
        val meta = vrm.optJSONObject("meta") ?: JSONObject()
        val authors = if (version == 1) {
            val array = meta.optJSONArray("authors")
            (0 until (array?.length() ?: 0)).mapNotNull { (array?.opt(it) as? String)?.takeIf(String::isNotBlank) }
        } else listOfNotNull(meta.optString("author").takeIf(String::isNotBlank))
        val imageIndex = if (version == 1) meta.optInt("thumbnailImage", -1) else {
            document.optJSONArray("textures")?.optJSONObject(meta.optInt("texture", -1))?.optInt("source", -1) ?: -1
        }
        val image = document.optJSONArray("images")?.optJSONObject(imageIndex)
        val view = document.optJSONArray("bufferViews")?.optJSONObject(image?.optInt("bufferView", -1) ?: -1)
        var thumbnail: ByteArray? = null
        if (view != null && binOffset >= 0 && view.optInt("buffer", 0) == 0) {
            val offset = view.optLong("byteOffset", 0)
            val size = view.optLong("byteLength", -1)
            require(offset >= 0 && size >= 0 && offset <= binLength && size <= binLength - offset) { "模型縮圖範圍不正確" }
            require(size <= Int.MAX_VALUE) { "模型縮圖過大" }
            thumbnail = ByteArray(size.toInt())
            input.seek(binOffset + offset)
            input.readFully(thumbnail)
        }
        VrmMetadata(version, meta.optString(if (version == 1) "name" else "title"), authors,
            license(meta, version), thumbnail, image?.optString("mimeType")?.takeIf(String::isNotBlank))
    }

    private fun license(meta: JSONObject, version: Int): String {
        val parts = mutableListOf<String>()
        fun known(key: String, values: Map<String, String>) { values[meta.optString(key)]?.let(parts::add) }
        if (version == 1) {
            known("commercialUsage", mapOf("personalNonProfit" to "個人非商業", "personalProfit" to "個人商業可", "corporation" to "企業商業可"))
            if (meta.opt("allowRedistribution") is Boolean) parts += if (meta.getBoolean("allowRedistribution")) "允許再配布" else "禁止再配布"
            known("modification", mapOf("prohibited" to "禁止修改", "allowModification" to "允許修改", "allowModificationRedistribution" to "允許修改與再配布"))
            known("avatarPermission", mapOf("onlyAuthor" to "僅作者可用", "onlySeparatelyLicensedPerson" to "僅授權者可用", "everyone" to "所有人可用"))
        } else {
            known("commercialUssageName", mapOf("Allow" to "允許商業使用", "Disallow" to "禁止商業使用"))
            known("licenseName", mapOf("Redistribution_Prohibited" to "禁止再配布", "CC0" to "CC0", "CC_BY" to "CC BY", "CC_BY_NC" to "CC BY-NC", "CC_BY_SA" to "CC BY-SA", "CC_BY_NC_SA" to "CC BY-NC-SA", "CC_BY_ND" to "CC BY-ND", "CC_BY_NC_ND" to "CC BY-NC-ND"))
            known("allowedUserName", mapOf("OnlyAuthor" to "僅作者可用", "ExplicitlyLicensedPerson" to "僅授權者可用", "Everyone" to "所有人可用"))
        }
        return parts.joinToString(" · ")
    }
}
