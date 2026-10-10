package com.tokencoin.writer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProfilesScreen(state: AppState) {
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    if (state.profiles.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("還沒有設定檔\n在寫卡頁按「存為設定檔」即可保存", textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.profiles, key = { it.name }) { p ->
            Card(
                onClick = {
                    // 載入設定檔時換新卡片 ID，避免多張卡重複
                    state.updateForm(p.form.copy(id = newCardId()))
                    state.tab = 0
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.form.provider} · ${p.form.model}" + (p.form.char.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { toDelete = p }) { Icon(Icons.Default.Delete, "刪除") }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("刪除設定檔？") },
            text = { Text("「${p.name}」將從本機移除，已寫入的卡片不受影響。") },
            confirmButton = { TextButton(onClick = { state.deleteProfile(p); toDelete = null }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } },
        )
    }
}
