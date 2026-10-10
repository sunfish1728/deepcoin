package com.tokencoin.client.avatar

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** 安裝目錄先寫完整，再一次更名，失敗時不留下半成品。 */
object AvatarInstaller {
    private const val MAX_BYTES = 600L * 1024 * 1024
    private val validId = Regex("[A-Za-z0-9_-]+")

    fun install(root: File, input: File, originalName: String = input.name,
        charId: String = "", source: String = "import"): AvatarInfo {
        check(root.isDirectory || root.mkdirs()) { "無法建立形象目錄" }
        val id = UUID.randomUUID().toString()
        // 用 canonical 路徑：Android 的 filesDir（/data/user/0）是 /data/data 的符號連結，inside() 會解析成 canonical，
        // 兩邊不一致時 relativeTo 會算出 ../../ 開頭的錯誤路徑
        val staging = File(root, ".tmp-$id").canonicalFile
        check(staging.mkdir()) { "無法建立安裝暫存目錄" }
        try {
            val magic = ByteArray(4)
            val count = input.inputStream().use { it.read(magic) }
            when {
                count == 4 && magic.contentEquals(byteArrayOf(0x67, 0x6c, 0x54, 0x46)) -> {
                    require(input.length() <= MAX_BYTES) { "模型超過 600 MB" }
                    input.copyTo(File(staging, "model.vrm"))
                }
                count == 4 && magic.contentEquals(byteArrayOf(0x50, 0x4b, 3, 4)) -> extract(input, staging)
                else -> throw IllegalArgumentException("不是 VRM 模型或角色包")
            }
            val manifestFile = File(staging, "avatar.json")
            val raw = if (manifestFile.isFile) manifestFile.readText() else "{}"
            val manifest = try { JSONObject(raw) } catch (e: Exception) {
                throw IllegalArgumentException("角色包 avatar.json 格式不正確", e)
            }
            require(!manifest.has("v") || manifest.optInt("v", -1) == 1) { "不支援的角色包版本" }
            val declared = manifest.optString("model")
            val model = if (declared.isNotBlank()) {
                inside(staging, declared).also { require(it.isFile) { "角色包指定的模型不存在" } }
            } else {
                val models = staging.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("vrm", "glb") }.toList()
                require(models.isNotEmpty()) { "角色包沒有 VRM 模型" }
                require(models.size == 1) { "角色包有多個模型，請在 avatar.json 指定 model" }
                models.single()
            }
            val meta = VrmInspector.inspect(model)
            val thumb = manifest.optString("thumb").takeIf(String::isNotBlank)?.let { path ->
                inside(staging, path).takeIf { it.isFile }?.relativeTo(staging)?.invariantSeparatorsPath
            } ?: run {
                val bytes = meta.thumbnailBytes
                val extension = when (meta.thumbnailMime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> null }
                if (bytes != null && extension != null) {
                    // 保留包內同名資產，避免覆寫模型或其他檔案。
                    var name = "thumb.$extension"
                    var index = 1
                    while (File(staging, name).exists()) name = "thumb-${index++}.$extension"
                    File(staging, name).writeBytes(bytes)
                    name
                } else ""
            }
            val info = AvatarInfo(id,
                manifest.optString("name").ifBlank { meta.name }.ifBlank { File(originalName).nameWithoutExtension }.ifBlank { "未命名形象" },
                manifest.optString("char_id").ifBlank { charId }, model.relativeTo(staging).invariantSeparatorsPath,
                meta.vrmVersion, meta.authors, meta.license, thumb, source,
                staging.walkTopDown().filter(File::isFile).sumOf(File::length), System.currentTimeMillis(), raw)
            File(staging, "info.json").writeText(encode(info).toString())
            check(staging.renameTo(File(root, id))) { "無法完成形象安裝" }
            return info
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun extract(input: File, staging: File) = ZipFile(input).use { zip ->
        require(zip.size() <= 2000) { "角色包檔案超過 2000 個" }
        val entries = zip.entries().asSequence().toList()
        entries.forEach { safePath(it.name) }
        val kept = entries.filterNot {
            val segments = it.name.trimEnd('/').split('/')
            "__MACOSX" in segments || segments.last() == ".DS_Store"
        }
        val files = kept.filterNot { it.isDirectory }
        val top = files.map { it.name.substringBefore('/') }.distinct().singleOrNull()
            ?.takeIf { folder -> files.isNotEmpty() && kept.all { it.name == "$folder/" || it.name.startsWith("$folder/") } }
        var total = 0L
        val destinations = mutableSetOf<String>()
        kept.forEach { entry ->
            val path = if (top != null) entry.name.removePrefix("$top/") else entry.name
            if (path.isNotEmpty()) {
                val target = inside(staging, path)
                if (entry.isDirectory) {
                    check(target.isDirectory || target.mkdirs()) { "無法建立角色包目錄" }
                } else {
                    require(destinations.add(target.canonicalPath)) { "角色包有重複路徑" }
                    require(entry.size <= MAX_BYTES - total) { "角色包解壓後超過 600 MB" }
                    val parent = requireNotNull(target.parentFile) { "角色包路徑不安全" }
                    check(parent.isDirectory || parent.mkdirs()) { "無法建立角色包目錄" }
                    zip.getInputStream(entry).use { stream ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val size = stream.read(buffer)
                                if (size < 0) break
                                total += size
                                require(total <= MAX_BYTES) { "角色包解壓後超過 600 MB" }
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safePath(path: String) {
        require(path.isNotEmpty() && !path.startsWith('/') && !path.startsWith('\\') &&
            '\\' !in path && ':' !in path && path.split('/').none { it == ".." || it == "." }) { "角色包路徑不安全" }
    }

    private fun inside(root: File, path: String): File {
        safePath(path)
        val target = File(root, path).canonicalFile
        require(target.toPath().startsWith(root.canonicalFile.toPath()) && target != root.canonicalFile) { "角色包路徑不安全" }
        return target
    }

    fun load(root: File): List<AvatarInfo> {
        return root.listFiles().orEmpty().mapNotNull { dir ->
            if (dir.name.startsWith(".tmp-")) {
                dir.deleteRecursively()
                null
            } else if (dir.isDirectory && validId.matches(dir.name)) {
                runCatching {
                    val json = JSONObject(File(dir, "info.json").readText())
                    require(json.getString("id") == dir.name)
                    val model = json.getString("modelPath")
                    require(inside(dir, model).isFile)
                    val thumb = json.getString("thumbPath")
                    if (thumb.isNotEmpty()) require(inside(dir, thumb).isFile)
                    val version = json.getInt("vrmVersion")
                    require(version in 0..1)
                    val authors = json.getJSONArray("authors")
                    val manifest = File(dir, "avatar.json").takeIf(File::isFile)?.readText() ?: "{}"
                    JSONObject(manifest)
                    AvatarInfo(dir.name, json.getString("name"), json.getString("charId"), model, version,
                        (0 until authors.length()).map { authors.getString(it) }, json.getString("license"), thumb,
                        json.getString("source"), json.getLong("sizeBytes"), json.getLong("installedAt"), manifest)
                }.getOrNull()
            } else null
        }.sortedByDescending { it.installedAt }
    }

    fun delete(root: File, id: String) {
        require(validId.matches(id)) { "形象 ID 不合法" }
        val dir = inside(root, id)
        check(!dir.exists() || dir.deleteRecursively()) { "無法刪除形象" }
    }

    private fun encode(info: AvatarInfo) = JSONObject().apply {
        put("id", info.id); put("name", info.name); put("charId", info.charId)
        put("modelPath", info.modelPath); put("vrmVersion", info.vrmVersion)
        put("authors", JSONArray(info.authors)); put("license", info.license)
        put("thumbPath", info.thumbPath); put("source", info.source)
        put("sizeBytes", info.sizeBytes); put("installedAt", info.installedAt)
    }
}
