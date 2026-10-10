package com.tokencoin.client.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokencoin.client.avatar.AvatarInfo
import com.tokencoin.client.avatar.AvatarTask
import com.tokencoin.client.ui.chat.ConfirmDialog
import com.tokencoin.client.ui.components.StatusDot
import com.tokencoin.client.ui.components.TcButton
import com.tokencoin.client.ui.components.TcButtonKind
import com.tokencoin.client.ui.components.TcIconButton
import com.tokencoin.client.ui.components.TcSectionLabel
import com.tokencoin.client.ui.components.TcTag
import com.tokencoin.client.ui.theme.Tc
import com.tokencoin.client.ui.theme.TcLabelStyle
import com.tokencoin.client.ui.theme.TcShape
import com.tokencoin.client.ui.theme.tcCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 形象清單：選擇、刪除、匯入。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AvatarSheet(
    avatars: List<AvatarInfo>,
    activeId: String?,
    task: AvatarTask?,
    rootDir: File,
    cardCharId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tc.colors
    var deleting by remember { mutableStateOf<AvatarInfo?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        shape = TcShape.large,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .tcCanvas()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("形象", style = MaterialTheme.typography.titleLarge, color = c.structure)
            if (cardCharId.isNotBlank()) {
                Text(
                    "目前卡片的角色 ID：$cardCharId。在這裡選的形象會記在這張卡上。",
                    color = c.textMuted, style = MaterialTheme.typography.bodySmall,
                )
            }
            TcSectionLabel("INSTALLED · ${avatars.size}")
            if (avatars.isEmpty()) {
                Text("還沒有形象。匯入一個 VRM 模型或角色包（.zip）。", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            avatars.forEach { a ->
                AvatarRow(a, a.id == activeId, rootDir, onClick = { onSelect(a.id) }, onDelete = { deleting = a })
            }
            task?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(c.action)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it.label + (it.progress?.let { p -> " ${(p * 100).toInt()}%" } ?: "…"),
                        color = c.structure, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TcButton(
                    "匯入 VRM / 角色包", onImport,
                    kind = TcButtonKind.Primary, icon = Icons.Default.Add,
                    enabled = task == null, height = 48.dp, modifier = Modifier.weight(1f),
                )
                TcButton(
                    "線上形象庫", onBrowse,
                    icon = Icons.Default.Search,
                    height = 48.dp, modifier = Modifier.weight(1f),
                )
            }
            Text(
                "VRM 檔內有作者設定的授權，請遵守。不要散布別人的模型。",
                color = c.textMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    deleting?.let { a ->
        ConfirmDialog(
            title = "刪除形象？",
            text = "「${a.name}」的模型檔會從手機刪除。",
            confirm = "刪除",
            onDismiss = { deleting = null },
            onConfirm = { onDelete(a.id); deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarRow(a: AvatarInfo, active: Boolean, rootDir: File, onClick: () -> Unit, onDelete: () -> Unit) {
    val c = Tc.colors
    val thumb by produceState<ImageBitmap?>(null, a.id, a.thumbPath) {
        if (a.thumbPath.isNotBlank()) value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(rootDir, a.id + "/" + a.thumbPath)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TcShape.panel)
            .border(1.dp, if (active) c.action else c.borderMuted, TcShape.panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(TcShape.control).background(c.inset)) {
            thumb?.let { Image(it, null, Modifier.size(56.dp), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    StatusDot(c.action)
                    Spacer(Modifier.width(6.dp))
                }
                Text(a.name, color = c.structure, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(
                    a.authors.takeIf { it.isNotEmpty() }?.joinToString("、"),
                    "VRM ${if (a.vrmVersion == 0) "0.x" else "1.0"}",
                    "%.1f MB".format(a.sizeBytes / 1048576.0),
                ).joinToString(" · "),
                color = c.textMuted, style = TcLabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.charId.isNotBlank()) TcTag("ID ${a.charId}", color = c.data)
                if (a.source != "import") TcTag("卡片下載")
                if (a.license.isNotBlank()) TcTag(a.license)
            }
        }
        TcIconButton(Icons.Default.Delete, "刪除", onDelete, tint = c.textMuted)
    }
}
