package com.tokencoin.client.avatar

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tokencoin.client.card.TokenCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.io.InputStream
import java.util.concurrent.TimeUnit

fun createAvatarRepository(context: Context, scope: CoroutineScope): AvatarRepository =
    AvatarRepositoryImpl(context.applicationContext, scope)

/** 狀態與安裝由單一 IO 佇列管理，傳輸工作只把進度與結果送回佇列。 */
class AvatarRepositoryImpl(private val context: Context, scope: CoroutineScope) : AvatarRepository {
    override val rootDir = File(context.filesDir, "avatars")
    private val avatarState = MutableStateFlow<List<AvatarInfo>>(emptyList())
    private val activeState = MutableStateFlow<AvatarInfo?>(null)
    private val taskState = MutableStateFlow<AvatarTask?>(null)
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val avatars = avatarState.asStateFlow()
    override val active = activeState.asStateFlow()
    override val task = taskState.asStateFlow()
    override val messages = events.asSharedFlow()

    // sel:<charId> 記錄卡片選擇；sel: 記錄最後一次手動選擇。
    private val prefs = context.getSharedPreferences("tokencoin_avatar", Context.MODE_PRIVATE)
    private val selections = mutableMapOf<String, String>()
    private var manualDefault: String? = null
    private var card: TokenCard? = null
    private val failedUrls = mutableSetOf<String>()
    private val queuedUrls = mutableSetOf<String>()
    private val pending = ArrayDeque<Transfer>()
    private var running: Transfer? = null
    private var transferJob: Job? = null
    private lateinit var workerScope: CoroutineScope
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    /**
     * url：安裝後的來源鍵（直接網址，或形象庫的「倉庫網址#char_id」）。
     * entry：已從形象庫挑好的角色；url 是形象庫但 entry 為 null 時，下載前先讀索引再挑。
     * select：安裝後自動選用（手動匯入、從形象庫安裝）。
     */
    private data class Transfer(
        val uri: Uri? = null,
        val url: String = "",
        val charId: String = "",
        val entry: AvatarRepoEntry? = null,
        val select: Boolean = false,
    )

