package com.tokencoin.client.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.avatar.AvatarRepoEntry
import com.tokencoin.client.avatar.AvatarRepoFormat
import com.tokencoin.client.avatar.AvatarRepoIndex
import com.tokencoin.client.avatar.AvatarRepoListing
import com.tokencoin.client.avatar.AvatarRepository
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcInput
import com.tokencoin.client.ui.components.TcPanel
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcTag
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 線上形象庫：搜尋 GitHub 上標了 topic `deepcoin-avatar` 的公開倉庫，或直接貼上倉庫網址。
 * @param onPick 寫卡用：選了角色就回傳（不安裝）；null 表示「安裝」模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarRepoBrowser(
    repo: AvatarRepository,
    onDismiss: () -> Unit,
    onPick: ((AvatarRepoIndex, AvatarRepoEntry) -> Unit)? = null,
) {
    val c = Tc.colors
    val scope = rememberCoroutineScope()
    val installed by repo.avatars.collectAsState()
    val task by repo.task.collectAsState()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var listings by remember { mutableStateOf<List<AvatarRepoListing>?>(null) }
    var index by remember { mutableStateOf<AvatarRepoIndex?>(null) }

    fun run(block: suspend () -> Unit) {
        loading = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }
    fun open(url: String) = run { index = repo.loadRepo(url) }
    fun submit() {
        val q = query.trim()
        if (AvatarRepoFormat.parseRef(q) != null) open(q) else run { index = null; listings = repo.searchRepos(q) }
    }
    LaunchedEffect(Unit) { submit() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        shape = TcShape.large,
        dragHandle = null,
    ) {
        Column(Modifier.tcCanvas().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index != null) {
                    TcIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", { index = null; error = null }, size = 34.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    index?.name ?: if (onPick != null) "從形象庫選擇" else "線上形象庫",
                    style = MaterialTheme.typography.titleLarge, color = c.structure,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = c.action, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(10.dp))
            if (index == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TcInput(
                        query, { query = it },
                        placeholder = "搜尋，或貼上倉庫網址",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TcIconButton(Icons.Default.Search, "搜尋", { submit() }, bordered = true, enabled = !loading)
                }
                Text(
                    "列出 GitHub 上標了 topic「${AvatarRepoFormat.TOPIC}」的公開倉庫。自建形象庫的方法見專案的 形象倉庫/README.md。",
                    color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )
            } else if (index!!.description.isNotBlank()) {
                Text(index!!.description, color = c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(10.dp))

            val idx = index
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (idx == null) {
                    val list = listings.orEmpty()
                    if (listings != null && list.isEmpty() && !loading) item {
                        Text("沒有找到形象庫。", color = c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                    }
                    items(list, key = { it.url }) { r ->
                        TcPanel(Modifier.fillMaxWidth().clickable { open(r.url) }, borderColor = c.borderMuted, contentPadding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.fullName, color = c.structure, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("★ ${r.stars}", color = c.textMuted, style = TcLabelStyle)
                            }
                            if (r.description.isNotBlank()) Text(r.description, color = c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    if (idx.entries.isEmpty()) item {
                        Text("這個形象庫還沒有角色。", color = c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                    }
                    items(idx.entries, key = { it.charId }) { e ->
                        val have = installed.any { it.source == e.sourceKey }
                        val busy = task != null
                        EntryRow(e) {
                            if (onPick != null) TcButton("選擇", { onPick(idx, e) }, kind = TcButtonKind.Primary)
                            else TcButton(
                                if (have) "使用" else "安裝",
                                { repo.installFromRepo(e); onDismiss() },
                                kind = if (have) TcButtonKind.Secondary else TcButtonKind.Primary,
                                enabled = have || !busy,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: AvatarRepoEntry, action: @Composable () -> Unit) {
    val c = Tc.colors
    TcPanel(Modifier.fillMaxWidth(), borderColor = c.borderMuted, contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = rememberRemoteThumb(e.thumbUrl)
            Box(Modifier.size(56.dp).clip(TcShape.control).background(c.inset), contentAlignment = Alignment.Center) {
                if (thumb != null) Image(thumb, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                else Text("VRM", color = c.textMuted, style = TcLabelStyle)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = c.structure, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        e.charId,
                        e.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                        e.size?.let { "%.1f MB".format(it / 1048576.0) },
                    ).joinToString(" · "),
                    color = c.textMuted, style = TcLabelStyle,
                )
                if (e.authors.isNotEmpty()) Text(e.authors.joinToString("、"), color = c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.license.isNotBlank()) TcTag(e.license, modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
        if (e.description.isNotBlank()) Text(e.description, color = c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 下載縮圖（上限 2 MB，縮小解碼）；失敗回傳 null */
@Composable
private fun rememberRemoteThumb(url: String): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, url) {
        if (url.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("User-Agent", "deepcoin-app")
                    if (conn.responseCode !in 200..299 || conn.contentLengthLong > 2_000_000) return@runCatching null
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 2_000_000) return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 160) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
    }
    return state.value
}