    init {
        scope.launch(Dispatchers.IO) {
            workerScope = this
            try {
                try {
                    prefs.all.forEach { (key, value) ->
                        if (key.startsWith("sel:") && value is String) {
                            if (key == "sel:") manualDefault = value else selections[key.removePrefix("sel:")] = value
                        }
                    }
                    avatarState.value = AvatarInstaller.load(rootDir)
                    recompute()
                } catch (e: Exception) { report("讀取形象失敗：${e.message}") }
                for (command in commands) {
                    try { command() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { report("形象操作失敗：${e.message}") }
                }
            } finally {
                commands.close()
                transferJob?.cancel()
            }
        }
    }

    private fun submit(action: () -> Unit) = commands.trySend(action).isSuccess
    private fun report(message: String) { android.util.Log.i("TcAvatar", message); events.tryEmit(message) }

    override fun setCard(card: TokenCard?) { submit { this.card = card; recompute() } }
    override fun select(id: String) { submit { selectNow(id); recompute() } }
    override fun import(uri: Uri) { submit { pending.addLast(Transfer(uri = uri, charId = card?.charId.orEmpty(), select = true)); startNext() } }

    override fun installFromRepo(entry: AvatarRepoEntry) {
        submit {
            val existing = avatarState.value.firstOrNull { it.source == entry.sourceKey }
            when {
                existing != null -> { selectNow(existing.id); recompute(); report("已切換：${existing.name}") }
                entry.sourceKey in queuedUrls -> Unit
                else -> {
                    queuedUrls += entry.sourceKey
                    failedUrls -= entry.sourceKey
                    pending.addLast(Transfer(url = entry.sourceKey, charId = entry.charId, entry = entry, select = true))
                    startNext()
                }
            }
        }
    }

    override suspend fun searchRepos(query: String): List<AvatarRepoListing> = withContext(Dispatchers.IO) {
        AvatarRepoFormat.parseSearch(getText(AvatarRepoFormat.searchUrl(query), github = true))
    }

    override suspend fun loadRepo(url: String): AvatarRepoIndex = withContext(Dispatchers.IO) {
        val ref = AvatarRepoFormat.parseRef(url) ?: throw IOException("不是形象庫網址（GitHub 倉庫或 ${AvatarRepoFormat.INDEX_FILE}）")
        AvatarRepoFormat.parseIndex(getText(ref.indexUrl), ref)
    }

    /** 小型文字回應（索引、搜尋結果），上限 1 MB */
    private fun getText(url: String, github: Boolean = false): String {
        val req = Request.Builder().url(url).get().header("User-Agent", "deepcoin-app")
        if (github) req.header("Accept", "application/vnd.github+json")
        client.newCall(req.build()).execute().use { r ->
            if (r.code == 404) throw IOException("找不到形象庫索引（$url）")
            if (r.code == 403 && github) throw IOException("GitHub 搜尋次數已達上限，請稍後再試")
            if (!r.isSuccessful) throw IOException("伺服器回應 HTTP ${r.code}")
            val body = r.body ?: throw IOException("伺服器沒有回應內容")
            require(body.contentLength() <= 1_000_000) { "索引檔太大" }
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    require(out.size() <= 1_000_000) { "索引檔太大" }
                }
            }
            return out.toString("UTF-8")
        }
    }
    override fun delete(id: String) {
        submit {
            AvatarInstaller.delete(rootDir, id)
            avatarState.value = avatarState.value.filterNot { it.id == id }
            val editor = prefs.edit()
            selections.filterValues { it == id }.keys.toList().forEach { selections.remove(it); editor.remove("sel:$it") }
            if (manualDefault == id) { manualDefault = null; editor.remove("sel:") }
            editor.apply()
            recompute()
        }
    }

    private fun selectNow(id: String) {
        if (avatarState.value.none { it.id == id }) return
        val editor = prefs.edit().putString("sel:", id)
        manualDefault = id
        card?.charId?.takeIf(String::isNotEmpty)?.let { selections[it] = id; editor.putString("sel:$it", id) }
        editor.apply()
    }

    private fun recompute() {
        val raw = card?.avatar.orEmpty()
        val url = if (raw.isBlank()) "" else AvatarRepoFormat.sourceKey(raw, card?.charId.orEmpty())
        val result = AvatarResolver.resolve(avatarState.value, card?.charId.orEmpty(), url, selections, manualDefault)
        activeState.value = result.active
        if (result.needsDownload && url !in failedUrls && url !in queuedUrls) {
            val parsed = runCatching { java.net.URI(url) }.getOrNull()
            if (parsed?.scheme?.lowercase() !in setOf("http", "https") || parsed?.host.isNullOrEmpty()) {
                failedUrls += url
                report("形象下載失敗：只支援 HTTP 或 HTTPS 網址")
            } else {
                queuedUrls += url
                pending.addLast(Transfer(url = url, charId = card?.charId.orEmpty()))
                startNext()
            }
        }
    }

    private fun startNext() {
        if (running != null || pending.isEmpty()) return
        val transfer = pending.removeFirst()
        running = transfer
        val label = if (transfer.uri != null) "匯入形象" else "下載形象"
        taskState.value = AvatarTask(label, null)
        transferJob = workerScope.launch(Dispatchers.IO) {
            var temp: File? = null
            try {
                temp = File.createTempFile("avatar-", ".tmp", context.cacheDir)
                val local = temp
                var name = ""
                var lastProgress = 0L
                fun progress(bytes: Long, total: Long?) {
                    val now = System.nanoTime()
                    if (now - lastProgress >= 100_000_000L || bytes == total) {
                        lastProgress = now
                        val fraction = total?.takeIf { it > 0 }?.let { (bytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
                        submit { if (running === transfer) taskState.value = AvatarTask(label, fraction) }
                    }
                }
                if (transfer.uri != null) {
                    var total: Long? = null
                    context.contentResolver.query(transfer.uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).orEmpty()
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) total = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                        }
                    }
                    if (name.isBlank()) name = transfer.uri.lastPathSegment.orEmpty()
                    val stream = context.contentResolver.openInputStream(transfer.uri) ?: error("無法開啟匯入檔案")
                    val n = stream.use { copy(it, local, total, ::progress) }
                    require(total == null || n == total) { "檔案傳輸不完整" }
                } else {
                    // 形象庫：先讀索引挑角色，再下載它的檔案
                    val entry = transfer.entry ?: AvatarRepoFormat.parseRef(transfer.url)?.let { ref ->
                        submit { if (running === transfer) taskState.value = AvatarTask("讀取形象庫", null) }
                        val index = AvatarRepoFormat.parseIndex(getText(ref.indexUrl), ref)
                        AvatarRepoFormat.pick(index, ref.fragment, transfer.charId)
                            ?: throw IOException("形象庫裡沒有角色「${ref.fragment.ifEmpty { transfer.charId }}」")
                    }
                    val fileUrl = entry?.fileUrl ?: transfer.url
                    entry?.size?.let { require(it <= 300L * 1024 * 1024) { "檔案超過 300 MB" } }
                    name = Uri.parse(fileUrl).lastPathSegment.orEmpty()
                    // 連線中斷時用 Range 續傳，最多試 3 次（手機網路、模擬器都會遇到串流被切斷）
                    var written = 0L
                    var total: Long? = null
                    var attempt = 0
                    while (true) {
                        val req = Request.Builder().url(fileUrl).get().header("User-Agent", "deepcoin-app")
                        if (written > 0) req.header("Range", "bytes=$written-")
                        val call = client.newCall(req.build())
                        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                        try {
                            call.execute().use { response ->
                                require(response.isSuccessful) { "伺服器回應 HTTP ${response.code}" }
                                // 卡片的 avatar 也可能是頭像圖片或網頁，不必整個下載
                                val type = response.header("Content-Type").orEmpty().lowercase()
                                require(!type.startsWith("image/") && !type.startsWith("text/")) { "網址不是 VRM 模型（$type）" }
                                val body = response.body ?: error("伺服器沒有傳回模型")
                                val length = body.contentLength().takeIf { size -> size >= 0 }
                                // 伺服器不支援續傳（回 200）就從頭來
                                if (response.code != 206) { written = 0; total = length } else if (total == null) total = length?.let { it + written }
                                body.byteStream().use { written = copy(it, local, total, ::progress, start = written) }
                            }
                            break
                        } catch (e: IOException) {
                            currentCoroutineContext().ensureActive()
                            written = local.length()
                            if (++attempt >= 3) throw e
                            android.util.Log.w("TcAvatar", "下載中斷，第 $attempt 次續傳（已收 $written bytes）", e)
                            kotlinx.coroutines.delay(1000L * attempt)
                        } finally { cancellation?.dispose() }
                    }
                    require(total == null || written == total) { "檔案傳輸不完整" }
                    entry?.sha256?.takeIf { it.isNotEmpty() }?.let { want ->
                        require(sha256(local) == want) { "檔案校驗失敗（SHA-256 和形象庫索引不符）" }
                    }
                }
                val installed = CompletableDeferred<Unit>()
                val accepted = submit {
                    try {
                        val info = AvatarInstaller.install(rootDir, local, name, transfer.charId,
                            if (transfer.uri != null) "import" else transfer.url)
                        avatarState.value = (avatarState.value + info).sortedByDescending { it.installedAt }
                        if (transfer.select) { selectNow(info.id); report((if (transfer.uri != null) "已匯入：" else "已安裝：") + info.name) }
                    } catch (e: Exception) { failed(transfer, e) }
                    finally { local.delete(); finished(transfer); installed.complete(Unit) }
                }
                if (accepted) installed.await()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { submit { failed(transfer, e); finished(transfer) } }
            finally { temp?.delete() }
        }
    }

    private fun failed(transfer: Transfer, error: Exception) {
        android.util.Log.w("TcAvatar", "transfer failed: ${transfer.url}", error)
        if (transfer.uri == null) failedUrls += transfer.url
        report("${if (transfer.uri != null) "匯入" else "形象下載"}失敗：${error.message ?: "無法讀取模型"}")
    }

    private fun finished(transfer: Transfer) {
        queuedUrls.remove(transfer.url)
        running = null
        taskState.value = null
        recompute()
        startNext()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 寫到 target（start > 0 時接在後面續寫）；回傳目前總共寫了多少 bytes。完整性由呼叫端檢查。 */
    private suspend fun copy(input: InputStream, target: File, total: Long?, progress: (Long, Long?) -> Unit, start: Long = 0L): Long {
        val limit = 300L * 1024 * 1024
        require(total == null || total <= limit) { "檔案超過 300 MB" }
        var bytes = start
        progress(bytes, total)
        java.io.FileOutputStream(target, start > 0).use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                // 先寫入再丟例外：中斷時已收到的部分留給續傳
                output.write(buffer, 0, count)
                bytes += count
                require(bytes <= limit) { "檔案超過 300 MB" }
                progress(bytes, total)
            }
        }
        return bytes
    }
}
